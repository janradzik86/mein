package pl.terminownik.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.terminownik.app.data.PantryRepository
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.ExpiryBucket
import pl.terminownik.app.domain.ExpiryFilter
import pl.terminownik.app.domain.ExpiryRules
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.ItemWithExpiry
import pl.terminownik.app.domain.Section
import pl.terminownik.app.notifications.ReminderScheduler

enum class SortBy { DATE, NAME, ADDED }

data class ItemListUiState(
    val loading: Boolean = true,
    val visible: List<ItemWithExpiry> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val filter: ExpiryFilter = ExpiryFilter.ALL,
    val location: String? = null,
    val locations: List<String> = emptyList(),
    val showArchived: Boolean = false,
    val sortBy: SortBy = SortBy.DATE,
    val expiringSoon: Int = 0,
    val expired: Int = 0,
    val unconfirmed: Int = 0,
    val noDate: Int = 0,
    val settings: AppSettings = AppSettings(),
    val sectionTitle: Section? = null,
)

/**
 * Logika prezentacji listy pozycji.
 *
 * @param section `null` oznacza widok wspólny (ekran „Terminy”).
 */
class ItemListViewModel(
    private val repository: PantryRepository,
    private val reminderScheduler: ReminderScheduler,
    private val section: Section?,
) : ViewModel() {

    private data class Prefs(
        val query: String = "",
        val filter: ExpiryFilter = ExpiryFilter.ALL,
        val location: String? = null,
        val showArchived: Boolean = false,
        val sortBy: SortBy = SortBy.DATE,
    )

    private val prefs = MutableStateFlow(Prefs())

    val uiState: StateFlow<ItemListUiState> =
        combine(repository.itemsWithExpiry, prefs, settingsFlow()) { items, p, settings ->
            val scoped = if (section == null) items else items.filter { it.item.section == section }
            val archivedVisible = p.showArchived || settings.showArchived
            val active = scoped.filter { archivedVisible || it.item.status == ItemStatus.ACTIVE }

            val query = p.query.trim().lowercase()
            val filtered = active.filter { row ->
                val matchesQuery = query.isEmpty() ||
                    row.item.name.lowercase().contains(query) ||
                    (row.item.storageLocation?.lowercase()?.contains(query) ?: false) ||
                    (row.item.batchCode?.lowercase()?.contains(query) ?: false) ||
                    (row.item.note?.lowercase()?.contains(query) ?: false) ||
                    (row.item.barcode?.contains(query) ?: false)
                val matchesLocation = p.location == null || row.item.storageLocation == p.location
                matchesQuery && matchesLocation && ExpiryRules.matchesFilter(row.assessment, p.filter)
            }

            val sorted = when (p.sortBy) {
                SortBy.DATE -> filtered.sortedBy { ExpiryRules.sortKey(it.assessment) }
                SortBy.NAME -> filtered.sortedBy { it.item.name.lowercase() }
                SortBy.ADDED -> filtered.sortedByDescending { it.item.updatedAt }
            }

            ItemListUiState(
                loading = false,
                visible = sorted,
                totalCount = scoped.size,
                query = p.query,
                filter = p.filter,
                location = p.location,
                locations = scoped.mapNotNull { it.item.storageLocation }.distinct().sorted(),
                showArchived = archivedVisible,
                sortBy = p.sortBy,
                expiringSoon = scoped.count {
                    it.assessment.bucket == ExpiryBucket.EXPIRING_SOON ||
                        it.assessment.bucket == ExpiryBucket.DUE_TODAY
                },
                expired = scoped.count { it.assessment.bucket == ExpiryBucket.EXPIRED },
                unconfirmed = scoped.count { it.assessment.bucket == ExpiryBucket.UNCONFIRMED },
                noDate = scoped.count { it.assessment.bucket == ExpiryBucket.NO_DATE },
                settings = settings,
                sectionTitle = section,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemListUiState(sectionTitle = section))

    private fun settingsFlow() = repository.settingsFlow()

    fun setQuery(value: String) = prefs.update { it.copy(query = value) }
    fun setFilter(value: ExpiryFilter) = prefs.update { it.copy(filter = value) }
    fun setLocation(value: String?) = prefs.update { it.copy(location = value) }
    fun toggleArchived() = prefs.update { it.copy(showArchived = !it.showArchived) }
    fun setSort(value: SortBy) = prefs.update { it.copy(sortBy = value) }

    fun markUsed(id: Long) = viewModelScope.launch { repository.markUsed(id) }
    fun markDiscarded(id: Long) = viewModelScope.launch { repository.markDiscarded(id) }
    fun markActive(id: Long) = viewModelScope.launch { repository.markActive(id) }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteItem(id) }
    fun addToShopping(id: Long) = viewModelScope.launch { repository.addShoppingItemFrom(id) }
    fun confirmDate(id: Long) = viewModelScope.launch { repository.confirmExpiryDate(id) }

    fun restock(id: Long, quantity: Double?, expiry: ExpiryDate?, batch: String?) =
        viewModelScope.launch { repository.restock(id, quantity, expiry, batch) }

    /** Ręczne przeliczenie przypomnień (np. po zmianie godziny w ustawieniach). */
    fun rescheduleReminders() = viewModelScope.launch { reminderScheduler.rescheduleAll() }
}
