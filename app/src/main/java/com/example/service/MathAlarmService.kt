package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.Alarm
import com.example.data.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MathAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "MATH_ALARM_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 9999

        private val _currentRingingAlarm = MutableStateFlow<Alarm?>(null)
        val currentRingingAlarm: StateFlow<Alarm?> = _currentRingingAlarm

        fun stopAlarmService(context: Context) {
            val intent = Intent(context, MathAlarmService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        Log.d("MathAlarmService", "Service started for alarm: $alarmId")

        if (alarmId != -1) {
            serviceScope.launch {
                val db = AlarmDatabase.getDatabase(applicationContext)
                val alarm = db.alarmDao().getAlarmById(alarmId)
                if (alarm != null) {
                    _currentRingingAlarm.value = alarm
                    launchAlarmTriggers(alarm)

                    // Mark one-shot alarm as disabled
                    if (!alarm.isRepeating()) {
                        val disabledAlarm = alarm.copy(isEnabled = false)
                        db.alarmDao().updateAlarm(disabledAlarm)
                    }
                } else {
                    // Fallback local description if not found in DB
                    val label = intent?.getStringExtra("ALARM_LABEL") ?: "Alarm"
                    val difficulty = intent?.getStringExtra("ALARM_DIFFICULTY") ?: "MEDIUM"
                    val answers = intent?.getIntExtra("ALARM_ANSWERS", 3) ?: 3
                    val dummyAlarm = Alarm(
                        id = alarmId,
                        hour = 0,
                        minute = 0,
                        label = label,
                        difficulty = difficulty,
                        correctAnswersRequired = answers,
                        isEnabled = false
                    )
                    _currentRingingAlarm.value = dummyAlarm
                    launchAlarmTriggers(dummyAlarm)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun launchAlarmTriggers(alarm: Alarm) {
        // Prepare ringtone sound
        try {
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("MathAlarmService", "Failed to play custom alarm sound, falling back to System Ringtone", e)
            try {
                // High-reliability backup
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer.create(applicationContext, alarmUri).apply {
                    isLooping = true
                    start()
                }
            } catch (e2: Exception) {
                Log.e("MathAlarmService", "All ringtone playing methods failed", e2)
            }
        }

        // Prepare physical vibration
        if (alarm.isVibrate) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (vibrator?.hasVibrator() == true) {
                    val pattern = longArrayOf(0, 1000, 500, 1000, 500) // Vibrate 1s, pause 0.5s
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1)) // Loop starting at index 1
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, 1)
                    }
                }
            } catch (e: Exception) {
                Log.e("MathAlarmService", "Failed to start vibration", e)
            }
        }

        // Show Notification
        val notification = createNotification(alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(alarm: Alarm): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bangun! Alarm Selesaikan Matematika")
            .setContentText("Alarm: ${alarm.label} (${String.format("%02d:%02d", alarm.hour, alarm.minute)})")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // Show overlay or activity when locked
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Math Alarm Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Saluran untuk menghidupkan alarm matematika"
                setSound(null, null) // Sound is handled by MediaPlayer
                enableVibration(false) // Vibration is handled by Vibrator
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MathAlarmService", "Service destroyed, releasing audio & vibration")
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("MathAlarmService", "Error cleaning up MediaPlayer", e)
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e("MathAlarmService", "Error cleaning up Vibrator", e)
        }

        _currentRingingAlarm.value = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
