package com.example.data

import android.content.Context
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val scheduler: AlarmScheduler
) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun getAlarmById(id: Int): Alarm? {
        return alarmDao.getAlarmById(id)
    }

    suspend fun insert(alarm: Alarm) {
        val newId = alarmDao.insertAlarm(alarm).toInt()
        val savedAlarm = alarmDao.getAlarmById(newId)
        if (savedAlarm != null) {
            scheduler.schedule(savedAlarm)
        }
    }

    suspend fun update(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
        if (alarm.isEnabled) {
            scheduler.schedule(alarm)
        } else {
            scheduler.cancel(alarm)
        }
    }

    suspend fun delete(alarm: Alarm) {
        alarmDao.deleteAlarm(alarm)
        scheduler.cancel(alarm)
    }

    suspend fun rescheduleAllActiveAlarms() {
        val activeAlarms = alarmDao.getActiveAlarmsSync()
        for (alarm in activeAlarms) {
            scheduler.schedule(alarm)
        }
    }
}
