package com.example.alarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlin.jvm.java

@Database(entities = [Alarm::class], version = 1)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao() : AlarmDao

    companion object {
        @Volatile private var INSTANCE: AlarmDatabase? = null
        fun getInstance(context: Context): AlarmDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarms-db"
                ).build().also { INSTANCE = it }
            }
    }

}