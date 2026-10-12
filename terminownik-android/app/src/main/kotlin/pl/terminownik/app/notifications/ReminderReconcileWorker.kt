package pl.terminownik.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import pl.terminownik.app.TerminownikApplication

/**
 * Siatka bezpieczeństwa dla przypomnień.
 *
 * UWAGA (świadome ograniczenie): periodyczna praca WorkManager NIE działa w czasie
 * rzeczywistym, a jej minimalny interwał wynosi 15 minut. Dlatego głównym mechanizmem
 * są alarmy systemowe, a ten worker uruchamia się raz dziennie i tylko przelicza stan:
 * naprawia alarmy utracone po restrykcjach producenta systemu oraz nadrabia zaległości.
 */
class ReminderReconcileWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? TerminownikApplication ?: return Result.failure()
        return try {
            application.container.reminderScheduler.rescheduleAll()
            application.container.reminderScheduler.catchUp()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "terminownik_reminder_reconcile"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderReconcileWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
