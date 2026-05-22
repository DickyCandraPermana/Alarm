package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.service.MathAlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val label = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val difficulty = intent.getStringExtra("ALARM_DIFFICULTY") ?: "MEDIUM"
        val answers = intent.getIntExtra("ALARM_ANSWERS", 3)

        Log.d("AlarmReceiver", "Alarm received! ID: $alarmId, Label: $label, Difficulty: $difficulty")

        // 1. Start the foreground alarm service to play the loud alarm ringtone
        val serviceIntent = Intent(context, MathAlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_DIFFICULTY", difficulty)
            putExtra("ALARM_ANSWERS", answers)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start foreground service", e)
        }

        // 2. Launch MainActivity to show the quiz overlay immediately
        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to launch MainActivity directly", e)
        }
    }
}
