package com.example.alarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)

    @Query("DELETE FROM alarms WHERE id IN (:ids)")
    suspend fun deleteAlarms(ids: List<Int>)

    @Query("SELECT * FROM alarms WHERE id == :id")
    suspend fun getAlarmById(id: Int): Alarm?

    @Query("SELECT * FROM alarms")
    fun getAllAlarms(): Flow<List<Alarm>>
}