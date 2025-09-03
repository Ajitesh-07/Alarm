package com.example.alarm.ui.screens.alarm

import android.R.attr.radius
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.data.Alarm
import com.example.alarm.receivers.AlarmReceiver
import androidx.core.net.toUri
import com.example.alarm.R
import kotlinx.coroutines.flow.distinctUntilChanged
import java.nio.file.Files.size
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalQueries.zone
import kotlin.math.max

fun formatTime(hour: Int, minute: Int): String {
    val hourConv = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    val formattedMinute = String.format("%02d", minute)
    val formattedHour = String.format("%02d", hourConv)

    return "$formattedHour:$formattedMinute"
}
fun getRepeatText(alarm: Alarm): String {
    if (alarm.repeatDays == 0) return "One-time"
    if (alarm.repeatDays == Alarm.allDays()) return "Every day"

    val codes = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val selected = mutableListOf<String>()
    Alarm.DAYS.forEachIndexed { idx, dayFlag ->
        if (alarm.repeatsOn(dayFlag)) selected.add(codes[idx])
    }

    return when (selected.size) {
        0 -> "One-time"
        1 -> selected[0]
        2 -> selected.joinToString(" & ")
        else -> {
            val rest = selected.dropLast(1).joinToString(", ")
            "$rest & ${selected.last()}"
        }
    }
}

private fun dayOfWeekToFlag(dow: DayOfWeek): Int {
    return when (dow) {
        DayOfWeek.SUNDAY -> Alarm.SUNDAY
        DayOfWeek.MONDAY -> Alarm.MONDAY
        DayOfWeek.TUESDAY -> Alarm.TUESDAY
        DayOfWeek.WEDNESDAY -> Alarm.WEDNESDAY
        DayOfWeek.THURSDAY -> Alarm.THURSDAY
        DayOfWeek.FRIDAY -> Alarm.FRIDAY
        DayOfWeek.SATURDAY -> Alarm.SATURDAY
    }
}

fun getNextTriggerLocalDateTime(
    alarm: Alarm,
    now: LocalDateTime = LocalDateTime.now()
): LocalDateTime {
    val safeHour = (alarm.hour % 24 + 24) % 24
    val safeMinute = (alarm.minute % 60 + 60) % 60
    val alarmLocalTime = try {
        LocalTime.of(safeHour, safeMinute)
    } catch (e: DateTimeException) {
        LocalTime.MIDNIGHT
    }

    if (alarm.repeatDays == 0) {
        val todayCandidate = LocalDateTime.of(now.toLocalDate(), alarmLocalTime)
        return if (todayCandidate.isAfter(now)) todayCandidate
        else LocalDateTime.of(now.toLocalDate().plusDays(1), alarmLocalTime)
    }

    for (i in 0..6) {
        val candidateDate = now.toLocalDate().plusDays(i.toLong())
        val flag = dayOfWeekToFlag(candidateDate.dayOfWeek)
        if (alarm.repeatsOn(flag)) {
            val candidateDateTime = LocalDateTime.of(candidateDate, alarmLocalTime)
            if (i == 0 && candidateDateTime.isBefore(now)) {
                continue
            }
            return candidateDateTime
        }
    }

    return LocalDateTime.of(now.toLocalDate().plusDays(1), alarmLocalTime)
}

fun getNextTriggerLabel(
    alarm: Alarm,
    now: LocalDateTime = LocalDateTime.now()
): String {
    val next = getNextTriggerLocalDateTime(alarm, now)
    val duration = Duration.between(now, next)

    if (!duration.isNegative && duration.toHours() < 24) {
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "in ${hours}h ${minutes}m"
            minutes > 0 -> "in ${minutes}m"
            else -> "in 0m"
        }
    }

    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    return next.format(fmt)
}

data class SubtitleParts(val left: String, val right: String)

fun buildSubtitleParts(
    alarm: Alarm,
    now: LocalDateTime = LocalDateTime.now()
): SubtitleParts {
    val repeatText = getRepeatText(alarm)

    val extras = mutableListOf<String>()
    if (alarm.snoozeTime > 0) extras.add("Snooze ${alarm.snoozeTime}m")
    if (alarm.isVibrating) extras.add("Vib")

    val left = listOf(repeatText, extras.joinToString(" · ")).filter { it.isNotBlank() }.joinToString(" · ")
    val right = getNextTriggerLabel(alarm, now)
    return SubtitleParts(left = left, right = right)
}

@Composable
fun AlarmsScreen(
    viewModel: AlarmViewModel,
    onNavigate: (id: Int) -> Unit,
    isSelectionMode: Boolean,
    selectedAlarmIds: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onStartSelectionMode: (Int) -> Unit
) {
    val alarmsState by viewModel.alarms.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    val context = LocalContext.current
    val scheduler = remember { AlarmScheduler(context.applicationContext) }

    if (alarmsState == UiState.Loading) {
        Box(modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val alarms = (alarmsState as UiState.Success<List<Alarm>>).data
    if (alarms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "You have no Alarms set",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap + to add an Alarm",
                            fontSize = 20.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                AddAlarmFAB {
                    onNavigate(-1)
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp)) {
            Column {
                Text(
                    text = "Your Alarms",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        itemsIndexed(alarms) { index, item ->
                            AlarmRow(
                                alarm = item,
                                isSelected = selectedAlarmIds.contains(item.id),
                                isSelectionMode = isSelectionMode,
                                onToggle = { enabled ->
                                    if (enabled) {
                                        scheduler.scheduleNextAlarm(item)
                                    } else {
                                        scheduler.cancelAlarm(item.id)
                                    }
                                    viewModel.setActive(item.id, enabled)
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        onToggleSelection(item.id)
                                    } else {
                                        onNavigate(item.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        onStartSelectionMode(item.id)
                                    }
                                }
                            )
                            if (index < alarms.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                    AddAlarmFAB {
                        onNavigate(-1)
                    }
                }
            }
        }
    }
}

@Composable
fun AddAlarmFAB(onNavigate: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        FloatingActionButton(
            onClick = { onNavigate() },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Add, "Add")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add new alarm",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp
        )
    }
}

@Composable
fun AlarmRow(
    alarm: Alarm,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val thumbColor by animateColorAsState(if (alarm.enabled) colorScheme.onPrimary else colorScheme.outline)
    val trackColor by animateColorAsState(if (alarm.enabled) colorScheme.primary.copy(alpha = 0.8f) else colorScheme.surfaceVariant)

    val subtitleParts = buildSubtitleParts(alarm)

    var rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)

    if (alarm.isSnoozing) {
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            )
        )

        rowModifier = rowModifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

    }

    Row(
        modifier = rowModifier
            .combinedClickable(
                onClick = {
                    if (!alarm.isSnoozing) {
                        onClick()
                    }
                          },
                onLongClick = onLongClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = isSelectionMode) {
            Icon(
                painter = if (isSelected) painterResource(R.drawable.check_circle_foreground) else painterResource(R.drawable.unfilled_circle_foreground),
                contentDescription = "Selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(40.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarm.title,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(alarm.hour, alarm.minute),
                    color = colorScheme.onSurface,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (alarm.hour < 12) "AM" else "PM",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            if (!alarm.isSnoozing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitleParts.left,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = subtitleParts.right,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                Text(text = "SNOOZING NOW",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp)
            }
        }

        if (!alarm.isSnoozing) {
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = thumbColor,
                    uncheckedThumbColor = colorScheme.outline,
                    checkedTrackColor = trackColor,
                    uncheckedTrackColor = colorScheme.surfaceDim
                ),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

