package com.papertrader.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.papertrader.app.data.local.dao.AccountDao
import com.papertrader.app.data.local.dao.FundsHistoryDao
import com.papertrader.app.data.local.dao.OrderDao
import com.papertrader.app.data.local.dao.PortfolioSnapshotDao
import com.papertrader.app.data.local.dao.PositionDao
import com.papertrader.app.data.local.dao.TradeDao
import com.papertrader.app.data.local.dao.UserSettingsDao
import com.papertrader.app.data.local.entity.AccountEntity
import com.papertrader.app.data.local.entity.FundsHistoryEntity
import com.papertrader.app.data.local.entity.OrderEntity
import com.papertrader.app.data.local.entity.PortfolioSnapshotEntity
import com.papertrader.app.data.local.entity.PositionEntity
import com.papertrader.app.data.local.entity.TradeEntity
import com.papertrader.app.data.local.entity.UserSettingsEntity

/**
 * Single local Room database. All paper-trading state (cash, positions,
 * orders, trades, funds history, portfolio history, settings) survives app
 * restarts through this database — nothing here ever talks to a real
 * exchange or wallet.
 */
@Database(
    entities = [
        AccountEntity::class,
        PositionEntity::class,
        OrderEntity::class,
        TradeEntity::class,
        FundsHistoryEntity::class,
        PortfolioSnapshotEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun positionDao(): PositionDao
    abstract fun orderDao(): OrderDao
    abstract fun tradeDao(): TradeDao
    abstract fun fundsHistoryDao(): FundsHistoryDao
    abstract fun portfolioSnapshotDao(): PortfolioSnapshotDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "papertrader.db"
                ).build().also { instance = it }
            }
    }
}
