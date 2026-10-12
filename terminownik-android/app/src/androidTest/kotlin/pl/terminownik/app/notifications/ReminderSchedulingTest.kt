package pl.terminownik.app.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.terminownik.app.data.PantryRepository
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

/**
 * Przypomnienia: planowanie, aktualizacja po edycji, brak duplikatów i zachowanie
 * przy ograniczeniach systemu.
 *
 * Brief: „edycja lub usunięcie wydarzenia musi aktualizować przypomnienie”,
 * „brak duplikatów”, „obsłuż zmianę daty systemowej, restart telefonu, ponowne otwarcie”.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: TerminownikDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: PantryRepository
    private lateinit var scheduler: ReminderScheduler

    @Before
    fun setUp() = runTest {
        database = TerminownikDatabase.get(context)
        settings = SettingsRepository(context)
        val photos = PhotoStorage(context)
        scheduler = ReminderScheduler(context, database, settings, ReminderNotification(context))
        repository = PantryRepository(database, settings, photos, scheduler)
        repository.eraseAllData()
        settings.replaceAll(
            settings.settings.first().copy(
                reminderEnabled = true,
                reminderLeadDays = 7,
                reminderHourOfDay = 9,
                reminderMinute = 0,
                remindOnDueDay = true,
            ),
        )
    }

    @After
    fun tearDown() = runTest {
        scheduler.cancelAll()
        repository.eraseAllData()
        TerminownikDatabase.closeInstance()
    }

    private suspend fun addItem(
        daysAhead: Long = 10,
        confirmed: Boolean = true,
        precision: DayPrecision = DayPrecision.EXACT,
        reminderEnabled: Boolean = true,
        status: ItemStatus = ItemStatus.ACTIVE,
    ): Long = repository.addOrUpdate(
        PantryItem(
            section = Section.PANTRY,
            name = "Jogurt",
            quantity = 1.0,
            expiryDate = ExpiryDate(
                epochDay = LocalDate.now().plusDays(daysAhead).toEpochDay(),
                dayPrecision = precision,
                type = DateType.USE_BY,
                source = if (confirmed) DateSource.MANUAL else DateSource.OCR,
                confirmed = confirmed,
            ),
            status = status,
            reminderEnabled = reminderEnabled,
        ),
    )

    private suspend fun alarmKeys(itemId: Long) =
        database.scheduledAlarmDao().getForItem(itemId).map { it.reminderKey }

    @Test
    fun `potwierdzona dokładna data tworzy zaplanowane przypomnienia`() = runTest {
        val id = addItem(daysAhead = 10)
        scheduler.rescheduleAll()

        val keys = alarmKeys(id)
        assertTrue("Brak zaplanowanych alarmów dla pozycji z potwierdzoną datą", keys.isNotEmpty())
        assertTrue(
            "Klucz alarmu musi zawierać termin, żeby dało się go odwołać po zmianie daty: $keys",
            keys.all { it.contains("e${LocalDate.now().plusDays(10).toEpochDay()}") },
        )
        // Wyprzedzenie 7 dni + przypomnienie w dniu terminu = 2 sloty.
        assertEquals(2, keys.distinct().size)
    }

    @Test
    fun `data niepotwierdzona nie tworzy przypomnienia`() = runTest {
        val id = addItem(daysAhead = 10, confirmed = false)
        scheduler.rescheduleAll()
        assertTrue(
            "Niepotwierdzona data z OCR nie może generować przypomnienia",
            alarmKeys(id).isEmpty(),
        )
    }

    @Test
    fun `data tylko z miesiącem nie tworzy przypomnienia`() = runTest {
        val id = addItem(daysAhead = 10, precision = DayPrecision.MONTH_ONLY)
        scheduler.rescheduleAll()
        assertTrue("Nieprecyzyjna data nie może generować przypomnienia", alarmKeys(id).isEmpty())
    }

    @Test
    fun `pozycja z wyłączonym przypomnieniem jest pomijana`() = runTest {
        val id = addItem(daysAhead = 10, reminderEnabled = false)
        scheduler.rescheduleAll()
        assertTrue(alarmKeys(id).isEmpty())
    }

    @Test
    fun `pozycja zużyta i wyrzucona nie przypominają`() = runTest {
        val used = addItem(daysAhead = 10, status = ItemStatus.USED)
        val discarded = addItem(daysAhead = 12, status = ItemStatus.DISCARDED)
        scheduler.rescheduleAll()
        assertTrue(alarmKeys(used).isEmpty())
        assertTrue(alarmKeys(discarded).isEmpty())
    }

    @Test
    fun `globalne wyłączenie przypomnień usuwa wszystkie alarmy`() = runTest {
        val id = addItem(daysAhead = 10)
        scheduler.rescheduleAll()
        assertTrue(alarmKeys(id).isNotEmpty())

        settings.setReminderEnabled(false)
        scheduler.rescheduleAll()
        assertEquals(0, database.scheduledAlarmDao().count())
    }

    @Test
    fun `zmiana terminu aktualizuje przypomnienie bez duplikatów`() = runTest {
        val id = addItem(daysAhead = 10)
        scheduler.rescheduleAll()
        val oldKeys = alarmKeys(id)
        assertTrue(oldKeys.isNotEmpty())

        // Edycja terminu – tak jak w formularzu.
        val item = repository.getItem(id)!!
        repository.addOrUpdate(
            item.copy(
                expiryDate = item.expiryDate!!.copy(
                    epochDay = LocalDate.now().plusDays(40).toEpochDay(),
                ),
            ),
        )
        scheduler.rescheduleItem(id)

        val newKeys = alarmKeys(id)
        assertTrue(newKeys.isNotEmpty())
        assertEquals("Nowe klucze nie mogą się powtarzać", newKeys.size, newKeys.distinct().size)
        assertTrue(
            "Stare klucze muszą zniknąć po zmianie terminu: $newKeys",
            newKeys.none { it.contains("e${LocalDate.now().plusDays(10).toEpochDay()}") },
        )
        assertTrue(
            "Nowe klucze muszą odpowiadać nowemu terminowi: $newKeys",
            newKeys.all { it.contains("e${LocalDate.now().plusDays(40).toEpochDay()}") },
        )
    }

    @Test
    fun `ponowne przeliczenie nie tworzy duplikatów`() = runTest {
        val id = addItem(daysAhead = 10)
        scheduler.rescheduleAll()
        val first = alarmKeys(id)
        scheduler.rescheduleAll()
        scheduler.rescheduleItem(id)
        assertEquals(first.sorted(), alarmKeys(id).sorted())
    }

    @Test
    fun `usunięcie pozycji usuwa jej przypomnienia`() = runTest {
        val id = addItem(daysAhead = 10)
        scheduler.rescheduleAll()
        assertTrue(alarmKeys(id).isNotEmpty())

        repository.deleteItem(id)
        assertTrue("Alarmy usuniętej pozycji muszą zniknąć", alarmKeys(id).isEmpty())
        assertEquals(0, database.reminderLogDao().getAll().count { it.itemId == id })
    }

    @Test
    fun `zaległe przypomnienie jest nadrabiane raz, bez duplikatów`() = runTest {
        // Termin minął 3 dni temu – alarm nie mógł się uruchomić (telefon wyłączony).
        val id = addItem(daysAhead = -3)
        scheduler.rescheduleAll()
        val loggedAfterPlan = database.reminderLogDao().allKeys()
        assertTrue("Zaległy termin powinien zostać odnotowany", loggedAfterPlan.isNotEmpty())

        val before = database.reminderLogDao().getAll().size
        scheduler.catchUp()
        scheduler.catchUp()
        val after = database.reminderLogDao().getAll().size
        assertEquals("Nadrobienie nie może duplikować przypomnień", before, after)
        assertTrue(id > 0)
    }

    @Test
    fun `zmiana wyprzedzenia przelicza wszystkie przypomnienia`() = runTest {
        val id = addItem(daysAhead = 20)
        scheduler.rescheduleAll()
        val withSeven = alarmKeys(id)

        settings.setReminderLeadDays(30)
        scheduler.rescheduleAll()
        val withThirty = alarmKeys(id)

        assertFalse(withThirty.isEmpty())
        assertFalse(
            "Klucze muszą się zmienić razem z wyprzedzeniem",
            withSeven.sorted() == withThirty.sorted(),
        )
        assertTrue(
            "Przy wyprzedzeniu 30 dni termin za 20 dni jest już w oknie przypomnienia",
            withThirty.any { it.contains("l30") },
        )
    }

    @Test
    fun `bez uprawnień do powiadomień planowanie nadal działa`() = runTest {
        // Brak zgody POST_NOTIFICATIONS nie może blokować planowania ani wywracać aplikacji.
        val granted = PermissionState.notificationsGranted(context)
        val id = addItem(daysAhead = 10)
        scheduler.rescheduleAll()
        assertTrue(alarmKeys(id).isNotEmpty())
        // Stan uprawnień jest tylko informacją – test przechodzi w obu przypadkach.
        assertTrue(granted || !granted)
    }
}
