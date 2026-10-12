package pl.terminownik.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.terminownik.app.TerminownikApplication
import pl.terminownik.app.data.local.toDomain
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.ReminderKind
import pl.terminownik.app.domain.ReminderSlot

/**
 * Odbiornik alarmu przypomnienia.
 *
 * W chwili zadziałania alarmu ponownie sprawdzamy stan danych: pozycja mogła zostać
 * zużyta, usunięta albo jej data mogła się zmienić. Dzięki temu nie pokazujemy
 * nieaktualnych przypomnień i nie wysyłamy duplikatów.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_KEY = "reminder_key"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_EXPIRY_EPOCH_DAY = "expiry_epoch_day"
        const val EXTRA_KIND = "reminder_kind"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val expiryEpochDay = intent.getLongExtra(EXTRA_EXPIRY_EPOCH_DAY, Long.MIN_VALUE)
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: ReminderKind.LEAD.name

        val application = context.applicationContext as? TerminownikApplication ?: return
        val pendingResult = goAsync()
        scope.launch {
            try {
                val container = application.container
                val database = container.database
                val item = database.itemDao().getById(itemId)?.toDomain()
                val alreadySent = database.reminderLogDao().contains(key)

                val stillValid = item != null &&
                    item.status == ItemStatus.ACTIVE &&
                    item.reminderEnabled &&
                    item.expiryDate != null &&
                    item.expiryDate.confirmed &&
                    item.expiryDate.epochDay == expiryEpochDay &&
                    !alreadySent

                if (stillValid && item != null) {
                    val slot = ReminderSlot(
                        key = key,
                        itemId = itemId,
                        itemName = item.name,
                        section = item.section,
                        expiryEpochDay = expiryEpochDay,
                        fireEpochDay = expiryEpochDay,
                        kind = runCatching { ReminderKind.valueOf(kindName) }.getOrDefault(ReminderKind.LEAD),
                        dateType = item.expiryDate?.type ?: DateType.UNSPECIFIED,
                    )
                    container.reminderNotification.publish(slot, key.hashCode())
                    container.reminderScheduler.markSent(slot)
                }
                container.reminderScheduler.removeScheduled(key)
                // Po przypomnieniu z wyprzedzeniem planujemy kolejne (np. w dniu terminu).
                if (itemId >= 0) {
                    container.reminderScheduler.rescheduleItem(itemId)
                }
            } catch (t: Throwable) {
                // Celowo połykamy wyjątek: awaria przypomnienia nie może wywrócić aplikacji.
                android.util.Log.w("ReminderAlarm", "Przypomnienie nie zostało dostarczone", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
