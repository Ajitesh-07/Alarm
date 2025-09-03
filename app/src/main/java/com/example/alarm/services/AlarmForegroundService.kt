package com.example.alarm.services

import android.R
import android.R.attr.action
import android.R.attr.path
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.stopForeground
import androidx.room.RoomDatabase
import com.example.alarm.data.AlarmDatabase
import com.example.alarm.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import com.example.alarm.data.Alarm
import com.example.alarm.services.AlarmForegroundService.Companion.CHANNEL_ID
import com.example.alarm.ui.screens.alarm.AlarmScheduler
import java.util.Calendar
import kotlin.math.min
import kotlin.time.Duration

class AlarmForegroundService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var scheduler: AlarmScheduler
    private lateinit var repo: AlarmRepository

    override fun onCreate() {
        super.onCreate()
        repo = AlarmRepository(AlarmDatabase.getInstance(applicationContext).alarmDao())
        scheduler = AlarmScheduler(applicationContext)
    }

    companion object {
        const val NOTIF_ID = 42
        const val CHANNEL_ID = "alarm_channel"
        const val ACTION_DISMISS = "com.example.alarm.ACTION_DISMISS"

        const val ACTION_SNOOZE = "com.example.alarm.ACTION_SNOOZE"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            val alarmId = intent.getIntExtra("alarmId", -1)
            val hour = intent.getIntExtra("hour", -1)
            val minute = intent.getIntExtra("minute", -1)
            val repeatDays = intent.getIntExtra("repeatDays", -1)
            serviceScope.launch {
                val alarm = withContext(Dispatchers.IO) {
                    repo.getAlarm(alarmId)
                }

                if (alarm != null) {
                    if (alarm.repeatDays != 0) {
                        scheduler.scheduleNextAlarm(alarm.id, hour, minute, repeatDays)
                    } else {
                        withContext(Dispatchers.IO) {
                            repo.deleteAlarm(alarm)
                        }
                    }
                }

                stopAlarm()
            }

            return START_NOT_STICKY
        }
        else if (intent?.action == ACTION_SNOOZE) {
            val alarmId = intent.getIntExtra("alarmId", -1)
            val snoozeTime = intent.getIntExtra("snoozeTime", -1)
            serviceScope.launch {
                val alarm = withContext(Dispatchers.IO) {
                    repo.getAlarm(alarmId)
                }

                Log.d("ALARM", "SERVICE HERE WITH ${alarm.toString()}")

                if (alarm != null) {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, snoozeTime)
                    }

                    val newHour = cal.get(Calendar.HOUR_OF_DAY)
                    val newMinute = cal.get(Calendar.MINUTE)


                    val updated = alarm.copy(
                        isSnoozing = true,
                        hour = newHour,
                        minute = newMinute
                    )

                    withContext(Dispatchers.IO) {
                        repo.updateAlarm(updated)
                    }

                    scheduler.scheduleSnooze(alarmId, cal.timeInMillis)
                }

                stopAlarm()
            }
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra("alarmId", -1)

        startForeground(NOTIF_ID, buildPreparingNotification())
        Log.d("ALARM", "Foreground started")
        createNotificationChannel(this,CHANNEL_ID)

        serviceScope.launch {
            val alarm = withContext(Dispatchers.IO) {
                repo.getAlarm(alarmId!!)
            }

            Log.d("ALARM", "Alarm id is $alarmId and alarm recieved is ${alarm.toString()}")
            if (alarm == null) {
                stopSelf()
                return@launch
            }

            val dismissIntent = Intent(this@AlarmForegroundService, AlarmForegroundService::class.java).apply {
                putExtra("alarmId", alarm.id)
                putExtra("hour", alarm.hour)
                putExtra("minute", alarm.minute)
                putExtra("repeatDays", alarm.repeatDays)
                action = ACTION_DISMISS
            }

            val snoozeIntent = Intent(this@AlarmForegroundService, AlarmForegroundService::class.java).apply {
                putExtra("alarmId", alarm.id)
                putExtra("snoozeTime", alarm.snoozeTime)
                action = ACTION_SNOOZE
            }

            val snoozePi = PendingIntent.getService(
                this@AlarmForegroundService, alarmId!! * 10 + 1, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissPi = PendingIntent.getService(
                this@AlarmForegroundService, alarmId!! * 10 + 2, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissAction = NotificationCompat.Action.Builder(R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi).build()
            val snoozeAction = NotificationCompat.Action.Builder(R.drawable.ic_lock_idle_alarm, "Snooze (${alarm.snoozeTime}m)", snoozePi).build()

            val notification = NotificationCompat.Builder(this@AlarmForegroundService, CHANNEL_ID).apply {
                setContentTitle(alarm.title)
                setContentText("Alarm is Ringing")
                setSmallIcon(R.drawable.ic_lock_idle_alarm)
                setOngoing(true)
                addAction(snoozeAction)
                addAction(dismissAction)
                setCategory(NotificationCompat.CATEGORY_ALARM)
            }.build()

            startForeground(NOTIF_ID, notification)
            Log.d("ALARM", "Foreground Activity Started")


            startPlaying(alarm.ringtoneUri)
            if (alarm.isVibrating) {
                startVibrating(this@AlarmForegroundService)
            }
            Log.d("ALARM", "Ringtone playing")

        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        stopAlarm()
        super.onDestroy()
    }

    private fun startVibrating(context: Context) {
        if (vibrator != null) return

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 300, 200, 500)
        vibrator?.vibrate(
            VibrationEffect.createWaveform(pattern, 0)
        )
    }

    private fun startPlaying(uriString: String) {
        if (player != null) return

//        val defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
        val ringtoneUri = uriString.toUri()
        player = MediaPlayer().apply {

            setOnPreparedListener { mp ->
                mp.start()
            }

            setDataSource(this@AlarmForegroundService, ringtoneUri)
            isLooping = true
            setAudioAttributes(
                AudioAttributes.Builder().apply {
                    setUsage(AudioAttributes.USAGE_ALARM)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                }.build()
            )
            prepareAsync()
        }
    }

    private fun stopAlarm() {
        player?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }

        vibrator?.cancel()
        player = null
        vibrator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel(context: Context, channelId: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(channelId) == null) {
            val channel =
                NotificationChannel(channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Alarm Notifications"
            channel.setSound(null, null)
            nm.createNotificationChannel(channel)
            Log.d("ALARM", "Channel Created")
        }
    }

    private fun buildPreparingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Preparing alarm")
            .setContentText("Starting...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }
}