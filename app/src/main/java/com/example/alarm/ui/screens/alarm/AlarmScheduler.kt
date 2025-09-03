package com.example.alarm.ui.screens.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.example.alarm.data.Alarm
import com.example.alarm.receivers.AlarmReceiver
import java.time.temporal.TemporalAdjusters.next
import kotlin.jvm.java

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canSetAlarms(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val req = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(req)
                return
            }
        }
    }

    fun scheduleSnooze(alarmId: Int, nextTime: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra("alarmId", alarmId)
            data = "alarm://alarm/$alarmId".toUri()
        }

        val pi = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmInfo = AlarmManager.AlarmClockInfo(nextTime, pi)
        alarmManager.setAlarmClock(alarmInfo, pi)
    }

    fun scheduleNextAlarm(
        alarm: Alarm
    ) {
        requestPermission()

        val alarmId = alarm.id
        val hour = alarm.hour
        val minute = alarm.minute

        val next = getNextAlarmTime(alarm)
        if (next == null) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra("alarmId", alarmId)
            data = "alarm://alarm/$alarmId".toUri()
        }

        val pi = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmInfo = AlarmManager.AlarmClockInfo(next.timeInMillis, pi)
        alarmManager.setAlarmClock(alarmInfo, pi)
        Log.d("ALARM", "Alarm set for $hour:$minute with ID: $alarmId")
    }

    fun scheduleNextAlarm(
        alarmId: Int,
        hour: Int,
        minute: Int,
        repeatDays: Int
    ) {
        requestPermission()

        val next = getNextAlarmTime(hour, minute, repeatDays)
        if (next == null) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra("alarmId", alarmId)
            data = "alarm://alarm/$alarmId".toUri()
        }

        val pi = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmInfo = AlarmManager.AlarmClockInfo(next.timeInMillis, pi)
        alarmManager.setAlarmClock(alarmInfo, pi)
        Log.d("ALARM", "Alarm set for $hour:$minute with ID: $alarmId")
    }


    fun cancelAlarm(
        alarmId: Int
    ) {
        requestPermission()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra("alarmId", alarmId)
            data = "alarm://alarm/$alarmId".toUri()
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun updateAlarm(
        alarm: Alarm
    ) {
        requestPermission()

        cancelAlarm(alarm.id)
        scheduleNextAlarm(alarm)
    }

    private fun getNextAlarmTime(alarm: Alarm): Calendar? {
        Log.d("ALARM", "Scheduling Alarm ${alarm.repeatDays}")
        val hour = alarm.hour
        val minute = alarm.minute

        val repeatDays = alarm.repeatDays

        val now = Calendar.getInstance()

        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays == 0) {
            if (candidate.before(now)) {
                candidate.add(Calendar.DAY_OF_MONTH, 1)
            }
            return candidate
        }

        for (i in 0..6) {
            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            val dayFlag = Alarm.DAYS[dayOfWeek - 1]

            if (alarm.repeatsOn(dayFlag) && candidate.after(now)) {
                return candidate
            }
            candidate.add(Calendar.DAY_OF_MONTH, 1)
        }

        return null
    }

    private fun getNextAlarmTime(hour: Int, minute: Int, repeatDays: Int): Calendar? {
        val now = Calendar.getInstance()

        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays == 0) {
            if (candidate.before(now)) {
                candidate.add(Calendar.DAY_OF_MONTH, 1)
            }
            return candidate
        }

        for (i in 0..7) {
            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            val dayFlag = Alarm.DAYS[dayOfWeek - 1]

            if ((repeatDays and dayFlag) != 0 && candidate.after(now)) {
                return candidate
            }
            candidate.add(Calendar.DAY_OF_MONTH, 1)
        }

        return null
    }

}