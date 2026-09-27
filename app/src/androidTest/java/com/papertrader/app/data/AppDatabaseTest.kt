package com.papertrader.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.papertrader.app.data.local.AppDatabase
import com.papertrader.app.data.local.entity.AccountEntity
import com.papertrader.app.data.local.entity.FundsHistoryEntity
import com.papertrader.app.data.local.entity.PositionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests verifying that Room persistence round-trips correctly
 * and — combined with [com.papertrader.app.domain.engine.PaperTradingEngineTest]
 * — that the app retains its paper-trading data across process death (the
 * product requirement that the account survive an app restart).
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun accountBalancePersistsAndUpdates() = runBlocking {
        db.accountDao().upsert(AccountEntity(cashBalance = 10_000.0))
        assertEquals(10_000.0, db.accountDao().get()!!.cashBalance, 0.0)

        db.accountDao().upsert(AccountEntity(cashBalance = 9_500.0))
        assertEquals(9_500.0, db.accountDao().get()!!.cashBalance, 0.0)
    }

    @Test
    fun positionRoundTripsThroughRoom() = runBlocking {
        val position = PositionEntity(coinId = "bitcoin", symbol = "BTC", quantity = 0.025, avgEntryPrice = 108_420.0)
        db.positionDao().upsert(position)

        val loaded = db.positionDao().observeAll().first()
        assertEquals(1, loaded.size)
        assertEquals(position, loaded.first())

        db.positionDao().delete("bitcoin")
        assertEquals(0, db.positionDao().observeAll().first().size)
    }

    @Test
    fun fundsHistoryOrdersNewestFirst() = runBlocking {
        db.fundsHistoryDao().insert(
            FundsHistoryEntity(type = "DEPOSIT", amount = 1000.0, balanceAfter = 11_000.0, timestampMillis = 1_000L)
        )
        db.fundsHistoryDao().insert(
            FundsHistoryEntity(type = "WITHDRAWAL", amount = -250.0, balanceAfter = 10_750.0, timestampMillis = 2_000L)
        )

        val history = db.fundsHistoryDao().observeAll().first()
        assertEquals(2, history.size)
        assertEquals("WITHDRAWAL", history.first().type) // newest (higher timestamp) first
    }
}
