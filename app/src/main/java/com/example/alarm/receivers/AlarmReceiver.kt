package com.example.alarm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alarm.services.AlarmForegroundService

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM = "com.example.alarm.ACTION_ALARM"
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val alarmId = intent.getIntExtra("alarmId", -1)

        val svcService = Intent(context, AlarmForegroundService::class.java).apply {
            putExtra("alarmId", alarmId)
        }

        Log.d("ALARM", "Alarm received")
        ContextCompat.startForegroundService(context,svcService)
        Log.d("ALARM", "Foreground")
    }

}