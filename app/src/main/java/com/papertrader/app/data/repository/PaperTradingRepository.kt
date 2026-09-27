package com.papertrader.app.data.repository

import com.papertrader.app.data.local.dao.AccountDao
import com.papertrader.app.data.local.dao.FundsHistoryDao
import com.papertrader.app.data.local.dao.OrderDao
import com.papertrader.app.data.local.dao.PortfolioSnapshotDao
import com.papertrader.app.data.local.dao.PositionDao
import com.papertrader.app.data.local.dao.TradeDao
import com.papertrader.app.data.local.entity.AccountEntity
import com.papertrader.app.data.local.entity.FundsHistoryEntity
import com.papertrader.app.data.local.entity.OrderEntity
import com.papertrader.app.data.local.entity.PortfolioSnapshotEntity
import com.papertrader.app.data.local.entity.PositionEntity
import com.papertrader.app.data.local.entity.TradeEntity
import com.papertrader.app.domain.engine.EngineError
import com.papertrader.app.domain.engine.PaperTradingEngine
import com.papertrader.app.domain.engine.TradingResult
import com.papertrader.app.domain.model.FundsTransaction
import com.papertrader.app.domain.model.FundsTransactionType
import com.papertrader.app.domain.model.OrderSide
import com.papertrader.app.domain.model.OrderStatus
import com.papertrader.app.domain.model.OrderType
import com.papertrader.app.domain.model.PortfolioState
import com.papertrader.app.domain.model.Position
import com.papertrader.app.domain.model.Trade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Bridges the pure [PaperTradingEngine] to Room persistence. This is the
 * only place in the app that both runs engine calculations AND writes their
 * results to disk, keeping the engine itself side-effect free and testable.
 */
