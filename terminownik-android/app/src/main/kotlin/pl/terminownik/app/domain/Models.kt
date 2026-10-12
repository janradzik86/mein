package pl.terminownik.app.domain

/**
 * Sekcje aplikacji. Specyfikacja wymaga osobnych sekcji Spiżarnia i Apteczka.
 */
enum class Section {
    PANTRY,
    MEDICINE,
    ;

    companion object {
        fun fromRaw(raw: String?): Section =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PANTRY
    }
}

/**
 * Rodzaj daty na opakowaniu. Rozróżnienie jest wymagane przez specyfikację:
 * „należy spożyć do” (termin bezpieczeństwa) i „najlepiej spożyć przed” (data minimalnej trwałości).
 */
enum class DateType {
    /** „należy spożyć do” / „use by” – termin przydatności do spożycia. */
    USE_BY,

    /** „najlepiej spożyć przed” / „best before” – data minimalnej trwałości. */
    BEST_BEFORE,

    /** Użytkownik nie wskazał rodzaju daty. */
    UNSPECIFIED,
    ;

    companion object {
        fun fromRaw(raw: String?): DateType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNSPECIFIED
    }
}

/** Skąd pochodzi data. Każde źródło automatyczne wymaga potwierdzenia przez użytkownika. */
enum class DateSource {
    MANUAL,
    GS1_DATAMATRIX,
    OCR,
    BACKUP_IMPORT,
    ;

    val isAutomatic: Boolean
        get() = this == GS1_DATAMATRIX || this == OCR

    companion object {
        fun fromRaw(raw: String?): DateSource =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

/** Precyzja odczytanej daty – potrzebna do oznaczania odczytów niejednoznacznych. */
enum class DayPrecision {
    /** Dokładny dzień znany i pewny. */
    EXACT,

    /**
     * Znamy tylko miesiąc (np. OCR „11.2026” albo GS1 AI 17 z DD=00).
     * Przechowujemy ostatni dzień miesiąca, ale wymagamy potwierdzenia.
     */
    MONTH_ONLY,

    /** OCR zwrócił dwuznaczny zapis (np. 03.04.2027 – nie wiadomo, który człon jest dniem). */
    AMBIGUOUS_ORDER,
}

/** Cykl życia pozycji w spisie. */
enum class ItemStatus {
    ACTIVE,
    USED,
    DISCARDED,
    ;

    companion object {
        fun fromRaw(raw: String?): ItemStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

/**
 * Data ważności wraz z pełnym kontekstem wymaganym przez specyfikację.
 *
 * @param epochDay liczba dni od 1970-01-01 (data kalendarzowa, niezależna od godziny),
 * @param confirmed `false` dopóki użytkownik nie potwierdzi odczytu automatycznego.
 */
data class ExpiryDate(
    val epochDay: Long,
    val dayPrecision: DayPrecision = DayPrecision.EXACT,
    val type: DateType = DateType.UNSPECIFIED,
    val source: DateSource = DateSource.MANUAL,
    val confirmed: Boolean = true,
) {
    val requiresConfirmation: Boolean
        get() = !confirmed || dayPrecision != DayPrecision.EXACT
}

/**
 * Pozycja spisu. Jeden wiersz = jedna partia.
 *
 * Specyfikacja: „ten sam produkt może mieć kilka partii z różnymi datami” oraz
 * „nie łącz takich partii automatycznie” – dlatego NIE ma tu żadnego klucza unikalności
 * po kodzie kreskowym ani po nazwie.
 */
data class PantryItem(
    val id: Long = 0L,
    val section: Section = Section.PANTRY,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val storageLocation: String? = null,
    val photoPath: String? = null,
    val batchCode: String? = null,
    val barcode: String? = null,
    val barcodeFormat: String? = null,
    val expiryDate: ExpiryDate? = null,
    val note: String? = null,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val reminderEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val statusChangedAt: Long? = null,
    /** Identyfikator z kopii zapasowej – pozwala odtwarzać kopię bez duplikatów. */
    val backupUid: String? = null,
)

/** Wpis lokalnego słownika: kod produktu → ostatnia nazwa wpisana przez użytkownika. */
data class BarcodeName(
    val barcode: String,
    val name: String,
    val unit: String? = null,
    val section: Section = Section.PANTRY,
    val updatedAt: Long = 0L,
    val useCount: Int = 1,
)

/** Element listy zakupowej. */
data class ShoppingItem(
    val id: Long = 0L,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val sectionHint: Section? = null,
    val done: Boolean = false,
    val note: String? = null,
    val createdAt: Long = 0L,
    /** Opcjonalne powiązanie z pozycją, z której powstał („kup ponownie”). */
    val sourceItemId: Long? = null,
)

/** Ustawienia użytkownika trzymane w DataStore. */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = true,
    /** Wyprzedzenie przypomnienia w dniach (konfigurowalne – specyfikacja, funkcja 10). */
    val reminderLeadDays: Int = 7,
    /** Godzina dnia (0–23), o której wysyłamy przypomnienie. */
    val reminderHourOfDay: Int = 9,
    val reminderMinute: Int = 0,
    /** Czy przypominać też w dniu terminu. */
    val remindOnDueDay: Boolean = true,
    val showArchived: Boolean = false,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
