package pl.terminownik.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE section = :section ORDER BY updatedAt DESC")
    fun observeSection(section: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: Long): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE backupUid IS NOT NULL AND backupUid = :backupUid LIMIT 1")
    suspend fun findByBackupUid(backupUid: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ItemEntity>): List<Long>

    @Upsert
    suspend fun upsert(entity: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM items WHERE section = :section AND status = 'ACTIVE'")
    fun observeActiveCount(section: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Query(
        "UPDATE items SET status = :status, statusChangedAt = :timestamp, updatedAt = :timestamp WHERE id = :id",
    )
    suspend fun updateStatus(id: Long, status: String, timestamp: Long)

    @Query("UPDATE items SET reminderEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateReminderEnabled(id: Long, enabled: Boolean, updatedAt: Long)
}

@Dao
interface BarcodeNameDao {

    @Query("SELECT * FROM barcode_names WHERE barcode = :barcode")
    suspend fun get(barcode: String): BarcodeNameEntity?

    @Query("SELECT * FROM barcode_names WHERE barcode = :barcode")
    fun observe(barcode: String): Flow<BarcodeNameEntity?>

    @Query("SELECT * FROM barcode_names ORDER BY useCount DESC, updatedAt DESC")
    suspend fun getAll(): List<BarcodeNameEntity>

    @Query("SELECT * FROM barcode_names ORDER BY useCount DESC, updatedAt DESC")
    fun observeAll(): Flow<List<BarcodeNameEntity>>

    /**
     * Zapis „kod → ostatnia nazwa wpisana przez użytkownika”.
     * Nazwa pochodzi zawsze od użytkownika, nigdy z zewnętrznej bazy produktów.
     */
    @Transaction
    suspend fun remember(barcode: String, name: String, unit: String?, section: String, now: Long) {
        val existing = get(barcode)
        val entity = BarcodeNameEntity(
            barcode = barcode,
            name = name,
            unit = unit ?: existing?.unit,
            section = section,
            updatedAt = now,
            useCount = (existing?.useCount ?: 0) + 1,
        )
        upsert(entity)
    }

    @Upsert
    suspend fun upsert(entity: BarcodeNameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BarcodeNameEntity>)

    @Query("DELETE FROM barcode_names")
    suspend fun deleteAll()

    @Query("DELETE FROM barcode_names WHERE barcode = :barcode")
    suspend fun delete(barcode: String)

    @Query("SELECT COUNT(*) FROM barcode_names")
    suspend fun count(): Int
}

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_items ORDER BY done ASC, createdAt DESC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAll(): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShoppingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ShoppingItemEntity>): List<Long>

    @Upsert
    suspend fun upsert(entity: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM shopping_items WHERE done = 1")
    suspend fun deleteDone()

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM shopping_items")
    suspend fun count(): Int
}

@Dao
interface ReminderLogDao {

    @Query("SELECT reminderKey FROM reminder_log")
    suspend fun allKeys(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM reminder_log WHERE reminderKey = :key)")
    suspend fun contains(key: String): Boolean

    @Query("SELECT * FROM reminder_log")
    suspend fun getAll(): List<ReminderLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ReminderLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ReminderLogEntity>)

    /** Po edycji/usunięciu pozycji czyścimy jej historię, żeby nowe przypomnienie nie zostało stłumione. */
    @Query("DELETE FROM reminder_log WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    @Query("DELETE FROM reminder_log")
    suspend fun deleteAll()
}

@Dao
interface ScheduledAlarmDao {

    @Query("SELECT * FROM scheduled_alarms")
    suspend fun getAll(): List<ScheduledAlarmEntity>

    @Query("SELECT * FROM scheduled_alarms WHERE itemId = :itemId")
    suspend fun getForItem(itemId: Long): List<ScheduledAlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduledAlarmEntity)

    @Query("DELETE FROM scheduled_alarms WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    @Query("DELETE FROM scheduled_alarms WHERE reminderKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM scheduled_alarms")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM scheduled_alarms")
    suspend fun count(): Int
}
