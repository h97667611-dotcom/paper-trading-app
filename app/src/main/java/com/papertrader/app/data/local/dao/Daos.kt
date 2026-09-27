package com.papertrader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.papertrader.app.data.local.entity.AccountEntity
import com.papertrader.app.data.local.entity.FundsHistoryEntity
import com.papertrader.app.data.local.entity.OrderEntity
import com.papertrader.app.data.local.entity.PortfolioSnapshotEntity
import com.papertrader.app.data.local.entity.PositionEntity
import com.papertrader.app.data.local.entity.TradeEntity
import com.papertrader.app.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE id = :id LIMIT 1")
    fun observe(id: Int = AccountEntity.SINGLETON_ID): Flow<AccountEntity?>

    @Query("SELECT * FROM account WHERE id = :id LIMIT 1")
    suspend fun get(id: Int = AccountEntity.SINGLETON_ID): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions ORDER BY symbol ASC")
    fun observeAll(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE coinId = :coinId LIMIT 1")
    suspend fun get(coinId: String): PositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PositionEntity)

    @Query("DELETE FROM positions WHERE coinId = :coinId")
    suspend fun delete(coinId: String)

    @Query("DELETE FROM positions")
    suspend fun clearAll()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = 'OPEN' ORDER BY timestampMillis DESC")
    fun observeOpenOrders(): Flow<List<OrderEntity>>

    @Insert
    suspend fun insert(order: OrderEntity): Long

    @Update
    suspend fun update(order: OrderEntity)

    @Query("DELETE FROM orders")
    suspend fun clearAll()
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<TradeEntity>>

    @Insert
    suspend fun insert(trade: TradeEntity): Long

    @Query("DELETE FROM trades")
    suspend fun clearAll()
}

@Dao
interface FundsHistoryDao {
    @Query("SELECT * FROM funds_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<FundsHistoryEntity>>

    @Insert
    suspend fun insert(entry: FundsHistoryEntity): Long

    @Query("DELETE FROM funds_history")
    suspend fun clearAll()
}

@Dao
interface PortfolioSnapshotDao {
    @Query("SELECT * FROM portfolio_snapshots WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun observeSince(sinceMillis: Long): Flow<List<PortfolioSnapshotEntity>>

    @Insert
    suspend fun insert(snapshot: PortfolioSnapshotEntity): Long

    @Query("DELETE FROM portfolio_snapshots")
    suspend fun clearAll()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = :id LIMIT 1")
    fun observe(id: Int = UserSettingsEntity.SINGLETON_ID): Flow<UserSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)
}
