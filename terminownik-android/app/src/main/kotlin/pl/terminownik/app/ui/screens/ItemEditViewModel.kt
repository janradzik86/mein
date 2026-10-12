package pl.terminownik.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import pl.terminownik.app.AppContainer
import pl.terminownik.app.data.PantryRepository
import pl.terminownik.app.data.photo.PhotoStorage
import pl.terminownik.app.domain.BarcodeValidator
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.DayPrecision
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.PantryItem
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.Section
import pl.terminownik.app.notifications.ReminderScheduler
import pl.terminownik.app.scan.BarcodeInterpretation

data class EditErrors(
    val nameEmpty: Boolean = false,
    val nameTooLong: Boolean = false,
    val quantityInvalid: Boolean = false,
    val barcodeInvalid: String? = null,
)

data class EditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val section: Section = Section.PANTRY,
    val name: String = "",
    val quantityText: String = "",
    val unit: String = "",
    val storageLocation: String = "",
    val batchCode: String = "",
    val note: String = "",
    val barcode: String = "",
    val barcodeFormat: String = "",
    val expiry: ExpiryDate? = null,
    val reminderEnabled: Boolean = true,
    val photoFile: File? = null,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val errors: EditErrors = EditErrors(),
    val saved: Boolean = false,
    /** Nazwa i jednostka z lokalnego słownika kod → ostatnio wpisana nazwa. */
    val knownName: String? = null,
    val knownUnit: String? = null,
    /** Kod komunikatu ze skanera (tłumaczony w UI). */
    val scanMessage: String? = null,
    /** Kod problemu z OCR (tłumaczony w UI). */
    val ocrIssue: String? = null,
    val itemMissing: Boolean = false,
    val dictionaryForgotten: Boolean = false,
    val photoImportFailed: Boolean = false,
    val leadDays: Int = 14,
)

/**
 * Formularz pozycji (dodawanie i edycja).
 *
 * Pilnowane zasady domenowe:
 *  - kod EAN/UPC nigdy nie uzupełnia terminu ważności,
 *  - data z GS1 i z OCR jest zapisywana jako NIEpotwierdzona,
 *  - kod kreskowy jest walidowany (suma kontrolna) z czytelnym komunikatem,
 *  - każdy zapis przelicza przypomnienia tej pozycji,
 *  - partie nie są łączone: „uzupełnij” tworzy nowy wiersz (patrz [PantryRepository.restock]).
 */
