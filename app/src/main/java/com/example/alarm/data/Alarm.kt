package com.example.alarm.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val ringtoneUri: String,
    var enabled: Boolean,
    val isVibrating: Boolean,
    val snoozeTime: Int,
    val repeatDays: Int = 0,
    val isSnoozing: Boolean = false
) {
    companion object {
        const val SUNDAY = 1
        const val MONDAY = 2
        const val TUESDAY = 4
        const val WEDNESDAY = 8
        const val THURSDAY = 16
        const val FRIDAY = 32
        const val SATURDAY = 64

        val DAYS = listOf(
            SUNDAY,
            MONDAY,
            TUESDAY,
            WEDNESDAY,
            THURSDAY,
            FRIDAY,
            SATURDAY
        )

        val CODES = listOf(
            "S",
            "M",
            "T",
            "W",
            "T",
            "F",
            "S"
        )

        fun allDays() = MONDAY + TUESDAY + WEDNESDAY + THURSDAY + FRIDAY + SATURDAY + SUNDAY
    }

    fun repeatsOn(day: Int): Boolean = (repeatDays and day) != 0
}