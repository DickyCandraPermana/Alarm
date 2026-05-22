package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val label: String = "Alarm",
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val correctAnswersRequired: Int = 3,
    val repeatDays: String = "", // Comma-separated ints of Calendar.DAY_OF_WEEK, e.g., "2,3,4" (Mon, Tue, Wed). Empty means one-time.
    val isVibrate: Boolean = true
) {
    fun isRepeating(): Boolean = repeatDays.isNotEmpty()

    fun getRepeatDaysList(): List<Int> {
        if (repeatDays.isEmpty()) return emptyList()
        return repeatDays.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun getNextTriggerMillis(): Long {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (isRepeating()) {
            val days = getRepeatDaysList()
            val today = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Find the closest day in the list starting from today
            var minDaysDiff = 10
            for (day in days) {
                var diff = day - today
                if (diff < 0) diff += 7
                if (diff == 0) {
                    // It is today, but is the time today passed?
                    if (currentHour > hour || (currentHour == hour && currentMinute >= minute)) {
                        diff += 7
                    }
                }
                if (diff < minDaysDiff) {
                    minDaysDiff = diff
                }
            }
            if (minDaysDiff < 10) {
                calendar.add(Calendar.DAY_OF_YEAR, minDaysDiff)
            } else {
                // Should not happen, but fallback
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        } else {
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }
}