class ItemEditViewModel(
    private val repository: PantryRepository,
    private val photoStorage: PhotoStorage,
    private val reminderScheduler: ReminderScheduler,
    private val section: Section,
    private val itemId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState(section = section))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private var loadedItem: PantryItem? = null

    init {
        viewModelScope.launch {
            val settings = repository.settingsOnce()
            if (itemId > 0) {
                val existing = repository.getItem(itemId)
                loadedItem = existing
                _state.value = if (existing == null) {
                    EditUiState(
                        loading = false,
                        isNew = true,
                        section = section,
                        itemMissing = true,
                        leadDays = settings.reminderLeadDays,
                    )
                } else {
                    EditUiState(
                        loading = false,
                        isNew = false,
                        section = existing.section,
                        name = existing.name,
                        quantityText = existing.quantity?.let { PlFormat.formatQuantity(it) } ?: "",
                        unit = existing.unit ?: "",
                        storageLocation = existing.storageLocation ?: "",
                        batchCode = existing.batchCode ?: "",
                        note = existing.note ?: "",
                        barcode = existing.barcode ?: "",
                        barcodeFormat = existing.barcodeFormat ?: "",
                        expiry = existing.expiryDate,
                        reminderEnabled = existing.reminderEnabled,
                        photoFile = photoStorage.fileFor(existing.photoPath),
                        status = existing.status,
                        leadDays = settings.reminderLeadDays,
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    loading = false,
                    isNew = true,
                    section = section,
                    reminderEnabled = settings.reminderEnabledByDefault,
                    storageLocation = settings.defaultStorageLocation ?: "",
                    leadDays = settings.reminderLeadDays,
                )
            }
        }
    }

    /** Wynik skanu: nazwa tylko jako podpowiedź, termin wyłącznie z GS1 i do potwierdzenia. */
    fun applyScan(interpretation: BarcodeInterpretation) {
        val proposedDay = interpretation.proposedExpiryEpochDay
        _state.value = _state.value.copy(
            barcode = interpretation.barcode,
            barcodeFormat = interpretation.formatName,
            name = _state.value.name.ifBlank { interpretation.knownName ?: "" },
            unit = _state.value.unit.ifBlank { interpretation.knownUnit ?: "" },
            expiry = if (proposedDay != null) {
                ExpiryDate(
                    epochDay = proposedDay,
                    dayPrecision = interpretation.proposedPrecision ?: DayPrecision.EXACT,
                    type = _state.value.expiry?.type ?: DateType.UNSPECIFIED,
                    source = DateSource.GS1_DATAMATRIX,
                    confirmed = false,
                )
            } else {
                _state.value.expiry
            },
            knownName = interpretation.knownName,
            knownUnit = interpretation.knownUnit,
            scanMessage = interpretation.messageCode,
            errors = _state.value.errors.copy(barcodeInvalid = null),
        )
    }

    /** Data z OCR: zawsze niepotwierdzona, z kodem problemu do pokazania użytkownikowi. */
    fun applyOcr(expiry: ExpiryDate, issueCode: String?) {
        _state.value = _state.value.copy(
            expiry = expiry.copy(confirmed = false, source = DateSource.OCR),
            ocrIssue = issueCode,
        )
    }

    fun onNameChange(value: String) = update { it.copy(name = value, errors = it.errors.copy(nameEmpty = false)) }

    fun onQuantityChange(value: String) = update {
        it.copy(quantityText = value.replace(',', '.'), errors = it.errors.copy(quantityInvalid = false))
    }

    fun onUnitChange(value: String) = update { it.copy(unit = value) }
    fun onLocationChange(value: String) = update { it.copy(storageLocation = value) }
    fun onBatchChange(value: String) = update { it.copy(batchCode = value) }
    fun onNoteChange(value: String) = update { it.copy(note = value) }

    fun onBarcodeChange(value: String) = update {
        it.copy(
            barcode = value.filter { ch -> ch.isDigit() },
            errors = it.errors.copy(barcodeInvalid = null),
        )
    }

    fun onReminderChange(enabled: Boolean) = update { it.copy(reminderEnabled = enabled) }

    fun onSectionChange(value: Section) = update { it.copy(section = value) }

    fun onDateTypeChange(value: DateType) = update { current ->
        val expiry = current.expiry ?: return@update current
        current.copy(expiry = expiry.copy(type = value))
    }

    fun setExpiry(expiry: ExpiryDate) = update { it.copy(expiry = expiry, ocrIssue = null) }

    fun clearExpiry() = update { it.copy(expiry = null, ocrIssue = null) }

    /** Potwierdzenie daty odczytanej automatycznie – dopiero wtedy może powstać przypomnienie. */
    fun confirmExpiry() = update { current ->
        val expiry = current.expiry ?: return@update current
        current.copy(expiry = expiry.copy(confirmed = true), ocrIssue = null)
    }

    /** Podpowiedź ze słownika: kod → ostatnio wpisana nazwa (użytkownik może ją zmienić). */
    fun applyKnownName() = update { current ->
        val known = current.knownName ?: return@update current
        current.copy(name = known, unit = current.unit.ifBlank { current.knownUnit ?: "" })
    }

    /** Zdjęcie z systemowego selektora – bez uprawnień do całej pamięci urządzenia. */
    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            val fileName = withContext(Dispatchers.IO) { photoStorage.import(uri) }
            update {
                it.copy(
                    photoFile = fileName?.let { name -> photoStorage.fileFor(name) },
                    photoImportFailed = fileName == null,
                )
            }
        }
    }

    fun removePhoto() {
        val file = _state.value.photoFile ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { photoStorage.delete(file.name) }
            update { it.copy(photoFile = null) }
        }
    }

    fun consumePhotoError() = update { it.copy(photoImportFailed = false) }

    fun dismissScanMessage() = update { it.copy(scanMessage = null) }

    /** Usunięcie powiązania kodu z nazwą z lokalnego słownika. */
    fun forgetKnownName() {
        val barcode = _state.value.barcode.trim().ifBlank { return }
        viewModelScope.launch {
            repository.forgetBarcodeName(barcode)
            update { it.copy(knownName = null, knownUnit = null, dictionaryForgotten = true) }
        }
    }

    fun save() {
        val current = _state.value
        val name = current.name.trim()
        val quantity = if (current.quantityText.isBlank()) null else current.quantityText.toDoubleOrNull()
        val barcode = current.barcode.trim().ifBlank { null }
        val barcodeError = barcodeErrorKey(barcode)

        val errors = EditErrors(
            nameEmpty = name.isBlank(),
            nameTooLong = name.length > MAX_NAME_LENGTH,
            quantityInvalid = current.quantityText.isNotBlank() && quantity == null,
            barcodeInvalid = barcodeError,
        )
        if (errors.nameEmpty || errors.nameTooLong || errors.quantityInvalid || errors.barcodeInvalid != null) {
            _state.value = current.copy(errors = errors)
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val item = PantryItem(
                id = loadedItem?.id ?: 0L,
                section = current.section,
                name = name,
                quantity = quantity,
                unit = current.unit.trim().ifBlank { null },
                storageLocation = current.storageLocation.trim().ifBlank { null },
                photoPath = current.photoFile?.name ?: loadedItem?.photoPath,
                batchCode = current.batchCode.trim().ifBlank { null },
                barcode = barcode,
                barcodeFormat = current.barcodeFormat.ifBlank { loadedItem?.barcodeFormat },
                expiryDate = current.expiry,
                note = current.note.trim().ifBlank { null },
                status = current.status,
                reminderEnabled = current.reminderEnabled,
                createdAt = loadedItem?.createdAt ?: now,
                updatedAt = now,
                statusChangedAt = loadedItem?.statusChangedAt,
                backupUid = loadedItem?.backupUid,
            )
            val savedId = repository.addOrUpdate(item)
            // Edycja pozycji musi zaktualizować jej przypomnienia (bez duplikatów).
            reminderScheduler.rescheduleItem(savedId)
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun delete() {
        val id = loadedItem?.id ?: return
        viewModelScope.launch {
            reminderScheduler.cancelItem(id)
            repository.deleteItem(id)
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun markUsed() = setStatus(ItemStatus.USED)
    fun markDiscarded() = setStatus(ItemStatus.DISCARDED)
    fun markActive() = setStatus(ItemStatus.ACTIVE)

    private fun setStatus(status: ItemStatus) {
        val id = loadedItem?.id ?: return
        viewModelScope.launch {
            when (status) {
                ItemStatus.USED -> repository.markUsed(id)
                ItemStatus.DISCARDED -> repository.markDiscarded(id)
                ItemStatus.ACTIVE -> repository.markActive(id)
            }
            reminderScheduler.rescheduleItem(id)
            _state.value = _state.value.copy(status = status, saved = true)
        }
    }

    fun consumeSaved() {
        _state.value = _state.value.copy(saved = false)
    }

    private fun update(block: (EditUiState) -> EditUiState) {
        _state.value = block(_state.value)
    }

    private companion object {
        /** Limit długości nazwy – taki sam jak w sanityzatorze kopii zapasowej. */
        const val MAX_NAME_LENGTH = 200
    }

    /**
     * Walidacja kodu: tylko cyfry, długość 8–14 i poprawna suma kontrolna GTIN (mod-10).
     * Zwraca klucz komunikatu z zasobów albo `null`, gdy kod jest poprawny.
     */
    private fun barcodeErrorKey(barcode: String?): String? {
        if (barcode == null) return null
        val digits = BarcodeValidator.normalize(barcode) ?: return "error_barcode_invalid_chars"
        if (digits.length !in 8..14) return "error_barcode_length"
        if (!BarcodeValidator.isCheckDigitValid(digits)) return "error_barcode_checksum"
        return null
    }
}

fun itemEditViewModelFactory(container: AppContainer, section: Section, itemId: Long) = viewModelFactory {
    initializer {
        ItemEditViewModel(
            container.pantryRepository,
            container.photoStorage,
            container.reminderScheduler,
            section,
            itemId,
        )
    }
}
