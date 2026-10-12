package pl.terminownik.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.terminownik.app.TerminownikApplication

/**
 * Zdarzenia systemowe, po których przypomnienia muszą zostać przeliczone:
 * restart telefonu, zmiana czasu, zmiana strefy czasowej, zmiana daty, aktualizacja aplikacji.
 *
 * Wszystkie te akcje to chronione broadcasty systemowe.
 */
class SystemEventReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val handled = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> true

            else -> false
        }
        if (!handled) return

        val application = context.applicationContext as? TerminownikApplication ?: return
        val pendingResult = goAsync()
        scope.launch {
            try {
                // Pełne przeliczenie obejmuje też nadrobienie przypomnień pominiętych
                // w czasie, gdy telefon był wyłączony albo aplikacja nie działała.
                application.container.reminderScheduler.rescheduleAll()
                application.container.reminderScheduler.catchUp()
            } catch (t: Throwable) {
                android.util.Log.w("SystemEvent", "Przeliczenie przypomnień nie powiodło się", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
