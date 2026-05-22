package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Alarm
import com.example.data.AlarmDatabase
import com.example.data.AlarmRepository
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository

    init {
        val database = AlarmDatabase.getDatabase(application)
        val scheduler = AlarmScheduler(application)
        repository = AlarmRepository(database.alarmDao(), scheduler)
    }

    val alarms: StateFlow<List<Alarm>> = repository.allAlarms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.insert(alarm)
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.update(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.delete(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            repository.update(alarm.copy(isEnabled = enabled))
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
                return AlarmViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
