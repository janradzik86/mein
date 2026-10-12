package pl.terminownik.app.data

import android.net.Uri
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.terminownik.app.data.local.BarcodeNameEntity
import pl.terminownik.app.data.local.TerminownikDatabase
import pl.terminownik.app.data.local.toDomain
import pl.terminownik.app.data.local.toEntity
import pl.terminownik.app.data.photo.PhotoStorage
import pl.terminownik.app.data.settings.SettingsRepository
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.BarcodeName
import pl.terminownik.app.domain.ExpiryBucket
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.ExpiryRules
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.ItemWithExpiry
import pl.terminownik.app.domain.PantryItem
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.Section
import pl.terminownik.app.domain.SectionSummary
import pl.terminownik.app.domain.ShoppingItem
import pl.terminownik.app.notifications.ReminderScheduler

/**
 * Logika biznesowa spisu: zapis, odczyt, oznaczanie zużycia/wyrzucenia/uzupełnienia,
 * słownik kodów i lista zakupowa.
 *
 * Każda zmiana danych synchronizuje przypomnienia (specyfikacja: „Edycja lub usunięcie
 * zdarzenia ma aktualizować jego przypomnienie”).
 */
class PantryRepository(
    private val database: TerminownikDatabase,
    private val settings: SettingsRepository,
    private val photoStorage: PhotoStorage,
    private val reminderScheduler: ReminderScheduler,
) {

    private val itemDao = database.itemDao()
    private val barcodeDao = database.barcodeNameDao()
    private val shoppingDao = database.shoppingDao()
    private val reminderLogDao = database.reminderLogDao()

    /** Wszystkie pozycje z oceną terminu liczoną na dziś i według ustawień użytkownika. */
    /** Migawka listy z oceną terminów – używana przy eksporcie CSV. */
    suspend fun itemsWithExpirySnapshot(): List<ItemWithExpiry> = itemsWithExpiry.first()

    val itemsWithExpiry: Flow<List<ItemWithExpiry>> =
        combine(itemDao.observeAll(), settings.settings) { entities, appSettings ->
            val today = PlFormat.todayEpochDay()
            entities
                .map { it.toDomain() }
                .map { ItemWithExpiry(it, ExpiryRules.assess(it, today, appSettings.reminderLeadDays)) }
        }

    val allItems: Flow<List<PantryItem>> = itemDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Resolver potrzebny do zapisu plików wybranych przez użytkownika (SAF). */
    fun contentResolver(): android.content.ContentResolver = context.contentResolver

    /** Ustawienia użytkownika – używane do liczenia wyprzedzenia przypomnień w listach. */
    fun settingsFlow(): Flow<AppSettings> = settings.settings

    /** Jednorazowy odczyt ustawień (np. przy inicjalizacji formularza). */
    suspend fun settingsOnce(): AppSettings = settings.settings.first()

    val shoppingList: Flow<List<ShoppingItem>> =
        shoppingDao.observeAll().map { list -> list.map { it.toDomain() } }

    val barcodeNames: Flow<List<BarcodeName>> =
        barcodeDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeItem(id: Long): Flow<PantryItem?> = itemDao.observeById(id).map { it?.toDomain() }

    suspend fun getItem(id: Long): PantryItem? = itemDao.getById(id)?.toDomain()

    suspend fun summaries(): List<SectionSummary> {
        val today = PlFormat.todayEpochDay()
        val items = itemDao.getAll().map { it.toDomain() }
        return Section.entries.map { section ->
            val scoped = items.filter { it.section == section && it.status == ItemStatus.ACTIVE }
            val assessed = scoped.map { ExpiryRules.assess(it, today, 7) }
            SectionSummary(
                section = section,
                activeCount = scoped.size,
                expiringSoon = assessed.count {
                    it.bucket == ExpiryBucket.EXPIRING_SOON || it.bucket == ExpiryBucket.DUE_TODAY
                },
                expired = assessed.count { it.bucket == ExpiryBucket.EXPIRED },
                noDate = assessed.count { it.bucket == ExpiryBucket.NO_DATE },
                unconfirmed = assessed.count { it.bucket == ExpiryBucket.UNCONFIRMED },
            )
        }
    }

    suspend fun addOrUpdate(item: PantryItem): Long {
        val now = System.currentTimeMillis()
        val withUid = item.copy(
            backupUid = item.backupUid ?: UUID.randomUUID().toString(),
            createdAt = if (item.createdAt == 0L) now else item.createdAt,
            updatedAt = now,
        )
        val entity = withUid.toEntity()
        val id = if (entity.id == 0L) itemDao.insert(entity) else {
            itemDao.upsert(entity)
            entity.id
        }
        rememberBarcodeName(withUid.copy(id = id))
        reminderScheduler.rescheduleItem(id)
        return id
    }

    suspend fun deleteItem(id: Long) {
        val existing = itemDao.getById(id)
        existing?.photoPath?.let { photoStorage.delete(it) }
        reminderScheduler.cancelItem(id)
        reminderLogDao.deleteForItem(id)
        itemDao.deleteById(id)
    }

    /** Oznaczenie zużycia – nie usuwa innych partii tego samego produktu. */
    suspend fun markUsed(id: Long) = changeStatus(id, ItemStatus.USED)

    suspend fun markDiscarded(id: Long) = changeStatus(id, ItemStatus.DISCARDED)

    /** Przywrócenie pozycji do aktywnych (cofnięcie zużycia/wyrzucenia). */
    suspend fun markActive(id: Long) = changeStatus(id, ItemStatus.ACTIVE)

    private suspend fun changeStatus(id: Long, status: ItemStatus) {
        itemDao.updateStatus(id, status.name, System.currentTimeMillis())
        reminderScheduler.rescheduleItem(id)
    }

    /**
     * Uzupełnienie = NOWA partia.
     *
     * Specyfikacja wymaga, żeby partii nie łączyć automatycznie, więc uzupełnienie tworzy
     * osobny wiersz z nową datą, a poprzednia partia zostaje w historii.
     */
    suspend fun restock(
        sourceId: Long,
        newQuantity: Double?,
        newExpiry: ExpiryDate?,
        newBatchCode: String?,
    ): Long {
        val source = itemDao.getById(sourceId)?.toDomain() ?: return -1L
        val copy = source.copy(
            id = 0L,
            quantity = newQuantity ?: source.quantity,
            expiryDate = newExpiry ?: source.expiryDate?.copy(confirmed = false),
            batchCode = newBatchCode ?: source.batchCode,
            status = ItemStatus.ACTIVE,
            photoPath = null,
            backupUid = null,
            createdAt = 0L,
            updatedAt = 0L,
            statusChangedAt = null,
        )
        return addOrUpdate(copy)
    }

    /** Zmiana ilości bez tworzenia nowej partii (np. „ubyło pół opakowania”). */
    suspend fun updateQuantity(id: Long, quantity: Double?) {
        val current = itemDao.getById(id) ?: return
        itemDao.upsert(current.copy(quantity = quantity, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setReminderEnabled(id: Long, enabled: Boolean) {
        itemDao.updateReminderEnabled(id, enabled, System.currentTimeMillis())
        reminderScheduler.rescheduleItem(id)
    }

    /** Data odczytana automatycznie staje się potwierdzona dopiero po akcji użytkownika. */
    suspend fun confirmExpiryDate(id: Long) {
        val current = itemDao.getById(id) ?: return
        if (current.expiryEpochDay == null) return
        itemDao.upsert(
            current.copy(
                expiryConfirmed = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        reminderScheduler.rescheduleItem(id)
    }

    // --- Lokalny słownik kodów (specyfikacja, funkcja 4) ---------------------------------------

    private suspend fun rememberBarcodeName(item: PantryItem) {
        val barcode = item.barcode?.takeIf { it.isNotBlank() } ?: return
        val name = item.name.trim().takeIf { it.isNotBlank() } ?: return
        barcodeDao.remember(
            barcode = barcode,
            name = name,
            unit = item.unit,
            section = item.section.name,
            now = System.currentTimeMillis(),
        )
    }

    suspend fun lookupBarcodeName(barcode: String): BarcodeName? = barcodeDao.get(barcode)?.toDomain()

    suspend fun forgetBarcodeName(barcode: String) = barcodeDao.delete(barcode)

    suspend fun upsertBarcodeName(entry: BarcodeName) {
        barcodeDao.upsert(
            BarcodeNameEntity(
                barcode = entry.barcode,
                name = entry.name,
                unit = entry.unit,
                section = entry.section.name,
                updatedAt = System.currentTimeMillis(),
                useCount = entry.useCount,
            ),
        )
    }

    // --- Lista zakupowa ------------------------------------------------------------------------

    suspend fun addShoppingItem(item: ShoppingItem): Long {
        val now = System.currentTimeMillis()
        return shoppingDao.insert(
            item.copy(createdAt = if (item.createdAt == 0L) now else item.createdAt).toEntity(),
        )
    }

    /** „Kup ponownie” z pozycji spisu. */
    suspend fun addShoppingItemFrom(itemId: Long): Long {
        val source = itemDao.getById(itemId)?.toDomain() ?: return -1L
        return addShoppingItem(
            ShoppingItem(
                name = source.name,
                quantity = source.quantity,
                unit = source.unit,
                sectionHint = source.section,
                sourceItemId = source.id,
            ),
        )
    }

    suspend fun updateShoppingItem(item: ShoppingItem) =
        shoppingDao.upsert(item.toEntity())

    suspend fun setShoppingDone(id: Long, done: Boolean) = shoppingDao.setDone(id, done)

    suspend fun deleteShoppingItem(id: Long) = shoppingDao.deleteById(id)

    suspend fun clearDoneShoppingItems() = shoppingDao.deleteDone()

    // --- Zdjęcia -------------------------------------------------------------------------------

    suspend fun importPhoto(uri: Uri): String? = photoStorage.import(uri)

    fun photoFile(fileName: String?): File? = photoStorage.fileFor(fileName)

    // --- Pełne usunięcie danych użytkownika ----------------------------------------------------

    /**
     * Usuwa WSZYSTKO: pozycje, partie, słownik kodów, listę zakupową, zdjęcia,
     * historię przypomnień, ustawienia oraz zaplanowane alarmy i powiadomienia.
     */
    suspend fun eraseAllData() {
        reminderScheduler.cancelAll()
        // Wszystkie tabele kasujemy w jednej transakcji, żeby nie zostawić połowy danych.
        database.withTransaction {
            itemDao.deleteAll()
            barcodeDao.deleteAll()
            shoppingDao.deleteAll()
            reminderLogDao.deleteAll()
            database.scheduledAlarmDao().deleteAll()
        }
        photoStorage.deleteAll()
        settings.clear()
    }

    suspend fun counts(): Triple<Int, Int, Int> =
        Triple(itemDao.count(), barcodeDao.count(), shoppingDao.count())
}
