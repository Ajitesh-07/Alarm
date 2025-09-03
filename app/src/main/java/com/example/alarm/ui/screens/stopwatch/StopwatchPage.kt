package com.example.alarm.ui.screens.stopwatch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.R
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

data class LapEntry(
    val id: Long = System.nanoTime(),
    val millis: Long
    )

@Composable
fun StopwatchPage() {
    var isRunning by remember { mutableStateOf(false) }
    var timeInMillis by remember { mutableStateOf(0L) }
    var lastLapTime by remember { mutableStateOf(0L) }
    var laps by remember { mutableStateOf(listOf<LapEntry>()) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
                val startTime = System.currentTimeMillis() - timeInMillis
            while (isRunning) {
                timeInMillis = System.currentTimeMillis() - startTime
                delay(16L) // 60fps
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            StopwatchDial(timeInMillis = timeInMillis, isRunning = isRunning, laps=laps)
            Spacer(modifier = Modifier.height(32.dp))
            ControlButtons(
                isRunning = isRunning,
                hasStarted = timeInMillis > 0L,
                onStart = { isRunning = true },
                onStop = { isRunning = false },
                onReset = {
                    isRunning = false
                    timeInMillis = 0L
                    lastLapTime = 0L
                    laps = emptyList()
                },
                onLap = {
                    val currentLapTime = timeInMillis - lastLapTime
                    laps = listOf(LapEntry(millis = currentLapTime)) + laps
                    lastLapTime = timeInMillis
                }
            )
        }

        LapList(laps = laps)
    }
}

@Composable
fun StopwatchDial(timeInMillis: Long, isRunning: Boolean, laps: List<LapEntry>) {
    val formattedTime = remember(timeInMillis) { formatTime(timeInMillis) }
    val rawSweep = ((timeInMillis % 60000L) / 60000f) * 360f

    val animatedSweepAngle by animateFloatAsState(
        targetValue = rawSweep,
        animationSpec = if (timeInMillis > 0) tween(durationMillis = 120, easing = LinearEasing) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val infinite = rememberInfiniteTransition()
    val animatedPulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.04f else 1f,
        animationSpec = if (isRunning) infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ) else infiniteRepeatable(
            animation = tween(1),
            repeatMode = RepeatMode.Restart
        )
    )



    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(280.dp)
            .graphicsLayer {
                scaleX = animatedPulse
                scaleY = animatedPulse
            }
    ) {
        val strokeWidth = 18f
        val primaryColor = MaterialTheme.colorScheme.primary
        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val radius = diameter / 2f
            val center = Offset(radius, radius)

            if (isRunning) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius
                )
            }

            drawCircle(
                color = surfaceVariantColor.copy(alpha = 0.5f),
                style = Stroke(width = strokeWidth),
                radius = radius - (strokeWidth / 2f)
            )

            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = animatedSweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(diameter - strokeWidth, diameter - strokeWidth),
                topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            )
        }

        Column {
            Text(
                text = formattedTime,
                fontSize = 52.sp,
                fontWeight = FontWeight.Thin,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Lap ${laps.size+1}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun ControlButtons(
    isRunning: Boolean,
    hasStarted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onLap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {

        OutlinedButton(
            onClick = if (isRunning) onLap else onReset,
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.extraLarge,
            enabled = hasStarted
        ) {
            Text(text = if (isRunning) "Lap" else "Reset",
                fontSize = if (isRunning) 18.sp else 10.sp)
        }

        FilledTonalButton(
            onClick = if (isRunning) onStop else onStart,
            modifier = Modifier.size(90.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if(isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if(isRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                painter = painterResource(id = if (isRunning) R.drawable.pause_icon_foreground else R.drawable.play_icon_foreground),
                contentDescription = if (isRunning) "Stop" else "Start",
                modifier = Modifier.size(120.dp)
            )
        }
    }
}
@Composable
fun LapList(laps: List<LapEntry>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.heightIn(max = 200.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = laps,
        ) { index, lap ->
            LapRow(
                index = laps.size-index,
                lap = lap,
                modifier = Modifier
                    .animateItem(placementSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ))
            )
        }

    }
}


@Composable
fun LapRow(index: Int, lap: LapEntry, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f))
                .clickable(
                    interactionSource = interactionSource,
                    onClick = { /* ... */ }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Lap $index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp)
            )
            Text(
                text = formatTime(lap.millis),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }


private fun formatTime(timeInMillis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) % 60
    val milliseconds = (timeInMillis % 1000) / 10
    return String.format("%02d:%02d.%02d", minutes, seconds, milliseconds)
}