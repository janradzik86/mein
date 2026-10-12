package pl.terminownik.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.terminownik.app.data.settings.SettingsRepository
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.ThemeMode

/** Ustawienia w DataStore: zapis, odczyt i odtworzenie z kopii (replaceAll). */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        settings = SettingsRepository(context)
    }

    @Test
    fun `zmiana wyprzedzenia jest trwała`() = runTest {
        settings.setReminderLeadDays(21)
        assertEquals(21, settings.settings.first().reminderLeadDays)
        // Ponowne utworzenie repozytorium = symulacja restartu aplikacji.
        val reopened = SettingsRepository(context)
        assertEquals(21, reopened.settings.first().reminderLeadDays)
    }

    @Test
    fun `motyw i godzina przypomnienia zapisują się razem`() = runTest {
        settings.setThemeMode(ThemeMode.DARK)
        settings.setReminderTime(18, 45)
        val current = settings.settings.first()
        assertEquals(ThemeMode.DARK, current.themeMode)
        assertEquals(18, current.reminderHourOfDay)
        assertEquals(45, current.reminderMinute)
    }

    @Test
    fun `godzina poza zakresem jest sprowadzana do poprawnej`() = runTest {
        settings.setReminderTime(99, 99)
        val current = settings.settings.first()
        assertTrue(current.reminderHourOfDay in 0..23)
        assertTrue(current.reminderMinute in 0..59)
    }

    @Test
    fun `replaceAll odtwarza ustawienia z kopii`() = runTest {
        val fromBackup = AppSettings(
            onboardingDone = true,
            themeMode = ThemeMode.LIGHT,
            reminderEnabled = false,
            reminderLeadDays = 30,
            reminderHourOfDay = 7,
            reminderMinute = 15,
            remindOnDueDay = false,
            showArchived = true,
        )
        settings.replaceAll(fromBackup)
        val current = settings.settings.first()
        assertEquals(fromBackup, current)
        assertFalse(current.reminderEnabled)
    }

    @Test
    fun `flaga wdrożenia domyślnie wymaga pokazania onboardingu`() = runTest {
        settings.clear()
        assertFalse(SettingsRepository(context).settings.first().onboardingDone)
        settings.setOnboardingDone(true)
        assertTrue(SettingsRepository(context).settings.first().onboardingDone)
        settings.clear()
    }
}
