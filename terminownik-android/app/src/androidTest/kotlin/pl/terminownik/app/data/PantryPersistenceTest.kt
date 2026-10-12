package pl.terminownik.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.terminownik.app.data.local.TerminownikDatabase
import pl.terminownik.app.data.photo.PhotoStorage
import pl.terminownik.app.data.settings.SettingsRepository
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.DayPrecision
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.PantryItem
import pl.terminownik.app.domain.Section
import pl.terminownik.app.notifications.ReminderNotification
import pl.terminownik.app.notifications.ReminderScheduler

/**
 * Testy trwałości danych na prawdziwej bazie plikowej.
 *
 * Brief: „sprawdź trwałość danych po zamknięciu i ponownym otwarciu aplikacji” oraz
 * „ten sam produkt może mieć kilka partii z różnymi datami i nie łącz ich automatycznie”.
 */
@RunWith(AndroidJUnit4::class)
class PantryPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: TerminownikDatabase
    private lateinit var repository: PantryRepository

    private fun buildRepository(): PantryRepository {
        database = TerminownikDatabase.get(context)
        val settings = SettingsRepository(context)
        val photos = PhotoStorage(context)
        val scheduler = ReminderScheduler(context, database, settings, ReminderNotification(context))
        return PantryRepository(database, settings, photos, scheduler)
    }

    @Before
    fun setUp() = runTest {
        repository = buildRepository()
        repository.eraseAllData()
    }

    @After
    fun tearDown() {
        TerminownikDatabase.closeInstance()
    }

    private fun item(
        name: String,
        expiryDay: Long,
        batch: String? = null,
        quantity: Double? = 1.0,
        section: Section = Section.PANTRY,
    ) = PantryItem(
        section = section,
        name = name,
        quantity = quantity,
        unit = "szt.",
        storageLocation = "Lodówka",
        batchCode = batch,
        barcode = "5901234123457",
        expiryDate = ExpiryDate(
            epochDay = expiryDay,
            dayPrecision = DayPrecision.EXACT,
            type = DateType.USE_BY,
            source = DateSource.MANUAL,
            confirmed = true,
        ),
    )

    @Test
    fun `dane przetrwają zamknięcie i ponowne otwarcie bazy`() = runTest {
        val expiry = LocalDate.now().plusDays(20).toEpochDay()
        val id = repository.addOrUpdate(item("Jogurt naturalny", expiry, batch = "A1", quantity = 2.5))
        assertTrue(id > 0)

        // Symulacja zamknięcia aplikacji: zamykanie instancji bazy i utworzenie jej ponownie.
        TerminownikDatabase.closeInstance()
        repository = buildRepository()

        val restored = repository.getItem(id)
        assertNotNull("Pozycja zniknęła po ponownym otwarciu bazy", restored)
        assertEquals("Jogurt naturalny", restored!!.name)
        assertEquals(2.5, restored.quantity!!, 0.0001)
        assertEquals("A1", restored.batchCode)
        assertEquals("Lodówka", restored.storageLocation)
        assertEquals(expiry, restored.expiryDate?.epochDay)
        assertEquals(DateType.USE_BY, restored.expiryDate?.type)
        assertTrue("Data wpisana ręcznie musi być potwierdzona", restored.expiryDate?.confirmed == true)
        assertEquals(ItemStatus.ACTIVE, restored.status)
    }

    @Test
    fun `dwie partie tego samego produktu pozostają osobne`() = runTest {
        val first = LocalDate.now().plusDays(5).toEpochDay()
        val second = LocalDate.now().plusDays(60).toEpochDay()
        val idFirst = repository.addOrUpdate(item("Mleko", first, batch = "P1"))
        val idSecond = repository.addOrUpdate(item("Mleko", second, batch = "P2"))

        assertNotEquals("Partie muszą być osobnymi wierszami", idFirst, idSecond)
        val sameCode = repository.itemsWithExpirySnapshot().filter { it.item.barcode == "5901234123457" }
        assertEquals(2, sameCode.size)
        assertEquals(setOf(first, second), sameCode.map { it.item.expiryDate?.epochDay }.toSet())
    }

    @Test
    fun `zużycie jednej partii nie usuwa drugiej`() = runTest {
        val first = LocalDate.now().plusDays(5).toEpochDay()
        val second = LocalDate.now().plusDays(60).toEpochDay()
        val idFirst = repository.addOrUpdate(item("Mleko", first, batch = "P1"))
        val idSecond = repository.addOrUpdate(item("Mleko", second, batch = "P2"))

        repository.markUsed(idFirst)

        val remaining = repository.getItem(idSecond)
        assertNotNull("Druga partia zniknęła po zużyciu pierwszej", remaining)
        assertEquals(ItemStatus.ACTIVE, remaining!!.status)
        assertEquals(second, remaining.expiryDate?.epochDay)

        val used = repository.getItem(idFirst)
        assertEquals(ItemStatus.USED, used?.status)
    }

    @Test
    fun `uzupełnienie tworzy nową partię i nie zmienia starej`() = runTest {
        val old = LocalDate.now().plusDays(3).toEpochDay()
        val idOld = repository.addOrUpdate(item("Ser", old, batch = "S1", quantity = 1.0))

        val fresh = LocalDate.now().plusDays(30).toEpochDay()
        val idNew = repository.restock(idOld, newQuantity = 3.0, newExpiry = ExpiryDate(fresh), newBatchCode = "S2")

        assertNotEquals(idOld, idNew)
        assertEquals(1.0, repository.getItem(idOld)?.quantity!!, 0.0001)
        assertEquals(old, repository.getItem(idOld)?.expiryDate?.epochDay)
        assertEquals(3.0, repository.getItem(idNew)?.quantity!!, 0.0001)
        assertEquals(fresh, repository.getItem(idNew)?.expiryDate?.epochDay)
        assertEquals("S2", repository.getItem(idNew)?.batchCode)
    }

    @Test
    fun `data z OCR bez potwierdzenia pozostaje niepotwierdzona po zapisie`() = runTest {
        val expiry = ExpiryDate(
            epochDay = LocalDate.now().plusDays(10).toEpochDay(),
            dayPrecision = DayPrecision.MONTH_ONLY,
            type = DateType.BEST_BEFORE,
            source = DateSource.OCR,
            confirmed = false,
        )
        val id = repository.addOrUpdate(item("Konserwa", expiry.epochDay).copy(expiryDate = expiry))
        val saved = repository.getItem(id)?.expiryDate
        assertEquals(false, saved?.confirmed)
        assertEquals(DateSource.OCR, saved?.source)
        assertEquals(DayPrecision.MONTH_ONLY, saved?.dayPrecision)

        repository.confirmExpiryDate(id)
        assertEquals(true, repository.getItem(id)?.expiryDate?.confirmed)
    }

    @Test
    fun `usunięcie pozycji usuwa ją trwale`() = runTest {
        val id = repository.addOrUpdate(item("Chleb", LocalDate.now().plusDays(2).toEpochDay()))
        repository.deleteItem(id)
        assertNull(repository.getItem(id))
        TerminownikDatabase.closeInstance()
        repository = buildRepository()
        assertNull("Pozycja wróciła po ponownym otwarciu bazy", repository.getItem(id))
    }

    @Test
    fun `słownik kodów zapamiętuje ostatnią nazwę`() = runTest {
        repository.addOrUpdate(item("Jogurt naturalny", LocalDate.now().plusDays(9).toEpochDay()))
        val known = repository.lookupBarcodeName("5901234123457")
        assertNotNull(known)
        assertEquals("Jogurt naturalny", known!!.name)

        repository.addOrUpdate(item("Jogurt grecki", LocalDate.now().plusDays(12).toEpochDay()))
        assertEquals("Ostatnia nazwa powinna wygrać", "Jogurt grecki", repository.lookupBarcodeName("5901234123457")?.name)
    }
}
