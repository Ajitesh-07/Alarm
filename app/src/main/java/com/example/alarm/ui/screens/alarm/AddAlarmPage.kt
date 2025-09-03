package com.example.alarm.ui.screens.alarm

import android.R.attr.data
import android.R.attr.onClick
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.alarm.data.Alarm
import java.util.*
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

fun getRingtoneTitle(context: Context, uri: Uri): String {
    return RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Unknown Ringtone"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlarmScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: AddEditAlarmViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val crrAlarm by viewModel.crrAlarm.collectAsState()

    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scheduler = remember { AlarmScheduler(context.applicationContext) }

    val calendar = Calendar.getInstance()
    var hour by remember(crrAlarm) { mutableStateOf(
        crrAlarm?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)
    ) }
    var minute by remember(crrAlarm) { mutableStateOf(
        crrAlarm?.minute ?: calendar.get(Calendar.MINUTE)
    ) }

    var alarmName by remember(crrAlarm) { mutableStateOf(
        crrAlarm?.title ?: "Title"
    ) }
    var repeatDays by remember(crrAlarm) { mutableStateOf(
        crrAlarm?.repeatDays ?: 0
    ) }
    var doesVibrate by remember(crrAlarm) { mutableStateOf(
        crrAlarm?.isVibrating ?: true
    ) }

    val thumbColor by animateColorAsState(
        if (doesVibrate) colorScheme.onPrimary else colorScheme.outline
    )

    val trackColor by animateColorAsState(
        if (doesVibrate) colorScheme.primary.copy(alpha=0.8f) else colorScheme.surfaceVariant
    )

    val defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
    var selectedRingtoneUri by remember(crrAlarm) { mutableStateOf(
        crrAlarm?.ringtoneUri?.toUri() ?: defaultRingtoneUri
    ) }

    val ringtoneDisplayName by remember(selectedRingtoneUri) {
        derivedStateOf { getRingtoneTitle(context, selectedRingtoneUri) }
    }

    val snoozeOptions = listOf(5, 10, 15, 20, 25, 30)
    var snoozeExpanded by remember { mutableStateOf(false) }
    var selectedSnooze by remember { mutableStateOf(snoozeOptions[0]) }

    val timeText = remember(hour, minute) {
        derivedStateOf {
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(Locale.getDefault(), "%02d:%02d", displayHour, minute)
        }
    }
    val amPm = remember(hour) { if (hour < 12) "AM" else "PM" }

    fun showTimePicker() {
        TimePickerDialog(
            context,
            { _, h, m ->
                hour = h
                minute = m
            },
            hour,
            minute,
            false
        ).show()
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data!!
                var uri: Uri?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    uri = intent.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                        Uri::class.java
                    )
                } else {
                    uri = intent.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                    )
                }
                if (uri != null) {
                    selectedRingtoneUri = uri
                }
            }
        }
    )

    var canScheduleAlarms by remember { mutableStateOf(scheduler.canSetAlarms()) }
    var canPostNotification by remember { mutableStateOf(hasNotificationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        canPostNotification = isGranted
    }


    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect (lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleAlarms = scheduler.canSetAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()

        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }

                Button(
                    enabled = (canScheduleAlarms && canPostNotification),
                    onClick = {
                        if (!canScheduleAlarms) {
                            scheduler.requestPermission()
                            return@Button
                        }
                        if (crrAlarm == null) {
                            val newAlarm = Alarm(
                                title = alarmName.ifBlank { "Title" },
                                hour = hour,
                                minute = minute,
                                ringtoneUri = selectedRingtoneUri.toString(),
                                enabled = true,
                                isVibrating = doesVibrate,
                                snoozeTime = selectedSnooze,
                                repeatDays = repeatDays
                            )

                            viewModel.addAlarm(newAlarm, { id ->
                                scheduler.scheduleNextAlarm(newAlarm.copy(id = id.toInt()))
                            })
                        } else {
                            val newAlarm = Alarm(
                                id = crrAlarm!!.id,
                                title = alarmName.ifBlank { "Title" },
                                hour = hour,
                                minute = minute,
                                ringtoneUri = selectedRingtoneUri.toString(),
                                enabled = crrAlarm!!.enabled,
                                isVibrating = doesVibrate,
                                snoozeTime = selectedSnooze,
                                repeatDays = repeatDays
                            )

                            viewModel.updateAlarm(newAlarm, {
                                scheduler.updateAlarm(newAlarm)
                            })
                        }
                        onSave()

                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker() }
            ) {
                Text(timeText.value, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text(amPm, fontSize = 14.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text("Alarm name", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = alarmName,
                onValueChange = { alarmName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Title") },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(18.dp))

            Text("Repeat", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Alarm.DAYS.forEachIndexed { index, dayFlag ->
                    val isSelected = (repeatDays and dayFlag) != 0
                    val activeBg by animateColorAsState(if (isSelected) colorScheme.primary else colorScheme.surface, label = "")
                    val activeText by animateColorAsState(if (isSelected) colorScheme.onPrimary else colorScheme.onSurface, label = "")
                    val borderColor = if (isSelected) colorScheme.primary else colorScheme.outline

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(activeBg, CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                repeatDays = if (isSelected) repeatDays - dayFlag else repeatDays + dayFlag
                            }
                            .border(if (isSelected) 0.dp else 1.dp, borderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Alarm.CODES[index], color = activeText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text("Snooze", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = snoozeExpanded,
                onExpandedChange = { snoozeExpanded = !snoozeExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = "$selectedSnooze minutes",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { Icon(if (snoozeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = snoozeExpanded, onDismissRequest = { snoozeExpanded = false }) {
                    snoozeOptions.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("$minutes minutes") },
                            onClick = {
                                selectedSnooze = minutes
                                snoozeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Ringtone", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {

                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri!!)
                        }

                        ringtonePickerLauncher.launch(intent)
                    }
            ) {
                OutlinedTextDisplay(
                    text = ringtoneDisplayName,
                    modifier = Modifier
                        .fillMaxWidth(),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick ringtone") },
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Vibration",
                    fontSize = 17.sp,
                )

                Switch(
                    checked = doesVibrate,
                    onCheckedChange = { doesVibrate = !doesVibrate },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = thumbColor,
                        uncheckedThumbColor = colorScheme.outline,
                        checkedTrackColor = trackColor,
                        uncheckedTrackColor = colorScheme.surfaceDim
                ),

                )
            }

            if (!canScheduleAlarms) {
                Spacer(Modifier.height(10.dp))
                Column {
                    Text("Note: You have not set the permission for this app to set exact alarms, please click the button below and give the permission",
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Button(
                        onClick = {
                            scheduler.requestPermission()
                        }
                    ) {
                        Text("Allow Permission")
                    }
                }
            }

            if (!canPostNotification) {
                Spacer(Modifier.height(10.dp))
                Column {
                    Text("Note: You have not set the permission for this app to give notifications, notifications permission is needed to show alarms on your screen and to play the ringtone, please click the button below and give the permission or go to the app settings",
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    ) {
                        Text("Allow Permission")
                    }
                }
            }


        }
    }
}

@Composable
fun OutlinedTextDisplay(
    text: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
) {
    val borderColor = when {
        isError -> Color.Red
        else -> MaterialTheme.colorScheme.outline
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                color = if (isError) Color.Red else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

