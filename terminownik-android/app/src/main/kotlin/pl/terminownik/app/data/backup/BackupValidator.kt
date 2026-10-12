package pl.terminownik.app.data.backup

import java.security.MessageDigest

/**
 * Walidacja niezaufanego pliku kopii zapasowej.
 *
 * Specyfikacja wymaga: „Importowane pliki traktuj jako niezaufane: limity rozmiaru,
 * walidacja zawartości, brak niebezpiecznych ścieżek w ZIP/XML”.
 *
 * Klasa jest czysto JVM-owa i w pełni testowalna.
 */
object BackupValidator {

    /**
     * Sprawdza, czy ścieżka wpisu archiwum jest bezpieczna (ochrona przed zip-slip).
     *
     * Odrzucamy: ścieżki absolutne, wyjścia poza katalog nadrzędny („..”), separatory Windows,
     * litery dysku, bajt null, puste nazwy, nazwy zbyt długie oraz wpisy spoza białej listy.
     */
    fun isSafeEntryPath(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.length > BackupContract.MAX_PATH_LENGTH) return false
        if (name.contains('\u0000')) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.contains('\\')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return false

        val segments = name.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
        if (segments.size > 2) return false

        return if (segments.size == 1) {
            name in BackupContract.JSON_PATHS
        } else {
            segments[0] == BackupContract.PHOTOS_DIR &&
                segments[1].length <= 80 &&
                segments[1].none { it == '/' }
        }
    }

    /** Waliduje listę wpisów archiwum pod kątem ścieżek, liczby i rozmiarów. */
    fun validateArchiveStructure(entries: List<ArchiveEntry>): BackupValidation? {
        if (entries.size > BackupContract.MAX_ENTRIES) {
            return BackupValidation.Invalid(
                BackupContract.Error.TOO_MANY_ENTRIES,
                "Archiwum zawiera ${entries.size} wpisów (limit ${BackupContract.MAX_ENTRIES}).",
            )
        }
        var totalUncompressed = 0L
        for (entry in entries) {
            if (entry.isDirectory) continue
            if (!isSafeEntryPath(entry.name)) {
                return BackupValidation.Invalid(
                    BackupContract.Error.UNSAFE_PATH,
                    "Niebezpieczna lub nieoczekiwana ścieżka w archiwum: ${entry.name.take(120)}",
                )
            }
            val limit = if (entry.name == BackupContract.MANIFEST_PATH || entry.name.endsWith(".json")) {
                BackupContract.MAX_JSON_ENTRY_BYTES
            } else {
                BackupContract.MAX_PHOTO_ENTRY_BYTES
            }
            if (entry.uncompressedSize > limit) {
                return BackupValidation.Invalid(
                    BackupContract.Error.ENTRY_TOO_LARGE,
                    "Wpis ${entry.name} ma ${entry.uncompressedSize} B (limit $limit B).",
                )
            }
            if (entry.compressedSize > 0 &&
                entry.uncompressedSize.toDouble() / entry.compressedSize > BackupContract.MAX_COMPRESSION_RATIO &&
                entry.uncompressedSize > 1024 * 1024
            ) {
                return BackupValidation.Invalid(
                    BackupContract.Error.COMPRESSION_BOMB,
                    "Podejrzany współczynnik kompresji wpisu ${entry.name}.",
                )
            }
            totalUncompressed += entry.uncompressedSize
            if (totalUncompressed > BackupContract.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                return BackupValidation.Invalid(
                    BackupContract.Error.ARCHIVE_TOO_LARGE,
                    "Archiwum po rozpakowaniu przekracza ${BackupContract.MAX_TOTAL_UNCOMPRESSED_BYTES} B.",
                )
            }
        }
        return null
    }

    /**
     * Waliduje manifest.
     *
     * Zasada: kopia z NOWSZEGO formatu jest odrzucana (nie zgadujemy, jak ją odczytać),
     * kopia ze starszego, wciąż wspieranego formatu jest przyjmowana z ostrzeżeniem.
     */
    fun validateManifest(
        manifest: BackupManifestData,
        supportedVersions: IntRange =
            BackupContract.MIN_SUPPORTED_FORMAT_VERSION..BackupContract.FORMAT_VERSION,
    ): BackupValidation {
        if (manifest.formatVersion > supportedVersions.last) {
            return BackupValidation.Invalid(
                BackupContract.Error.FORMAT_TOO_NEW,
                "Kopia w formacie ${manifest.formatVersion}; ta wersja aplikacji obsługuje do ${supportedVersions.last}.",
            )
        }
        if (manifest.formatVersion < supportedVersions.first) {
            return BackupValidation.Invalid(
                BackupContract.Error.FORMAT_UNSUPPORTED,
                "Format ${manifest.formatVersion} nie jest już obsługiwany.",
            )
        }
        if (manifest.app.isNotBlank() && manifest.app != BackupContract.APP_ID && manifest.app != "Terminownik") {
            return BackupValidation.Invalid(
                BackupContract.Error.WRONG_APP,
                "Archiwum pochodzi z innej aplikacji (${manifest.app}).",
            )
        }
        val warnings = mutableListOf<String>()
        if (manifest.formatVersion < supportedVersions.last) {
            warnings += "FORMAT_OLDER"
        }
        val itemCount = manifest.counts["items"] ?: 0
        if (itemCount > BackupContract.MAX_ITEMS) {
            return BackupValidation.Invalid(
                BackupContract.Error.TOO_MANY_ITEMS,
                "Kopia deklaruje $itemCount pozycji (limit ${BackupContract.MAX_ITEMS}).",
            )
        }
        if (itemCount == 0 && (manifest.counts["shopping"] ?: 0) == 0) {
            warnings += "EMPTY_BACKUP"
        }
        if (manifest.createdAtEpochMillis <= 0) {
            warnings += "MISSING_TIMESTAMP"
        }
        val declared = manifest.entries.map { it.path }.toSet()
        if (BackupContract.MANIFEST_PATH !in declared) {
            warnings += "MANIFEST_NOT_SELF_LISTED"
        }
        return BackupValidation.Ok(manifest, warnings)
    }

    /** Sprawdza sumy kontrolne SHA-256 wpisów (hex, case-insensitive). */
    fun verifyChecksum(expectedSha256: String, actualBytes: ByteArray): Boolean {
        val actual = sha256Hex(actualBytes)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Sprawdza, czy nazwa pliku zdjęcia jest dopuszczalna.
     * Zdjęcia w archiwum nazywamy `<backupUid>.jpg` – tylko małe litery, cyfry i myślnik.
     */
    fun isSafePhotoFileName(fileName: String): Boolean {
        if (fileName.isBlank() || fileName.length > 80) return false
        if (!fileName.endsWith(".jpg")) return false
        val base = fileName.removeSuffix(".jpg")
        return base.isNotEmpty() && base.all { it.isLowerCase() || it.isDigit() || it == '-' }
    }

    /**
     * Walidacja pojedynczej daty z kopii: epochDay musi mieścić się w rozsądnym zakresie,
     * żeby uszkodzona kopia nie utworzyła przypomnienia np. w roku 1.
     */
    fun isPlausibleEpochDay(epochDay: Long, todayEpochDay: Long): Boolean {
        val min = todayEpochDay - 365L * 20
        val max = todayEpochDay + 365L * 60
        return epochDay in min..max
    }

    /**
     * Walidacja tekstu z kopii (nazwa, notatka): usuwa znaki sterujące i tnie do limitu.
     * Zwraca null dla wartości, które są wyłącznie białymi znakami.
     */
    fun sanitizeText(value: String?, maxLength: Int = 500): String? {
        if (value == null) return null
        val cleaned = value.replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "").trim()
        if (cleaned.isEmpty()) return null
        return if (cleaned.length > maxLength) cleaned.take(maxLength) else cleaned
    }

    /** Walidacja liczby: odrzuca NaN/nieskończoności i wartości ujemne dla ilości. */
    fun sanitizeQuantity(value: Double?, max: Double = 1_000_000.0): Double? {
        if (value == null) return null
        if (value.isNaN() || value.isInfinite()) return null
        if (value < 0.0) return null
        if (value > max) return null
        return value
    }
}
