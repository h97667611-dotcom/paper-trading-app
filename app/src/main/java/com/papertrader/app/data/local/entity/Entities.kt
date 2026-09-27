package com.papertrader.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val cashBalance: Double
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_STARTING_CASH = 10_000.0
    }
}

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val coinId: String,
    val symbol: String,
    val quantity: Double,
    val avgEntryPrice: Double
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coinId: String,
    val symbol: String,
    val side: String, // OrderSide.name
    val type: String, // OrderType.name
    val quantity: Double,
    val limitPrice: Double?,
    val status: String, // OrderStatus.name
    val timestampMillis: Long
)

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coinId: String,
    val symbol: String,
    val side: String,
    val quantity: Double,
    val price: Double,
    val totalValue: Double,
    val realizedPnl: Double?,
    val orderType: String,
    val timestampMillis: Long
)

@Entity(tableName = "funds_history")
data class FundsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // FundsTransactionType.name
    val amount: Double,
    val balanceAfter: Double,
    val timestampMillis: Long
)

@Entity(tableName = "portfolio_snapshots")
data class PortfolioSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val totalValue: Double,
    val timestampMillis: Long
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val currency: String = "USD",
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = true
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
