package com.papertrader.app.ui.screens.funds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.engine.TradingResult
import com.papertrader.app.domain.model.FundsTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FundsUiState(
    val cashBalance: Double = 0.0,
    val history: List<FundsTransaction> = emptyList(),
    val amountInput: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class FundsViewModel(private val tradingRepository: PaperTradingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FundsUiState())
    val uiState: StateFlow<FundsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tradingRepository.observeCashBalance().collect { cash ->
                _uiState.value = _uiState.value.copy(cashBalance = cash)
            }
        }
        viewModelScope.launch {
            tradingRepository.observeFundsHistory().collect { history ->
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
    }

    fun onAmountChanged(value: String) {
        _uiState.value = _uiState.value.copy(amountInput = value, errorMessage = null)
    }

    fun addFunds() {
        val amount = _uiState.value.amountInput.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter an amount greater than zero.")
            return
        }
        viewModelScope.launch {
            when (val result = tradingRepository.addFunds(amount)) {
                is TradingResult.Success -> _uiState.value = _uiState.value.copy(
                    amountInput = "",
                    successMessage = "Added $amount USDT to your paper account."
                )
                is TradingResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.error.message)
            }
        }
    }

    fun withdrawFunds() {
        val amount = _uiState.value.amountInput.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter an amount greater than zero.")
            return
        }
        viewModelScope.launch {
            when (val result = tradingRepository.withdrawFunds(amount)) {
                is TradingResult.Success -> _uiState.value = _uiState.value.copy(
                    amountInput = "",
                    successMessage = "Withdrew $amount USDT from your paper account."
                )
                is TradingResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.error.message)
            }
        }
    }

    fun consumeMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
