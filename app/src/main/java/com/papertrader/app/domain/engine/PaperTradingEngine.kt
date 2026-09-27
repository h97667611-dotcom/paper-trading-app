package com.papertrader.app.domain.engine

import com.papertrader.app.domain.model.FundsTransactionType
import com.papertrader.app.domain.model.OrderSide
import com.papertrader.app.domain.model.OrderType
import com.papertrader.app.domain.model.PortfolioState
import com.papertrader.app.domain.model.Position
import com.papertrader.app.domain.model.Trade
import kotlin.math.abs

/**
 * Pure, framework-free paper-trading engine.
 *
 * Every function takes the current [PortfolioState] as an explicit argument
 * and returns a new state plus the resulting [Trade] record — nothing is
 * mutated in place and there is no I/O. This keeps the engine trivially unit
 * testable and lets the repository layer be the only place that persists the
 * returned state to Room.
 *
 * All order execution in this app is simulated ("paper"): no network call to
 * a real exchange is ever made here.
 */
class PaperTradingEngine {

    data class ExecutionResult(
        val newState: PortfolioState,
        val trade: Trade
    )

    data class FundsResult(
        val newCashBalance: Double
    )

    /**
     * Executes a simulated market or limit BUY.
     *
     * For a MARKET order [executionPrice] is the current market price.
     * For a LIMIT order [executionPrice] is the price the order fills at
     * (the caller is responsible for only calling this once the limit
     * condition has been met — the engine itself just books the fill).
     */
    fun executeBuy(
        state: PortfolioState,
        coinId: String,
        symbol: String,
        quantity: Double,
        executionPrice: Double,
        orderType: OrderType,
        timestampMillis: Long
    ): TradingResult<ExecutionResult> {
        if (quantity <= 0.0) return TradingResult.Error(EngineError.INVALID_QUANTITY)
        if (executionPrice <= 0.0) return TradingResult.Error(EngineError.INVALID_PRICE)

        val totalCost = quantity * executionPrice
        if (totalCost > state.cashBalance + EPSILON) {
            return TradingResult.Error(EngineError.INSUFFICIENT_FUNDS)
        }

        val existing = state.positions[coinId]
        val newQuantity = (existing?.quantity ?: 0.0) + quantity
        val newAvgEntry = if (existing == null) {
            executionPrice
        } else {
            // Weighted-average entry price across old + new quantity.
            ((existing.avgEntryPrice * existing.quantity) + (executionPrice * quantity)) / newQuantity
        }

        val newPositions = state.positions.toMutableMap()
        newPositions[coinId] = Position(coinId, symbol, newQuantity, newAvgEntry)

        val newState = state.copy(
            cashBalance = round2(state.cashBalance - totalCost),
            positions = newPositions
        )

        val trade = Trade(
            coinId = coinId,
            symbol = symbol,
            side = OrderSide.BUY,
            quantity = quantity,
            price = executionPrice,
            totalValue = round2(totalCost),
            realizedPnl = null,
            orderType = orderType,
            timestampMillis = timestampMillis
        )

        return TradingResult.Success(ExecutionResult(newState, trade))
    }

    /**
     * Executes a simulated market or limit SELL (fully or partially closing
     * a position). Realized P&L is computed against the position's average
     * entry price.
     */
    fun executeSell(
        state: PortfolioState,
        coinId: String,
        symbol: String,
        quantity: Double,
        executionPrice: Double,
        orderType: OrderType,
        timestampMillis: Long
    ): TradingResult<ExecutionResult> {
        if (quantity <= 0.0) return TradingResult.Error(EngineError.INVALID_QUANTITY)
        if (executionPrice <= 0.0) return TradingResult.Error(EngineError.INVALID_PRICE)

        val existing = state.positions[coinId]
            ?: return TradingResult.Error(EngineError.NO_POSITION)

        if (quantity > existing.quantity + EPSILON) {
            return TradingResult.Error(EngineError.INSUFFICIENT_POSITION)
        }

        val proceeds = quantity * executionPrice
        val realizedPnl = (executionPrice - existing.avgEntryPrice) * quantity

        val remainingQuantity = existing.quantity - quantity
        val newPositions = state.positions.toMutableMap()
        if (remainingQuantity <= EPSILON) {
            newPositions.remove(coinId)
        } else {
            // Average entry price is unchanged on a partial sell.
            newPositions[coinId] = existing.copy(quantity = remainingQuantity)
        }

        val newState = state.copy(
            cashBalance = round2(state.cashBalance + proceeds),
            positions = newPositions
        )

        val trade = Trade(
            coinId = coinId,
            symbol = symbol,
            side = OrderSide.SELL,
            quantity = quantity,
            price = executionPrice,
            totalValue = round2(proceeds),
            realizedPnl = round2(realizedPnl),
            orderType = orderType,
            timestampMillis = timestampMillis
        )

        return TradingResult.Success(ExecutionResult(newState, trade))
    }

    /** Closes an entire open position at the current market price. */
    fun closePosition(
        state: PortfolioState,
        coinId: String,
        currentPrice: Double,
        timestampMillis: Long
    ): TradingResult<ExecutionResult> {
        val existing = state.positions[coinId]
            ?: return TradingResult.Error(EngineError.NO_POSITION)
        return executeSell(
            state = state,
            coinId = coinId,
            symbol = existing.symbol,
            quantity = existing.quantity,
            executionPrice = currentPrice,
            orderType = OrderType.MARKET,
            timestampMillis = timestampMillis
        )
    }

    fun addFunds(state: PortfolioState, amount: Double): TradingResult<FundsResult> {
        if (amount <= 0.0) return TradingResult.Error(EngineError.INVALID_AMOUNT)
        return TradingResult.Success(FundsResult(round2(state.cashBalance + amount)))
    }

    fun withdrawFunds(state: PortfolioState, amount: Double): TradingResult<FundsResult> {
        if (amount <= 0.0) return TradingResult.Error(EngineError.INVALID_AMOUNT)
        if (amount > state.cashBalance + EPSILON) {
            return TradingResult.Error(EngineError.INSUFFICIENT_FUNDS)
        }
        return TradingResult.Success(FundsResult(round2(state.cashBalance - amount)))
    }

    fun fundsTransactionType(amountDelta: Double): FundsTransactionType =
        if (amountDelta >= 0) FundsTransactionType.DEPOSIT else FundsTransactionType.WITHDRAWAL

    fun unrealizedPnl(position: Position, currentPrice: Double): Double =
        round2(position.unrealizedPnl(currentPrice))

    fun portfolioValue(state: PortfolioState, priceOf: (String) -> Double?): Double =
        round2(state.totalValue(priceOf))

    private fun round2(value: Double): Double {
        val factor = 100.0
        return kotlin.math.round(value * factor) / factor
    }

    companion object {
        /** Tolerance for floating point comparisons on quantities/cash. */
        const val EPSILON = 1e-8
    }
}

/** True if [a] and [b] are equal within the engine's floating point epsilon. */
fun approximatelyEquals(a: Double, b: Double): Boolean = abs(a - b) < PaperTradingEngine.EPSILON
