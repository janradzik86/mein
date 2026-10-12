package pl.terminownik.app

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.terminownik.app.notifications.NotificationChannels
import pl.terminownik.app.notifications.ReminderReconcileWorker

/**
 * Aplikacja działa w pełni lokalnie: nie inicjalizujemy żadnego SDK analitycznego,
 * reklamowego ani sieciowego. Jedyny „ruch” w aplikacji to alarmy i prace w tle.
 */
class TerminownikApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.create(this)

        // Konfiguracja WorkManager musi być dostępna synchronicznie przy starcie.
        applicationScope.launch {
            // Siatka bezpieczeństwa przypomnień (raz dziennie) – patrz ReminderReconcileWorker.
            ReminderReconcileWorker.enqueue(this@TerminownikApplication)
            // Nadrobienie przypomnień po ponownym uruchomieniu telefonu/aplikacji.
            runCatching { container.reminderScheduler.rescheduleAll() }
        }
    }

    /**
     * Własna konfiguracja WorkManager (domyślny inicjalizator jest wyłączony w manifeście),
     * żeby logi produkcyjne nie zawierały danych diagnostycznych.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()
}
