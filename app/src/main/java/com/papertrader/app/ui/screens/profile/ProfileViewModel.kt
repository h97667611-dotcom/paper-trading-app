package com.papertrader.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.PaperTradingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val showResetConfirmation: Boolean = false,
    val resetComplete: Boolean = false
)

class ProfileViewModel(private val tradingRepository: PaperTradingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun requestReset() {
        _uiState.value = _uiState.value.copy(showResetConfirmation = true)
    }

    fun dismissResetConfirmation() {
        _uiState.value = _uiState.value.copy(showResetConfirmation = false)
    }

    fun confirmReset() {
        viewModelScope.launch {
            tradingRepository.resetAccount()
            _uiState.value = _uiState.value.copy(showResetConfirmation = false, resetComplete = true)
        }
    }

    fun consumeResetComplete() {
        _uiState.value = _uiState.value.copy(resetComplete = false)
    }
}
