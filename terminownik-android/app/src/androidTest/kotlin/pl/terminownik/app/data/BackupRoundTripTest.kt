package pl.terminownik.app.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.terminownik.app.data.backup.BackupContract
import pl.terminownik.app.data.backup.BackupManifestDto
import pl.terminownik.app.data.backup.BackupRepository
import pl.terminownik.app.data.backup.BackupResult
import pl.terminownik.app.data.backup.RestoreMode
import pl.terminownik.app.data.local.TerminownikDatabase
import pl.terminownik.app.data.photo.PhotoStorage
import pl.terminownik.app.data.settings.SettingsRepository
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.DayPrecision
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.PantryItem
import pl.terminownik.app.domain.Section
import pl.terminownik.app.domain.ShoppingItem
import pl.terminownik.app.notifications.ReminderNotification
import pl.terminownik.app.notifications.ReminderScheduler

/**
 * Pełny obieg kopii zapasowej na urządzeniu.
 *
 * Brief: „przywrócenie kopii musi odtworzyć ilości, partie, zdjęcia i terminy ważności”
 * oraz „importowane pliki traktuj jako niezaufane”.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: TerminownikDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var photos: PhotoStorage
    private lateinit var repository: PantryRepository
    private lateinit var backups: BackupRepository
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() = runTest {
        database = TerminownikDatabase.get(context)
        settings = SettingsRepository(context)
        photos = PhotoStorage(context)
        val scheduler = ReminderScheduler(context, database, settings, ReminderNotification(context))
        repository = PantryRepository(database, settings, photos, scheduler)
        backups = BackupRepository(context, database, settings, photos, scheduler)
        repository.eraseAllData()
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
        TerminownikDatabase.closeInstance()
    }

    private fun newFile(prefix: String, suffix: String): Uri {
        val file = File(context.cacheDir, "$prefix-${System.nanoTime()}$suffix")
        tempFiles += file
        return Uri.fromFile(file)
    }

    /** Minimalny, ale prawdziwy plik JPEG (nagłówek + EOI) – wystarczy do testów kopiowania. */
    private fun fakeJpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00,
        0xFF.toByte(), 0xD9.toByte(),
    )

    private suspend fun seedData(): Long {
        val expiry = LocalDate.now().plusDays(45).toEpochDay()
        assertTrue(photos.writeBytes("test-photo.jpg", fakeJpeg()))
        val id = repository.addOrUpdate(
            PantryItem(
                section = Section.MEDICINE,
                name = "Syrop na kaszel",
                quantity = 1.5,
                unit = "opak.",
                storageLocation = "Apteczka",
                photoPath = "test-photo.jpg",
                batchCode = "B-2027",
                barcode = "5909990000019",
                expiryDate = ExpiryDate(
                    epochDay = expiry,
                    dayPrecision = DayPrecision.EXACT,
                    type = DateType.USE_BY,
                    source = DateSource.MANUAL,
                    confirmed = true,
                ),
                note = "kupione w aptece",
            ),
        )
        repository.addShoppingItem(
            ShoppingItem(name = "Mleko", quantity = 2.0, unit = "szt.", createdAt = System.currentTimeMillis()),
        )
        return id
    }

    @Test
    fun `eksport i odtworzenie zastępujące przywraca wszystkie dane`() = runTest {
        val id = seedData()
        val target = newFile("kopia", ".zip")

        val exported = backups.export(target)
        assertTrue("Eksport powinien się udać: $exported", exported is BackupResult.Success)
        val summary = (exported as BackupResult.Success).value
        assertEquals(1, summary.items)
        assertEquals(1, summary.photos)
        assertEquals(1, summary.shoppingItems)
        assertTrue(summary.bytes > 0)

        // Symulacja utraty danych.
        repository.eraseAllData()
        assertEquals(0, repository.counts().first)
        assertEquals(0, photos.count())

        val restored = backups.restore(target, RestoreMode.REPLACE_ALL)
        assertTrue("Odtworzenie powinno się udać: $restored", restored is BackupResult.Success)
        val restoreSummary = (restored as BackupResult.Success).value
        assertEquals(1, restoreSummary.items)
        assertEquals(1, restoreSummary.photos)

        val items = repository.itemsWithExpirySnapshot()
        assertEquals(1, items.size)
        val item = items.first().item
        assertEquals("Syrop na kaszel", item.name)
        assertEquals(Section.MEDICINE, item.section)
        assertEquals(1.5, item.quantity!!, 0.0001)
        assertEquals("opak.", item.unit)
        assertEquals("B-2027", item.batchCode)
        assertEquals("Apteczka", item.storageLocation)
        assertEquals("kupione w aptece", item.note)
        assertEquals("5909990000019", item.barcode)

        val expiry = item.expiryDate
        assertNotNull("Termin ważności musi zostać odtworzony", expiry)
        assertEquals(LocalDate.now().plusDays(45).toEpochDay(), expiry!!.epochDay)
        assertEquals(DateType.USE_BY, expiry.type)
        assertEquals(DayPrecision.EXACT, expiry.dayPrecision)
        assertTrue(expiry.confirmed)

        // Zdjęcie musi istnieć na dysku i być powiązane z pozycją.
        assertNotNull(item.photoPath)
        val photoFile = photos.fileFor(item.photoPath)
        assertNotNull("Plik zdjęcia nie został odtworzony", photoFile)
        assertTrue(photoFile!!.exists())
        assertTrue(photoFile.length() > 0)

        assertEquals(1, repository.shoppingList.first().size)
        assertNotNull("Słownik kodów musi zostać odtworzony", repository.lookupBarcodeName("5909990000019"))
        assertTrue("Id pozycji musi być poprawne", id > 0)
    }

    @Test
    fun `tryb dołączania nie tworzy duplikatów tej samej pozycji`() = runTest {
        seedData()
        val target = newFile("kopia", ".zip")
        assertTrue(backups.export(target) is BackupResult.Success)

        val first = backups.restore(target, RestoreMode.MERGE)
        assertTrue(first is BackupResult.Success)
        assertEquals(1, repository.itemsWithExpirySnapshot().size)

        val second = backups.restore(target, RestoreMode.MERGE)
        assertTrue(second is BackupResult.Success)
        assertEquals(
            "Ponowne dołączenie tej samej kopii nie może duplikować pozycji",
            1,
            repository.itemsWithExpirySnapshot().size,
        )
        assertTrue((second as BackupResult.Success).value.skippedItems >= 1)
    }

    @Test
    fun `plik, który nie jest ZIP, zostaje odrzucony`() = runTest {
        val target = newFile("zly", ".zip")
        context.contentResolver.openOutputStream(target)?.use { it.write("to nie jest zip".toByteArray()) }
        val result = backups.restore(target, RestoreMode.REPLACE_ALL)
        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupContract.Error.NOT_A_ZIP, (result as BackupResult.Failure).code)
    }

    @Test
    fun `ZIP bez manifestu zostaje odrzucony`() = runTest {
        val target = newFile("bez-manifestu", ".zip")
        ZipOutputStream(context.contentResolver.openOutputStream(target)!!).use { zip ->
            zip.putNextEntry(ZipEntry("items.json"))
            zip.write("[]".toByteArray())
            zip.closeEntry()
        }
        val result = backups.inspect(target)
        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupContract.Error.MISSING_MANIFEST, (result as BackupResult.Failure).code)
    }

    @Test
    fun `kopia innej aplikacji zostaje odrzucona`() = runTest {
        val target = newFile("obca", ".zip")
        val manifest = BackupManifestDto(
            formatVersion = BackupContract.FORMAT_VERSION,
            app = "com.inna.aplikacja",
            appVersionName = "9.9.9",
            createdAtEpochMillis = System.currentTimeMillis(),
            counts = mapOf("items" to 5),
            entries = emptyList(),
        )
        ZipOutputStream(context.contentResolver.openOutputStream(target)!!).use { zip ->
            zip.putNextEntry(ZipEntry(BackupContract.MANIFEST_PATH))
            zip.write(Json.encodeToString(BackupManifestDto.serializer(), manifest).toByteArray())
            zip.closeEntry()
        }
        val result = backups.restore(target, RestoreMode.REPLACE_ALL)
        assertTrue("Obca kopia musi zostać odrzucona: $result", result is BackupResult.Failure)
        assertEquals(BackupContract.Error.WRONG_APP, (result as BackupResult.Failure).code)
        assertEquals("Obca kopia nie może nic zmienić", 0, repository.counts().first)
    }

    @Test
    fun `kopia z nowszej wersji aplikacji zostaje odrzucona`() = runTest {
        val target = newFile("nowsza", ".zip")
        val manifest = BackupManifestDto(
            formatVersion = BackupContract.FORMAT_VERSION + 5,
            app = BackupContract.APP_ID,
            appVersionName = "99.0.0",
            createdAtEpochMillis = System.currentTimeMillis(),
            counts = mapOf("items" to 1),
            entries = emptyList(),
        )
        ZipOutputStream(context.contentResolver.openOutputStream(target)!!).use { zip ->
            zip.putNextEntry(ZipEntry(BackupContract.MANIFEST_PATH))
            zip.write(Json.encodeToString(BackupManifestDto.serializer(), manifest).toByteArray())
            zip.closeEntry()
        }
        val result = backups.inspect(target)
        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupContract.Error.FORMAT_TOO_NEW, (result as BackupResult.Failure).code)
    }

    @Test
    fun `podgląd kopii nie zmienia danych`() = runTest {
        seedData()
        val target = newFile("kopia", ".zip")
        assertTrue(backups.export(target) is BackupResult.Success)

        repository.eraseAllData()
        val inspect = backups.inspect(target)
        assertTrue(inspect is BackupResult.Success)
        val manifest = (inspect as BackupResult.Success).value
        assertEquals(BackupContract.FORMAT_VERSION, manifest.formatVersion)
        assertEquals(1, manifest.counts["items"])
        assertEquals("Podgląd nie może odtworzyć danych", 0, repository.counts().first)
        assertFalse(manifest.entries.isEmpty())
    }
}
