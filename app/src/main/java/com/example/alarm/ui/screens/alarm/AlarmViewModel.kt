package com.example.alarm.ui.screens.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alarm.data.Alarm
import com.example.alarm.data.AlarmRepository
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data class Success<out T>(val data: T) : UiState<T>
    data object Loading: UiState<Nothing>
    data class Error<out T>(val message: String, val data: T? = null) : UiState<T>
}

class AlarmViewModel(private val alarmRepository: AlarmRepository) : ViewModel() {
    val alarms = alarmRepository.alarms
        .map<List<Alarm>, UiState<List<Alarm>>> { UiState.Success(it) }
        .onStart { emit(UiState.Loading) }
        .catch { e -> emit(UiState.Error(e.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    fun setActive(id: Int, active: Boolean) {
        viewModelScope.launch {
            alarmRepository.setActive(id, active)
        }
    }

    fun deleteAlarms(ids: List<Int>) {
        viewModelScope.launch {
            alarmRepository.deleteAlarms(ids)
        }
    }
}

class AlarmViewModelFactory(private val alarmRepository: AlarmRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AlarmViewModel(alarmRepository) as T
    }
}
