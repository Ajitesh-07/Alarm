package com.example.alarm.data

class AlarmRepository(private val dao: AlarmDao) {
    val alarms = dao.getAllAlarms()

    suspend fun addAlarm(alarm: Alarm): Long {
        return dao.insertAlarm(alarm)
    }

    suspend fun getAlarm(id: Int): Alarm? {
        return if (id == -1) null
        else dao.getAlarmById(id)
    }

    suspend fun deleteAlarm(alarm: Alarm) {
        dao.deleteAlarm(alarm)
    }

    suspend fun deleteAlarms(ids: List<Int>) {
        dao.deleteAlarms(ids)
    }

    suspend fun updateAlarm(alarm: Alarm) {
        dao.updateAlarm(alarm)
    }

    suspend fun deletePrevAlarms(alarm: Alarm) {

    }

    suspend fun setActive(id: Int, active: Boolean) {
        val crrAlarm = dao.getAlarmById(id)
        if (crrAlarm != null) {
            crrAlarm.enabled = active
            dao.updateAlarm(crrAlarm)
        }
    }
}