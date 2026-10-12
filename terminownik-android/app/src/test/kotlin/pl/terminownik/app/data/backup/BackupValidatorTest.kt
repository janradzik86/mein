package pl.terminownik.app.data.backup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.terminownik.app.domain.PlFormat

/**
 * Testy walidacji niezaufanego pliku kopii.
 *
 * Brief: „Importowane pliki traktuj jako niezaufane – limity rozmiaru, walidacja zawartości,
 * brak niebezpiecznych ścieżek w ZIP/XML”. Te testy są czysto JVM-owe, więc działają w CI
 * bez emulatora i bez Android SDK.
 */
class BackupValidatorTest {

    private fun entry(name: String, size: Long = 1_000, compressed: Long = size) =
        ArchiveEntry(name = name, uncompressedSize = size, compressedSize = compressed, isDirectory = false)

    private fun manifest(
        version: Int = BackupContract.FORMAT_VERSION,
        app: String = BackupContract.APP_ID,
        items: Int = 3,
        createdAt: Long = System.currentTimeMillis(),
    ) = BackupManifestData(
        formatVersion = version,
        app = app,
        appVersionName = "1.0.0",
        createdAtEpochMillis = createdAt,
        timezone = "Europe/Warsaw",
        counts = mapOf("items" to items, "photos" to 1, "shopping" to 0, "barcodeNames" to 0),
        entries = listOf(BackupEntryInfo(BackupContract.MANIFEST_PATH, "aa", 10L)),
    )

    // --- ścieżki w archiwum (zip-slip) ---

    @Test
    fun `ścieżki dozwolone przechodzą`() {
        assertTrue(BackupValidator.isSafeEntryPath("manifest.json"))
        assertTrue(BackupValidator.isSafeEntryPath("items.json"))
        assertTrue(BackupValidator.isSafeEntryPath("photos/abc-123.jpg"))
    }

    @Test
    fun `ścieżki niebezpieczne są odrzucane`() {
        listOf(
            "../items.json",
            "/etc/passwd",
            "photos/../../secret.jpg",
            "..\\windows\\system32",
            "C:/plik.json",
            "photos/${'a'.repeat(200)}.jpg",
            "items.json\u0000.jpg",
            "",
            "nieznany/plik.txt",
        ).forEach { path ->
            assertFalse("Ścieżka powinna być odrzucona: $path", BackupValidator.isSafeEntryPath(path))
        }
    }

    @Test
    fun `struktura archiwum z niebezpieczną ścieżką jest odrzucana`() {
        val result = BackupValidator.validateArchiveStructure(
            listOf(entry("manifest.json"), entry("../items.json")),
        )
        assertTrue(result is BackupValidation.Invalid)
        assertEquals(BackupContract.Error.UNSAFE_PATH, (result as BackupValidation.Invalid).code)
    }

    @Test
    fun `zbyt duża liczba wpisów jest odrzucana`() {
        val entries = (0 until BackupContract.MAX_ENTRIES + 1).map { entry("photos/f$it.jpg") }
        val result = BackupValidator.validateArchiveStructure(entries)
        assertEquals(BackupContract.Error.TOO_MANY_ENTRIES, (result as BackupValidation.Invalid).code)
    }

    @Test
    fun `bomba ZIP jest odrzucana przez współczynnik kompresji`() {
        val bomb = entry("items.json", size = 5_000_000, compressed = 100)
        val result = BackupValidator.validateArchiveStructure(listOf(entry("manifest.json"), bomb))
        assertTrue(result is BackupValidation.Invalid)
    }

    @Test
    fun `poprawna struktura archiwum przechodzi`() {
        assertNull(
            BackupValidator.validateArchiveStructure(
                listOf(entry("manifest.json"), entry("items.json"), entry("photos/a.jpg")),
            ),
        )
    }

    // --- polityka manifestu ---

    @Test
    fun `manifest z nowszego formatu jest odrzucany`() {
        val result = BackupValidator.validateManifest(manifest(version = BackupContract.FORMAT_VERSION + 1))
        assertEquals(BackupContract.Error.FORMAT_TOO_NEW, (result as BackupValidation.Invalid).code)
    }

    @Test
    fun `manifest ze starszego wspieranego formatu jest przyjmowany z ostrzeżeniem`() {
        val supported = BackupContract.MIN_SUPPORTED_FORMAT_VERSION until BackupContract.FORMAT_VERSION
        if (supported.isEmpty()) return
        val result = BackupValidator.validateManifest(manifest(version = supported.first))
        assertTrue(result is BackupValidation.Ok)
        assertTrue((result as BackupValidation.Ok).warnings.any { it.startsWith("FORMAT_OLDER") })
    }

