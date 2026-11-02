package dev.rootiest.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatusUiState(
    val view: StatusView? = null, // the last good answer, kept on screen while refreshing or after an error
    val loading: Boolean = false,
    val error: String? = null,
)

class StatusViewModel(private val api: StatusApi = StatusApi()) : ViewModel() {
    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val view = api.fetch()
                _state.update { StatusUiState(view = view) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }
}
