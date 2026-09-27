package com.papertrader.app.ui.screens.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papertrader.app.data.repository.MarketRepository
import com.papertrader.app.data.repository.PaperTradingRepository
import com.papertrader.app.domain.engine.TradingResult
import com.papertrader.app.domain.model.Coin
import com.papertrader.app.domain.model.OrderSide
import com.papertrader.app.domain.model.OrderType
import com.papertrader.app.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrderUiState(
    val isLoading: Boolean = true,
    val coin: Coin? = null,
    val side: OrderSide = OrderSide.BUY,
    val orderType: OrderType = OrderType.MARKET,
    val quantityInput: String = "",
    val limitPriceInput: String = "",
    val availableCash: Double = 0.0,
    val heldQuantity: Double = 0.0,
    val showConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val quantity: Double get() = quantityInput.toDoubleOrNull() ?: 0.0
    val limitPrice: Double? get() = limitPriceInput.toDoubleOrNull()

    val executionPriceEstimate: Double get() = when (orderType) {
        OrderType.MARKET -> coin?.currentPrice ?: 0.0
        OrderType.LIMIT -> limitPrice ?: (coin?.currentPrice ?: 0.0)
    }

    val orderValue: Double get() = quantity * executionPriceEstimate
    // Paper trading is fee-free by design, but the UI surfaces an explicit
    // simulated-fee line at 0.00 so the order summary reads like a real venue.
    val estimatedFee: Double get() = 0.0
}

class OrderViewModel(
    private val tradingRepository: PaperTradingRepository,
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    fun load(coinId: String, side: OrderSide) {
        _uiState.value = _uiState.value.copy(side = side)
        viewModelScope.launch {
            tradingRepository.observeCashBalance().collect { cash ->
                _uiState.value = _uiState.value.copy(availableCash = cash)
            }
        }
        viewModelScope.launch {
            tradingRepository.observePositions().collect { positions ->
                val held = positions.firstOrNull { it.coinId == coinId }?.quantity ?: 0.0
                _uiState.value = _uiState.value.copy(heldQuantity = held)
            }
        }
        viewModelScope.launch {
            when (val result = marketRepository.getCoinsByIds(listOf(coinId))) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    coin = result.data.firstOrNull()
                )
                is NetworkResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message
                )
            }
        }
    }

    fun onQuantityChanged(value: String) {
        _uiState.value = _uiState.value.copy(quantityInput = value, errorMessage = null)
    }

    fun onLimitPriceChanged(value: String) {
        _uiState.value = _uiState.value.copy(limitPriceInput = value, errorMessage = null)
    }

    fun onOrderTypeChanged(type: OrderType) {
        _uiState.value = _uiState.value.copy(orderType = type, errorMessage = null)
    }

    fun requestConfirmation() {
        val state = _uiState.value
        if (state.quantity <= 0.0) {
            _uiState.value = state.copy(errorMessage = "Enter a quantity greater than zero.")
            return
        }
        if (state.orderType == OrderType.LIMIT && (state.limitPrice == null || state.limitPrice <= 0.0)) {
            _uiState.value = state.copy(errorMessage = "Enter a valid limit price.")
            return
        }
        if (state.side == OrderSide.BUY && state.orderValue > state.availableCash) {
            _uiState.value = state.copy(errorMessage = "Order value exceeds your available cash.")
            return
        }
        if (state.side == OrderSide.SELL && state.quantity > state.heldQuantity) {
            _uiState.value = state.copy(errorMessage = "You only hold ${state.heldQuantity} ${state.coin?.symbol}.")
            return
        }
        _uiState.value = state.copy(showConfirmation = true)
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(showConfirmation = false)
    }

    fun confirmPlaceOrder(onFilled: () -> Unit) {
        val state = _uiState.value
        val coin = state.coin ?: return
        viewModelScope.launch {
            val result = when (state.orderType) {
                OrderType.MARKET -> tradingRepository.placeMarketOrder(
                    coinId = coin.id,
                    symbol = coin.symbol,
                    side = state.side,
                    quantity = state.quantity,
                    currentPrice = coin.currentPrice
                )
                OrderType.LIMIT -> {
                    val limitResult = tradingRepository.placeLimitOrder(
                        coinId = coin.id,
                        symbol = coin.symbol,
                        side = state.side,
                        quantity = state.quantity,
                        limitPrice = state.limitPrice ?: coin.currentPrice,
                        currentPrice = coin.currentPrice
                    )
                    when (limitResult) {
                        is TradingResult.Success -> when (limitResult.value) {
                            is PaperTradingRepository.LimitOrderOutcome.Filled -> {
                                _uiState.value = state.copy(
                                    showConfirmation = false,
                                    successMessage = "Paper trade filled."
                                )
                                onFilled()
                            }
                            PaperTradingRepository.LimitOrderOutcome.Queued -> {
                                _uiState.value = state.copy(
                                    showConfirmation = false,
                                    successMessage = "Limit order placed — it will fill once the price is reached."
                                )
                            }
                        }
                        is TradingResult.Error -> _uiState.value = state.copy(
                            showConfirmation = false,
                            errorMessage = limitResult.error.message
                        )
                    }
                    return@launch
                }
            }

            when (result) {
                is TradingResult.Success -> {
                    _uiState.value = state.copy(showConfirmation = false, successMessage = "Paper trade filled.")
                    onFilled()
                }
                is TradingResult.Error -> _uiState.value = state.copy(
                    showConfirmation = false,
                    errorMessage = result.error.message
                )
            }
        }
    }
}
