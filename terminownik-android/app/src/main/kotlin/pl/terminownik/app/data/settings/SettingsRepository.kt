package pl.terminownik.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.ThemeMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "terminownik_settings")

/**
 * Ustawienia użytkownika w DataStore (Preferences).
 * Dane zostają na urządzeniu; aplikacja nie wymaga konta ani dostępu do sieci.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_LEAD_DAYS = intPreferencesKey("reminder_lead_days")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMIND_ON_DUE_DAY = booleanPreferencesKey("remind_on_due_day")
        val SHOW_ARCHIVED = booleanPreferencesKey("show_archived")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { cause ->
            // Uszkodzony plik ustawień nie może blokować aplikacji – wracamy do domyślnych.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            AppSettings(
                onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
                }.getOrDefault(ThemeMode.SYSTEM),
                reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: true,
                reminderLeadDays = (prefs[Keys.REMINDER_LEAD_DAYS] ?: 7).coerceIn(0, 365),
                reminderHourOfDay = (prefs[Keys.REMINDER_HOUR] ?: 9).coerceIn(0, 23),
                reminderMinute = (prefs[Keys.REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
                remindOnDueDay = prefs[Keys.REMIND_ON_DUE_DAY] ?: true,
                showArchived = prefs[Keys.SHOW_ARCHIVED] ?: false,
            )
        }

    suspend fun setOnboardingDone(done: Boolean) = update { it[Keys.ONBOARDING_DONE] = done }

    suspend fun setThemeMode(mode: ThemeMode) = update { it[Keys.THEME_MODE] = mode.name }

    suspend fun setReminderEnabled(enabled: Boolean) = update { it[Keys.REMINDER_ENABLED] = enabled }

    suspend fun setReminderLeadDays(days: Int) =
        update { it[Keys.REMINDER_LEAD_DAYS] = days.coerceIn(0, 365) }

    suspend fun setReminderTime(hour: Int, minute: Int) = update {
        it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
        it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setRemindOnDueDay(enabled: Boolean) = update { it[Keys.REMIND_ON_DUE_DAY] = enabled }

    suspend fun setShowArchived(show: Boolean) = update { it[Keys.SHOW_ARCHIVED] = show }

    suspend fun replaceAll(settings: AppSettings) = update { prefs ->
        prefs[Keys.ONBOARDING_DONE] = settings.onboardingDone
        prefs[Keys.THEME_MODE] = settings.themeMode.name
        prefs[Keys.REMINDER_ENABLED] = settings.reminderEnabled
        prefs[Keys.REMINDER_LEAD_DAYS] = settings.reminderLeadDays
        prefs[Keys.REMINDER_HOUR] = settings.reminderHourOfDay
        prefs[Keys.REMINDER_MINUTE] = settings.reminderMinute
        prefs[Keys.REMIND_ON_DUE_DAY] = settings.remindOnDueDay
        prefs[Keys.SHOW_ARCHIVED] = settings.showArchived
    }

    /** Część pełnego usunięcia danych użytkownika. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }
}