class PaperTradingRepository(
    private val accountDao: AccountDao,
    private val positionDao: PositionDao,
    private val orderDao: OrderDao,
    private val tradeDao: TradeDao,
    private val fundsHistoryDao: FundsHistoryDao,
    private val portfolioSnapshotDao: PortfolioSnapshotDao,
    private val engine: PaperTradingEngine = PaperTradingEngine()
) {

    suspend fun ensureAccountInitialized() {
        if (accountDao.get() == null) {
            accountDao.upsert(AccountEntity(cashBalance = AccountEntity.DEFAULT_STARTING_CASH))
        }
    }

    fun observeCashBalance(): Flow<Double> =
        accountDao.observe().map { it?.cashBalance ?: AccountEntity.DEFAULT_STARTING_CASH }

    fun observePositions(): Flow<List<Position>> =
        positionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTrades(): Flow<List<Trade>> =
        tradeDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeOpenOrders(): Flow<List<OrderEntity>> = orderDao.observeOpenOrders()

    fun observeFundsHistory(): Flow<List<FundsTransaction>> =
        fundsHistoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    private suspend fun currentState(): PortfolioState {
        val cash = accountDao.get()?.cashBalance ?: AccountEntity.DEFAULT_STARTING_CASH
        val positions = positionDao.observeAll().first().associate { it.coinId to it.toDomain() }
        return PortfolioState(cash, positions)
    }

    /** Places and immediately fills a simulated MARKET order (buy or sell). */
    suspend fun placeMarketOrder(
        coinId: String,
        symbol: String,
        side: OrderSide,
        quantity: Double,
        currentPrice: Double
    ): TradingResult<Trade> = executeAndPersist(coinId, symbol, side, quantity, currentPrice, OrderType.MARKET, null)

    /**
     * Places a simulated LIMIT order. In this local-only simulation the
     * order fills immediately once the limit condition is already satisfied
     * by [currentPrice]; otherwise it is stored as OPEN for later execution
     * by [tryFillOpenLimitOrders].
     */
    /**
     * Result of submitting a limit order: it either fills immediately
     * (condition already met at the current price) or is queued as an open
     * order for [tryFillOpenLimitOrders] to fill later.
     */
    sealed class LimitOrderOutcome {
        data class Filled(val trade: Trade) : LimitOrderOutcome()
        data object Queued : LimitOrderOutcome()
    }

    suspend fun placeLimitOrder(
        coinId: String,
        symbol: String,
        side: OrderSide,
        quantity: Double,
        limitPrice: Double,
        currentPrice: Double
    ): TradingResult<LimitOrderOutcome> {
        if (limitPrice <= 0.0) return TradingResult.Error(EngineError.INVALID_LIMIT_PRICE)
        if (quantity <= 0.0) return TradingResult.Error(EngineError.INVALID_QUANTITY)

        val limitConditionMet = when (side) {
            OrderSide.BUY -> currentPrice <= limitPrice
            OrderSide.SELL -> currentPrice >= limitPrice
        }

        if (!limitConditionMet) {
            orderDao.insert(
                OrderEntity(
                    coinId = coinId,
                    symbol = symbol,
                    side = side.name,
                    type = OrderType.LIMIT.name,
                    quantity = quantity,
                    limitPrice = limitPrice,
                    status = OrderStatus.OPEN.name,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            return TradingResult.Success(LimitOrderOutcome.Queued)
        }

        return when (val result = executeAndPersist(coinId, symbol, side, quantity, currentPrice, OrderType.LIMIT, limitPrice)) {
            is TradingResult.Success -> TradingResult.Success(LimitOrderOutcome.Filled(result.value))
            is TradingResult.Error -> result
        }
    }

    /** Call periodically (or on price refresh) to fill any queued limit orders whose condition is now met. */
    suspend fun tryFillOpenLimitOrders(currentPricesByCoinId: Map<String, Double>) {
        val open = orderDao.observeOpenOrders().first()
        for (order in open) {
            val price = currentPricesByCoinId[order.coinId] ?: continue
            val side = OrderSide.valueOf(order.side)
            val limit = order.limitPrice ?: continue
            val conditionMet = when (side) {
                OrderSide.BUY -> price <= limit
                OrderSide.SELL -> price >= limit
            }
            if (!conditionMet) continue

            val result = executeAndPersist(
                order.coinId, order.symbol, side, order.quantity, price, OrderType.LIMIT, limit
            )
            if (result is TradingResult.Success) {
                orderDao.update(order.copy(status = OrderStatus.FILLED.name))
            }
        }
    }

    suspend fun closePosition(coinId: String, currentPrice: Double): TradingResult<Trade> {
        val state = currentState()
        val position = state.positions[coinId]
            ?: return TradingResult.Error(EngineError.NO_POSITION)
        return placeMarketOrder(coinId, position.symbol, OrderSide.SELL, position.quantity, currentPrice)
    }

    suspend fun addFunds(amount: Double): TradingResult<Double> {
        val state = currentState()
        return when (val result = engine.addFunds(state, amount)) {
            is TradingResult.Success -> {
                persistCashBalance(result.value.newCashBalance)
                recordFundsTransaction(FundsTransactionType.DEPOSIT, amount, result.value.newCashBalance)
                TradingResult.Success(result.value.newCashBalance)
            }
            is TradingResult.Error -> result
        }
    }

    suspend fun withdrawFunds(amount: Double): TradingResult<Double> {
        val state = currentState()
        return when (val result = engine.withdrawFunds(state, amount)) {
            is TradingResult.Success -> {
                persistCashBalance(result.value.newCashBalance)
                recordFundsTransaction(FundsTransactionType.WITHDRAWAL, -amount, result.value.newCashBalance)
                TradingResult.Success(result.value.newCashBalance)
            }
            is TradingResult.Error -> result
        }
    }

    suspend fun resetAccount() {
        positionDao.clearAll()
        orderDao.clearAll()
        tradeDao.clearAll()
        fundsHistoryDao.clearAll()
        portfolioSnapshotDao.clearAll()
        accountDao.upsert(AccountEntity(cashBalance = AccountEntity.DEFAULT_STARTING_CASH))
    }

    suspend fun recordPortfolioSnapshot(totalValue: Double) {
        portfolioSnapshotDao.insert(
            PortfolioSnapshotEntity(totalValue = totalValue, timestampMillis = System.currentTimeMillis())
        )
    }

    fun observeSnapshotsSince(sinceMillis: Long) = portfolioSnapshotDao.observeSince(sinceMillis)

    // ---- internal helpers ------------------------------------------------

    private suspend fun executeAndPersist(
        coinId: String,
        symbol: String,
        side: OrderSide,
        quantity: Double,
        executionPrice: Double,
        orderType: OrderType,
        limitPrice: Double?
    ): TradingResult<Trade> {
        val state = currentState()
        val now = System.currentTimeMillis()

        val engineResult = when (side) {
            OrderSide.BUY -> engine.executeBuy(state, coinId, symbol, quantity, executionPrice, orderType, now)
            OrderSide.SELL -> engine.executeSell(state, coinId, symbol, quantity, executionPrice, orderType, now)
        }

        return when (engineResult) {
            is TradingResult.Success -> {
                val execution = engineResult.value
                persistCashBalance(execution.newState.cashBalance)
                persistPositions(execution.newState)
                orderDao.insert(
                    OrderEntity(
                        coinId = coinId,
                        symbol = symbol,
                        side = side.name,
                        type = orderType.name,
                        quantity = quantity,
                        limitPrice = limitPrice,
                        status = OrderStatus.FILLED.name,
                        timestampMillis = now
                    )
                )
                tradeDao.insert(execution.trade.toEntity())
                TradingResult.Success(execution.trade)
            }
            is TradingResult.Error -> engineResult
        }
    }

    private suspend fun persistCashBalance(newBalance: Double) {
        accountDao.upsert(AccountEntity(cashBalance = newBalance))
    }

    private suspend fun persistPositions(newState: PortfolioState) {
        val existingIds = positionDao.observeAll().first().map { it.coinId }.toSet()
        val newIds = newState.positions.keys
        (existingIds - newIds).forEach { positionDao.delete(it) }
        newState.positions.values.forEach { positionDao.upsert(it.toEntity()) }
    }

    private suspend fun recordFundsTransaction(type: FundsTransactionType, amountDelta: Double, balanceAfter: Double) {
        fundsHistoryDao.insert(
            FundsHistoryEntity(
                type = type.name,
                amount = amountDelta,
                balanceAfter = balanceAfter,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }
}

private fun PositionEntity.toDomain() = Position(coinId, symbol, quantity, avgEntryPrice)
private fun Position.toEntity() = PositionEntity(coinId, symbol, quantity, avgEntryPrice)

private fun TradeEntity.toDomain() = Trade(
    id = id,
    coinId = coinId,
    symbol = symbol,
    side = OrderSide.valueOf(side),
    quantity = quantity,
    price = price,
    totalValue = totalValue,
    realizedPnl = realizedPnl,
    orderType = OrderType.valueOf(orderType),
    timestampMillis = timestampMillis
)

private fun Trade.toEntity() = TradeEntity(
    coinId = coinId,
    symbol = symbol,
    side = side.name,
    quantity = quantity,
    price = price,
    totalValue = totalValue,
    realizedPnl = realizedPnl,
    orderType = orderType.name,
    timestampMillis = timestampMillis
)

private fun FundsHistoryEntity.toDomain() = FundsTransaction(
    id = id,
    type = FundsTransactionType.valueOf(type),
    amount = amount,
    balanceAfter = balanceAfter,
    timestampMillis = timestampMillis
)
