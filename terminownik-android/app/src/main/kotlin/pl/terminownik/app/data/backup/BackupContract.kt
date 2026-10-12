package pl.terminownik.app.data.backup

/**
 * Kontrakt kopii zapasowej: wersjonowany format ZIP + JSON.
 *
 * Struktura archiwum:
 * ```
 * manifest.json          – metadane, wersja formatu, sumy kontrolne, liczniki
 * items.json             – pozycje spisu (partie)
 * barcode_names.json     – lokalny słownik kod → nazwa
 * shopping.json          – lista zakupowa
 * settings.json          – ustawienia użytkownika
 * reminder_log.json      – historia wysłanych przypomnień (antyduplikat)
 * photos/<backupUid>.jpg – załączniki (zdjęcia pozycji)
 * ```
 *
 * Ta klasa nie zależy od Androida ani od kotlinx-serialization, dzięki czemu walidacja
 * kopii jest testowalna na JVM (patrz BackupValidatorTest / DomainScenarios).
 */
object BackupContract {

    const val APP_ID: String = "pl.terminownik.app"
    const val FORMAT_VERSION: Int = 1
    const val MIN_SUPPORTED_FORMAT_VERSION: Int = 1

    const val MANIFEST_PATH: String = "manifest.json"
    const val ITEMS_PATH: String = "items.json"
    const val BARCODE_NAMES_PATH: String = "barcode_names.json"
    const val SHOPPING_PATH: String = "shopping.json"
    const val SETTINGS_PATH: String = "settings.json"
    const val REMINDER_LOG_PATH: String = "reminder_log.json"
    const val PHOTOS_DIR: String = "photos"

    val JSON_PATHS: Set<String> = setOf(
        MANIFEST_PATH,
        ITEMS_PATH,
        BARCODE_NAMES_PATH,
        SHOPPING_PATH,
        SETTINGS_PATH,
        REMINDER_LOG_PATH,
    )

    // --- Limity bezpieczeństwa (plik importowany traktujemy jako niezaufany) ---
    /** Maksymalny rozmiar archiwum po rozpakowaniu. */
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 400L * 1024 * 1024

    /** Maksymalny rozmiar pojedynczego pliku JSON w archiwum. */
    const val MAX_JSON_ENTRY_BYTES: Long = 20L * 1024 * 1024

    /** Maksymalny rozmiar pojedynczego zdjęcia. */
    const val MAX_PHOTO_ENTRY_BYTES: Long = 15L * 1024 * 1024

    /** Maksymalna liczba wpisów w archiwum (ochrona przed zip-bomb i śmieciami). */
    const val MAX_ENTRIES: Int = 20_000

    /** Maksymalna liczba pozycji spisu w jednej kopii. */
    const val MAX_ITEMS: Int = 50_000

    /** Maksymalna długość ścieżki wpisu. */
    const val MAX_PATH_LENGTH: Int = 200

    /**
     * Maksymalny dopuszczalny współczynnik kompresji.
     * Klasyczna bomba ZIP ma współczynnik rzędu 1000:1 i więcej.
     */
    const val MAX_COMPRESSION_RATIO: Double = 200.0

    /** Nazwa pliku sugerowana przy eksporcie (bez daty – datę dokleja warstwa UI). */
    const val EXPORT_FILE_PREFIX: String = "terminownik-kopia"
    const val EXPORT_FILE_EXTENSION: String = "zip"

    /** Kody błędów walidacji – UI tłumaczy je na komunikaty z zasobów. */
    object Error {
        const val NOT_A_ZIP = "NOT_A_ZIP"
        const val MISSING_MANIFEST = "MISSING_MANIFEST"
        const val FORMAT_TOO_NEW = "FORMAT_TOO_NEW"
        const val FORMAT_UNSUPPORTED = "FORMAT_UNSUPPORTED"
        const val WRONG_APP = "WRONG_APP"
        const val CORRUPTED_JSON = "CORRUPTED_JSON"
        const val CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH"
        const val ENTRY_TOO_LARGE = "ENTRY_TOO_LARGE"
        const val ARCHIVE_TOO_LARGE = "ARCHIVE_TOO_LARGE"
        const val TOO_MANY_ENTRIES = "TOO_MANY_ENTRIES"
        const val TOO_MANY_ITEMS = "TOO_MANY_ITEMS"
        const val UNSAFE_PATH = "UNSAFE_PATH"
        const val UNEXPECTED_ENTRY = "UNEXPECTED_ENTRY"
        const val COMPRESSION_BOMB = "COMPRESSION_BOMB"
        const val IO_ERROR = "IO_ERROR"
    }
}

/** Wpis manifestu opisujący pojedynczy plik w archiwum. */
data class BackupEntryInfo(
    val path: String,
    val sha256: String,
    val bytes: Long,
)

/** Dane manifestu używane przez walidator (bez adnotacji serializacyjnych). */
data class BackupManifestData(
    val formatVersion: Int,
    val app: String,
    val appVersionName: String,
    val createdAtEpochMillis: Long,
    val timezone: String?,
    val counts: Map<String, Int>,
    val entries: List<BackupEntryInfo>,
)

/** Wynik walidacji kopii. */
sealed class BackupValidation {
    data class Ok(
        val manifest: BackupManifestData,
        val warnings: List<String> = emptyList(),
    ) : BackupValidation()

    data class Invalid(
        val code: String,
        val details: String,
    ) : BackupValidation()
}

/** Opis wpisu ZIP przekazany do walidacji (bez zależności od java.util.zip w logice). */
data class ArchiveEntry(
    val name: String,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
)
