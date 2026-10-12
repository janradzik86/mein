package pl.terminownik.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import pl.terminownik.app.AppContainer
import pl.terminownik.app.R
import pl.terminownik.app.data.backup.BackupContract
import pl.terminownik.app.data.backup.BackupManifestData
import pl.terminownik.app.data.backup.BackupResult
import pl.terminownik.app.data.backup.ExportSummary
import pl.terminownik.app.data.backup.RestoreMode
import pl.terminownik.app.data.backup.RestoreSummary
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.CsvExport
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.ReminderKind
import pl.terminownik.app.domain.ReminderSlot
import pl.terminownik.app.domain.Section
import pl.terminownik.app.domain.ThemeMode
import pl.terminownik.app.notifications.ExactAlarmState
import pl.terminownik.app.notifications.NotificationChannels
import pl.terminownik.app.notifications.PermissionState

/** Komunikaty ustawień – tekst dobiera warstwa UI z zasobów. */
sealed interface SettingsMessage {
    data class BackupExported(val summary: ExportSummary) : SettingsMessage
    data class BackupRestored(val summary: RestoreSummary) : SettingsMessage
    data class BackupFailed(val code: String) : SettingsMessage
    data class CsvExported(val rows: Int) : SettingsMessage
    data class CsvEmpty(val code: String) : SettingsMessage
    data object DataErased : SettingsMessage
    data object TestNotificationSent : SettingsMessage
}

/** Statystyki pokazywane w ustawieniach. */
data class SettingsStats(
    val items: Int = 0,
    val barcodeNames: Int = 0,
    val shoppingPending: Int = 0,
    val photoCount: Int = 0,
    val photoBytes: Long = 0L,
    val databaseBytes: Long = 0L,
)

/** Podgląd kopii przed importem: zapamiętujemy też URI, żeby odtworzyć bez ponownego wyboru. */
data class BackupInspectData(
    val uri: Uri,
    val manifest: BackupManifestData,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val stats: SettingsStats? = null,
    val busy: Boolean = false,
    val busyCode: String? = null,
    val notificationsGranted: Boolean = false,
    val exactAlarmState: ExactAlarmState = ExactAlarmState.NOT_APPLICABLE,
    val inspect: BackupInspectData? = null,
    val message: SettingsMessage? = null,
)