    @Test
    fun `manifest innej aplikacji jest odrzucany`() {
        val result = BackupValidator.validateManifest(manifest(app = "com.inna.aplikacja"))
        assertEquals(BackupContract.Error.WRONG_APP, (result as BackupValidation.Invalid).code)
    }

    @Test
    fun `pusta kopia jest przyjmowana, ale z ostrzeżeniem`() {
        val result = BackupValidator.validateManifest(manifest(items = 0))
        assertTrue(result is BackupValidation.Ok)
        assertTrue((result as BackupValidation.Ok).warnings.contains("EMPTY_BACKUP"))
    }

    @Test
    fun `zbyt wiele pozycji w kopii jest odrzucane`() {
        val result = BackupValidator.validateManifest(manifest(items = BackupContract.MAX_ITEMS + 1))
        assertEquals(BackupContract.Error.TOO_MANY_ITEMS, (result as BackupValidation.Invalid).code)
    }

    @Test
    fun `brak znacznika czasu daje ostrzeżenie`() {
        val result = BackupValidator.validateManifest(manifest(createdAt = 0))
        assertTrue((result as BackupValidation.Ok).warnings.contains("MISSING_TIMESTAMP"))
    }

    // --- sumy kontrolne i sanityzacja ---

    @Test
    fun `suma kontrolna SHA-256 jest weryfikowana`() {
        val bytes = "Terminownik".toByteArray()
        val hex = BackupValidator.sha256Hex(bytes)
        assertTrue(BackupValidator.verifyChecksum(hex, bytes))
        assertTrue("Porównanie musi być niezależne od wielkości liter", BackupValidator.verifyChecksum(hex.uppercase(), bytes))
        assertFalse(BackupValidator.verifyChecksum(hex, "inne dane".toByteArray()))
        assertFalse(BackupValidator.verifyChecksum("za-krótka", bytes))
    }

    @Test
    fun `nazwy plików zdjęć są ograniczone do bezpiecznego zestawu`() {
        assertTrue(BackupValidator.isSafePhotoFileName("abc-123.jpg"))
        assertFalse(BackupValidator.isSafePhotoFileName("../abc.jpg"))
        assertFalse(BackupValidator.isSafePhotoFileName("plik.exe"))
        assertFalse(BackupValidator.isSafePhotoFileName(""))
        assertFalse(BackupValidator.isSafePhotoFileName("a".repeat(120) + ".jpg"))
    }

    @Test
    fun `daty poza rozsądnym zakresem są odrzucane`() {
        val today = PlFormat.todayEpochDay()
        assertTrue(BackupValidator.isPlausibleEpochDay(today, today))
        assertTrue(BackupValidator.isPlausibleEpochDay(today + 365 * 20, today))
        assertFalse(BackupValidator.isPlausibleEpochDay(0, today))
        assertFalse(BackupValidator.isPlausibleEpochDay(today + 365 * 200, today))
        assertFalse(BackupValidator.isPlausibleEpochDay(LocalDate.of(1800, 1, 1).toEpochDay(), today))
    }

    @Test
    fun `tekst i liczby z kopii są sanityzowane`() {
        assertEquals("abc", BackupValidator.sanitizeText("  abc  "))
        assertNull(BackupValidator.sanitizeText("   "))
        assertNull(BackupValidator.sanitizeText(null))
        assertTrue(BackupValidator.sanitizeText("x".repeat(900))!!.length <= 500)
        // Sterowanie kursorem i znaki kontrolne nie mogą trafić do bazy ani do UI.
        assertFalse(BackupValidator.sanitizeText("a\u0000b\u001b[c")!!.contains('\u0000'))

        assertEquals(1.5, BackupValidator.sanitizeQuantity(1.5)!!, 0.0001)
        assertNull("Ujemna ilość jest odrzucana", BackupValidator.sanitizeQuantity(-3.0))
        assertNull("NaN jest odrzucane", BackupValidator.sanitizeQuantity(Double.NaN))
        assertNull("Nieskończoność jest odrzucana", BackupValidator.sanitizeQuantity(Double.POSITIVE_INFINITY))
        assertNull("Wartość absurdalnie duża jest odrzucana", BackupValidator.sanitizeQuantity(1e12))
    }
}
