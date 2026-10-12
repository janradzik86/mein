package pl.terminownik.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pozycja spisu = jedna partia.
 *
 * Celowo NIE ma indeksu unikalnego po kodzie kreskowym ani po nazwie:
 * specyfikacja wymaga, żeby dwie sztuki z tym samym kodem i różnymi terminami
 * pozostały rozdzielone i żeby zużycie jednej nie usuwało drugiej.
 */
@Entity(
    tableName = "items",
    indices = [
        Index("section"),
        Index("status"),
        Index("expiryEpochDay"),
        Index("barcode"),
        Index("backupUid"),
    ],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val section: String,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val storageLocation: String?,
    val photoPath: String?,
    val batchCode: String?,
    val barcode: String?,
    val barcodeFormat: String?,
    val expiryEpochDay: Long?,
    val expiryDayPrecision: String?,
    val expiryDateType: String?,
    val expiryDateSource: String?,
    val expiryConfirmed: Boolean,
    val note: String?,
    val status: String,
    val reminderEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val statusChangedAt: Long?,
    val backupUid: String?,
)

/** Lokalny słownik: kod produktu → ostatnia nazwa wpisana przez użytkownika (specyfikacja, funkcja 4). */
@Entity(tableName = "barcode_names")
data class BarcodeNameEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val unit: String?,
    val section: String,
    val updatedAt: Long,
    val useCount: Int,
)

/** Lista zakupowa (specyfikacja, funkcja 12). */
@Entity(tableName = "shopping_items", indices = [Index("done")])
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val sectionHint: String?,
    val done: Boolean,
    val note: String?,
    val createdAt: Long,
    val sourceItemId: Long?,
)

/**
 * Dziennik wysłanych przypomnień – gwarancja braku duplikatów (specyfikacja: „Nie wysyłaj duplikatów”).
 * Klucz zmienia się po edycji daty/wyprzedzenia, więc edycja tworzy nowe przypomnienie,
 * a stare przestaje obowiązywać.
 */
@Entity(tableName = "reminder_log")
data class ReminderLogEntity(
    @PrimaryKey val reminderKey: String,
    val itemId: Long,
    val scheduledFireEpochDay: Long,
    val sentAt: Long,
    val kind: String,
)

/** Stan zaplanowanych alarmów – pozwala anulować dokładnie te, które dotyczą danej pozycji. */
@Entity(tableName = "scheduled_alarms", indices = [Index("itemId")])
data class ScheduledAlarmEntity(
    @PrimaryKey val reminderKey: String,
    val itemId: Long,
    val requestCode: Int,
    val fireAtMillis: Long,
)