/** Ustawienia, kopia zapasowa, eksport CSV i kasowanie danych. */
class SettingsViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _state.value = _state.value.copy(settings = settings)
            }
        }
        refreshPermissions()
        refreshStats()
    }

    fun refreshPermissions() {
        _state.value = _state.value.copy(
            notificationsGranted = PermissionState.notificationsGranted(context),
            exactAlarmState = PermissionState.exactAlarmState(context),
        )
    }

    fun refreshStats() {
        viewModelScope.launch {
            val (items, codes, shopping) = container.pantryRepository.counts()
            val pending = container.pantryRepository.shoppingList.first().count { !it.done }
            val (photoCount, photoBytes, databaseBytes) = withContext(Dispatchers.IO) {
                Triple(
                    container.photoStorage.count(),
                    container.photoStorage.totalBytes(),
                    context.getDatabasePath(DATABASE_NAME).takeIf { it.exists() }?.length() ?: 0L,
                )
            }
            _state.value = _state.value.copy(
                stats = SettingsStats(
                    items = items,
                    barcodeNames = codes,
                    shoppingPending = if (pending > 0) pending else shopping,
                    photoCount = photoCount,
                    photoBytes = photoBytes,
                    databaseBytes = databaseBytes,
                ),
            )
        }
    }

    // --- Ustawienia (każda zmiana wyprzedzenia/godziny przelicza przypomnienia) ---

    fun setTheme(mode: ThemeMode) = updateSettings { it.setThemeMode(mode) }

    fun setLeadDays(days: Int) = updateSettings { it.setReminderLeadDays(days) }

    fun setReminderTime(hour: Int, minute: Int) = updateSettings { it.setReminderTime(hour, minute) }

    fun setRemindersEnabled(enabled: Boolean) = updateSettings { it.setReminderEnabled(enabled) }

    fun setRemindOnDueDay(enabled: Boolean) = updateSettings { it.setRemindOnDueDay(enabled) }

    fun setShowArchived(show: Boolean) = updateSettings { it.setShowArchived(show) }

    fun repeatOnboarding() = updateSettings { it.setOnboardingDone(false) }

    private fun updateSettings(block: suspend (pl.terminownik.app.data.settings.SettingsRepository) -> Unit) {
        viewModelScope.launch {
            block(container.settingsRepository)
            container.reminderScheduler.rescheduleAll()
        }
    }

    /** Powiadomienie próbne: sprawdza cały tor (kanał, uprawnienie, treść, tapnięcie). */
    fun sendTestNotification() {
        viewModelScope.launch {
            NotificationChannels.create(context)
            val today = LocalDate.now(PlFormat.zone())
            val slot = ReminderSlot(
                key = "test|${System.currentTimeMillis()}",
                itemId = 0L,
                itemName = context.getString(R.string.notification_test_item),
                section = Section.PANTRY,
                expiryEpochDay = today.plusDays(3).toEpochDay(),
                fireEpochDay = today.toEpochDay(),
                kind = ReminderKind.LEAD,
                dateType = DateType.USE_BY,
            )
            container.reminderNotification.publish(slot, TEST_NOTIFICATION_ID)
            _state.value = _state.value.copy(message = SettingsMessage.TestNotificationSent)
        }
    }

    // --- Kopia zapasowa ---

    fun exportBackup(uri: Uri) {
        setBusy(R.string.backup_progress_export)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { container.backupRepository.export(uri) }
            setBusy(null)
            _state.value = _state.value.copy(
                message = when (result) {
                    is BackupResult.Success -> SettingsMessage.BackupExported(result.value)
                    is BackupResult.Failure -> SettingsMessage.BackupFailed(result.code)
                },
            )
            refreshStats()
        }
    }

    /** Podgląd zawartości kopii przed importem – na tym etapie nic nie jest zapisywane. */
    fun inspectBackup(uri: Uri) {
        setBusy(R.string.backup_progress_import)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { container.backupRepository.inspect(uri) }
            setBusy(null)
            when (result) {
                is BackupResult.Success ->
                    _state.value = _state.value.copy(inspect = BackupInspectData(uri, result.value))

                is BackupResult.Failure ->
                    _state.value = _state.value.copy(message = SettingsMessage.BackupFailed(result.code))
            }
        }
    }

    fun restoreBackup(mode: RestoreMode) {
        val inspect = _state.value.inspect ?: return
        _state.value = _state.value.copy(inspect = null)
        setBusy(R.string.backup_progress_import)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.backupRepository.restore(inspect.uri, mode)
            }
            setBusy(null)
            _state.value = _state.value.copy(
                message = when (result) {
                    is BackupResult.Success -> SettingsMessage.BackupRestored(result.value)
                    is BackupResult.Failure -> SettingsMessage.BackupFailed(result.code)
                },
            )
            refreshStats()
            // Po odtworzeniu przypomnienia muszą odpowiadać nowym danym.
            container.reminderScheduler.rescheduleAll()
            container.reminderScheduler.catchUp()
        }
    }

    fun dismissInspect() {
        _state.value = _state.value.copy(inspect = null)
    }

    // --- CSV ---

    fun exportCsv(uri: Uri, columns: CsvExport.ItemColumns, labels: CsvExport.Labels) {
        setBusy(null)
        viewModelScope.launch {
            val items = container.pantryRepository.allItems.first()
            if (items.isEmpty()) {
                _state.value = _state.value.copy(message = SettingsMessage.CsvEmpty("csv_export_empty"))
                return@launch
            }
            val csv = withContext(Dispatchers.Default) { CsvExport.exportItems(items, columns, labels) }
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("no-stream")
                }.isSuccess
            }
            _state.value = _state.value.copy(
                message = if (written) {
                    SettingsMessage.CsvExported(items.size)
                } else {
                    SettingsMessage.BackupFailed(BackupContract.Error.IO_ERROR)
                },
            )
        }
    }

    /** Kasowanie wszystkich danych użytkownika (wymóg: realna możliwość usunięcia danych). */
    fun eraseAllData() {
        setBusy(null)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.pantryRepository.eraseAllData() }
            container.reminderScheduler.cancelAll()
            container.reminderNotification.cancelAll()
            _state.value = _state.value.copy(message = SettingsMessage.DataErased)
            refreshStats()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun setBusy(labelRes: Int?) {
        _state.value = _state.value.copy(busy = labelRes != null, busyCode = labelRes?.toString())
    }

    /** Etykieta postępu (0 = brak). */
    fun busyLabelRes(): Int? = _state.value.busyCode?.toIntOrNull()

    companion object {
        const val TEST_NOTIFICATION_ID = 900002
        private const val DATABASE_NAME = "terminownik.db"
    }
}

