package com.example.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.Alarm
import com.example.receiver.AlarmReceiver

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }

        val triggerTime = alarm.getNextTriggerMillis()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_LABEL", alarm.label)
            putExtra("ALARM_DIFFICULTY", alarm.difficulty)
            putExtra("ALARM_ANSWERS", alarm.correctAnswersRequired)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            flags
        )

        val info = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // If we target Android 12+, we should check if we can schedule exact alarms
                // But setAlarmClock is generally allowed or is preferred.
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setAlarmClock(info, pendingIntent)
            }
            Log.d("AlarmScheduler", "Scheduled alarm ${alarm.id} at $triggerTime (${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(triggerTime))})")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Security exception scheduling alarm", e)
            // Fallback to non-exact
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancel(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            flags
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Canceled alarm ${alarm.id}")
        }
    }
}
