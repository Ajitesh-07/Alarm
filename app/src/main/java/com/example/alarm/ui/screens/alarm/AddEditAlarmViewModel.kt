package com.example.alarm.ui.screens.alarm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alarm.data.Alarm
import com.example.alarm.data.AlarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddEditAlarmViewModel(
    private val alarmRepository: AlarmRepository,
    private val alarmId: Int?
    ) : ViewModel() {

    private var _crrAlarm = MutableStateFlow<Alarm?>(null)
    val crrAlarm = _crrAlarm.asStateFlow()

    init {
        if (alarmId != null) {
            viewModelScope.launch {
                Log.d("ALARM", "In VM: ${alarmRepository.getAlarm(alarmId).toString()}")
                _crrAlarm.value = alarmRepository.getAlarm(alarmId)
            }
        }
    }

    fun addAlarm(alarm: Alarm, onInserted: (Long) -> Unit) {
        viewModelScope.launch {
            val id = alarmRepository.addAlarm(alarm)
            onInserted(id)
        }
    }

    fun updateAlarm(alarm: Alarm, onInserted: () -> Unit) {
        viewModelScope.launch {
            alarmRepository.updateAlarm(alarm)
            onInserted()
        }
    }

}

class AddEditAlarmViewModelFactory(private val alarmRepository: AlarmRepository, private val alarmId: Int) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AddEditAlarmViewModel(alarmRepository, alarmId) as T
    }
}