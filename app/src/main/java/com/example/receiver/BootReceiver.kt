package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AlarmDatabase
import com.example.data.AlarmRepository
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device rebooted, rescheduling active alarms...")
            
            val db = AlarmDatabase.getDatabase(context)
            val scheduler = AlarmScheduler(context)
            val repository = AlarmRepository(db.alarmDao(), scheduler)
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.rescheduleAllActiveAlarms()
                    Log.d("BootReceiver", "Successfully rescheduled all active alarms on boot.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule active alarms on boot", e)
                }
            }
        }
    }
}
