package com.papertrader.app.domain.engine

import com.papertrader.app.domain.model.OrderType
import com.papertrader.app.domain.model.PortfolioState
import com.papertrader.app.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaperTradingEngineTest {

    private lateinit var engine: PaperTradingEngine
    private val startingState = PortfolioState(cashBalance = 10_000.0, positions = emptyMap())

    @Before
    fun setUp() {
        engine = PaperTradingEngine()
    }

    // ---- Buy orders -------------------------------------------------

    @Test
    fun `buy reduces cash and opens a new position`() {
        val result = engine.executeBuy(
            state = startingState,
            coinId = "bitcoin",
            symbol = "BTC",
            quantity = 0.025,
            executionPrice = 108_420.0,
            orderType = OrderType.MARKET,
            timestampMillis = 0L
        )

        val success = assertSuccess(result)
        val expectedCost = 0.025 * 108_420.0
        assertEquals(10_000.0 - expectedCost, success.newState.cashBalance, 0.01)

        val position = success.newState.positions.getValue("bitcoin")
        assertEquals(0.025, position.quantity, PaperTradingEngine.EPSILON)
        assertEquals(108_420.0, position.avgEntryPrice, 0.01)
        assertEquals(expectedCost, success.trade.totalValue, 0.01)
    }

    @Test
    fun `buy fails when quantity would exceed available cash`() {
        val result = engine.executeBuy(
            state = startingState,
            coinId = "bitcoin",
            symbol = "BTC",
            quantity = 1.0,
            executionPrice = 108_420.0, // costs more than the 10,000 balance
            orderType = OrderType.MARKET,
            timestampMillis = 0L
        )

        assertError(result, EngineError.INSUFFICIENT_FUNDS)
    }

    @Test
    fun `buy fails for zero or negative quantity`() {
        val result = engine.executeBuy(
            state = startingState,
            coinId = "bitcoin",
            symbol = "BTC",
            quantity = 0.0,
            executionPrice = 100.0,
            orderType = OrderType.MARKET,
            timestampMillis = 0L
        )
        assertError(result, EngineError.INVALID_QUANTITY)
    }

    @Test
    fun `repeated buys compute a correct weighted average entry price`() {
        val afterFirst = assertSuccess(
            engine.executeBuy(startingState, "bitcoin", "BTC", 0.01, 100_000.0, OrderType.MARKET, 0L)
        ).newState

        val afterSecond = assertSuccess(
            engine.executeBuy(afterFirst, "bitcoin", "BTC", 0.02, 115_000.0, OrderType.MARKET, 1L)
        ).newState

        val position = afterSecond.positions.getValue("bitcoin")
        // weighted avg = (0.01*100000 + 0.02*115000) / 0.03
        val expectedAvg = (0.01 * 100_000.0 + 0.02 * 115_000.0) / 0.03
        assertEquals(expectedAvg, position.avgEntryPrice, 0.001)
        assertEquals(0.03, position.quantity, PaperTradingEngine.EPSILON)
    }

    // ---- Sell orders / realized P&L ----------------------------------

    @Test
    fun `sell against an existing position realizes correct profit`() {
        val afterBuy = assertSuccess(
            engine.executeBuy(startingState, "bitcoin", "BTC", 0.025, 108_420.0, OrderType.MARKET, 0L)
        ).newState

        val sellResult = engine.executeSell(
            state = afterBuy,
            coinId = "bitcoin",
            symbol = "BTC",
            quantity = 0.025,
            executionPrice = 112_482.0,
            orderType = OrderType.MARKET,
            timestampMillis = 1L
        )

        val success = assertSuccess(sellResult)
        val expectedPnl = (112_482.0 - 108_420.0) * 0.025
        assertEquals(expectedPnl, success.trade.realizedPnl!!, 0.01)
        assertTrue("position should be fully closed", success.newState.positions.isEmpty())

        val expectedCash = 10_000.0 - (0.025 * 108_420.0) + (0.025 * 112_482.0)
        assertEquals(expectedCash, success.newState.cashBalance, 0.01)
    }

    @Test
    fun `sell realizes a loss when execution price is below entry`() {
        val afterBuy = assertSuccess(
            engine.executeBuy(startingState, "eth", "ETH", 1.0, 4_000.0, OrderType.MARKET, 0L)
        ).newState

        val sellResult = assertSuccess(
            engine.executeSell(afterBuy, "eth", "ETH", 1.0, 3_500.0, OrderType.MARKET, 1L)
        )

        assertEquals(-500.0, sellResult.trade.realizedPnl!!, 0.01)
    }

    @Test
    fun `partial sell keeps remaining position at same average entry price`() {
        val afterBuy = assertSuccess(
            engine.executeBuy(startingState, "eth", "ETH", 2.0, 4_000.0, OrderType.MARKET, 0L)
        ).newState

        val afterPartialSell = assertSuccess(
            engine.executeSell(afterBuy, "eth", "ETH", 1.0, 4_200.0, OrderType.MARKET, 1L)
        ).newState

        val remaining = afterPartialSell.positions.getValue("eth")
        assertEquals(1.0, remaining.quantity, PaperTradingEngine.EPSILON)
        assertEquals(4_000.0, remaining.avgEntryPrice, 0.01)
    }

    @Test
    fun `sell fails when there is no open position`() {
        val result = engine.executeSell(startingState, "bitcoin", "BTC", 0.01, 100_000.0, OrderType.MARKET, 0L)
        assertError(result, EngineError.NO_POSITION)
    }

    @Test
    fun `sell fails when quantity exceeds held quantity`() {
        val afterBuy = assertSuccess(
            engine.executeBuy(startingState, "bitcoin", "BTC", 0.01, 100_000.0, OrderType.MARKET, 0L)
        ).newState

        val result = engine.executeSell(afterBuy, "bitcoin", "BTC", 0.02, 100_000.0, OrderType.MARKET, 1L)
        assertError(result, EngineError.INSUFFICIENT_POSITION)
    }

    @Test
    fun `closePosition fully liquidates at the given market price`() {
        val afterBuy = assertSuccess(
            engine.executeBuy(startingState, "bitcoin", "BTC", 0.025, 108_420.0, OrderType.MARKET, 0L)
        ).newState

        val closed = assertSuccess(
            engine.closePosition(afterBuy, "bitcoin", 112_482.0, 1L)
        )

        assertTrue(closed.newState.positions.isEmpty())
        assertEquals((112_482.0 - 108_420.0) * 0.025, closed.trade.realizedPnl!!, 0.01)
    }

    // ---- Unrealized P&L ----------------------------------------------

    @Test
    fun `unrealizedPnl reflects the difference between current and entry price`() {
        val position = Position("bitcoin", "BTC", 0.025, 108_420.0)
        val pnl = engine.unrealizedPnl(position, 112_482.0)
        assertEquals((112_482.0 - 108_420.0) * 0.025, pnl, 0.01)
    }

    // ---- Funds management ----------------------------------------------

    @Test
    fun `addFunds increases cash balance`() {
        val result = assertSuccess(engine.addFunds(startingState, 1_000.0))
        assertEquals(11_000.0, result.newCashBalance, 0.01)
    }

    @Test
    fun `addFunds rejects zero or negative amounts`() {
        assertError(engine.addFunds(startingState, 0.0), EngineError.INVALID_AMOUNT)
        assertError(engine.addFunds(startingState, -50.0), EngineError.INVALID_AMOUNT)
    }

    @Test
    fun `withdrawFunds decreases cash balance`() {
        val result = assertSuccess(engine.withdrawFunds(startingState, 500.0))
        assertEquals(9_500.0, result.newCashBalance, 0.01)
    }

    @Test
    fun `withdrawFunds rejects amount larger than available cash`() {
        assertError(engine.withdrawFunds(startingState, 999_999.0), EngineError.INSUFFICIENT_FUNDS)
    }

    // ---- Portfolio valuation --------------------------------------------

    @Test
    fun `portfolioValue sums cash plus mark-to-market position value`() {
        val afterBuy = assertSuccess(
            engine.executeBuy(startingState, "bitcoin", "BTC", 0.025, 108_420.0, OrderType.MARKET, 0L)
        ).newState

        val total = engine.portfolioValue(afterBuy) { coinId ->
            if (coinId == "bitcoin") 112_482.0 else null
        }

        val expected = afterBuy.cashBalance + (0.025 * 112_482.0)
        assertEquals(expected, total, 0.01)
    }

    // ---- helpers ---------------------------------------------------------

    private fun <T> assertSuccess(result: TradingResult<T>): T {
        assertTrue("expected Success but was $result", result is TradingResult.Success)
        return (result as TradingResult.Success).value
    }

    private fun <T> assertError(result: TradingResult<T>, expected: EngineError) {
        assertTrue("expected Error but was $result", result is TradingResult.Error)
        assertEquals(expected, (result as TradingResult.Error).error)
    }
}