fun settingsViewModelFactory(container: AppContainer, context: Context) = viewModelFactory {
    initializer { SettingsViewModel(container, context) }
}

/** Kody błędów kopii (z [BackupContract.Error]) na zasoby tekstowe. */
fun backupErrorText(code: String): Int = when (code) {
    BackupContract.Error.NOT_A_ZIP -> R.string.backup_error_not_a_zip
    BackupContract.Error.MISSING_MANIFEST -> R.string.backup_error_missing_manifest
    BackupContract.Error.FORMAT_TOO_NEW -> R.string.backup_error_format_too_new
    BackupContract.Error.FORMAT_UNSUPPORTED -> R.string.backup_error_format_unsupported
    BackupContract.Error.WRONG_APP -> R.string.backup_error_wrong_app
    BackupContract.Error.CORRUPTED_JSON -> R.string.backup_error_corrupted_json
    BackupContract.Error.CHECKSUM_MISMATCH -> R.string.backup_error_checksum
    BackupContract.Error.ENTRY_TOO_LARGE -> R.string.backup_error_entry_too_large
    BackupContract.Error.ARCHIVE_TOO_LARGE -> R.string.backup_error_archive_too_large
    BackupContract.Error.TOO_MANY_ENTRIES -> R.string.backup_error_too_many_entries
    BackupContract.Error.TOO_MANY_ITEMS -> R.string.backup_error_too_many_items
    BackupContract.Error.UNSAFE_PATH -> R.string.backup_error_unsafe_path
    BackupContract.Error.UNEXPECTED_ENTRY -> R.string.backup_error_unexpected_entry
    BackupContract.Error.COMPRESSION_BOMB -> R.string.backup_error_compression_bomb
    BackupContract.Error.IO_ERROR -> R.string.backup_error_io
    else -> R.string.error_io
}

/** Ostrzeżenia z odtwarzania (łańcuchy z walidatora) na zasoby tekstowe. */
fun backupWarningText(warning: String): Int = when {
    warning.startsWith("FORMAT_OLDER") -> R.string.backup_warning_format_older
    warning.startsWith("EMPTY_BACKUP") -> R.string.backup_warning_empty
    warning.startsWith("NO_TIMESTAMP") -> R.string.backup_warning_timestamp
    warning.startsWith("CORRUPTED_SECTION") -> R.string.backup_warning_corrupt_section
    warning.startsWith("DATE_OUT_OF_RANGE") -> R.string.backup_warning_date_range
    warning.startsWith("PHOTO_COPY_FAILED") -> R.string.backup_warning_photos
    warning.startsWith("MANIFEST") -> R.string.backup_warning_manifest
    else -> R.string.backup_warning_corrupt_section
}

/** Etykiety CSV budowane z zasobów (warstwa danych nie zna tekstów). */
fun csvLabels(context: Context) = CsvExport.Labels(
    pantry = context.getString(R.string.section_pantry),
    medicine = context.getString(R.string.section_medicine),
    useBy = context.getString(R.string.date_type_use_by),
    bestBefore = context.getString(R.string.date_type_best_before),
    unspecified = context.getString(R.string.date_type_unspecified),
    sourceManual = context.getString(R.string.source_manual),
    sourceGs1 = context.getString(R.string.source_gs1),
    sourceOcr = context.getString(R.string.source_ocr),
    sourceImport = context.getString(R.string.source_import),
    statusActive = context.getString(R.string.item_status_active),
    statusUsed = context.getString(R.string.item_status_used),
    statusDiscarded = context.getString(R.string.item_status_discarded),
    yes = context.getString(R.string.yes),
    no = context.getString(R.string.no),
)

/** Nagłówki kolumn CSV spisu. */
fun csvItemColumns(context: Context) = CsvExport.ItemColumns(
    section = context.getString(R.string.csv_headers_section),
    name = context.getString(R.string.csv_headers_name),
    quantity = context.getString(R.string.csv_headers_quantity),
    unit = context.getString(R.string.csv_headers_unit),
    location = context.getString(R.string.field_location),
    batch = context.getString(R.string.field_batch),
    barcode = context.getString(R.string.field_barcode),
    expiry = context.getString(R.string.field_expiry),
    dateType = context.getString(R.string.field_date_type),
    dateSource = context.getString(R.string.field_date_source),
    confirmed = context.getString(R.string.date_confirmed),
    status = context.getString(R.string.form_status),
    note = context.getString(R.string.csv_headers_note),
    createdAt = context.getString(R.string.csv_headers_created),
    updatedAt = context.getString(R.string.csv_headers_updated),
)
