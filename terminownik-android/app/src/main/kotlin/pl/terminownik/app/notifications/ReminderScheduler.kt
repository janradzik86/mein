package pl.terminownik.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import pl.terminownik.app.data.local.ReminderLogEntity
import pl.terminownik.app.data.local.ScheduledAlarmEntity
import pl.terminownik.app.data.local.TerminownikDatabase
import pl.terminownik.app.data.local.toDomain
import pl.terminownik.app.data.settings.SettingsRepository
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.ReminderPlanner
import pl.terminownik.app.domain.ReminderSlot

/**
 * Planowanie przypomnień o terminach ważności.
 *
 * Model działania (uczciwy wobec ograniczeń Androida):
 *  1. Podstawą są alarmy systemowe (`AlarmManager`) ustawiane na konkretny dzień i godzinę.
 *     Gdy użytkownik zezwolił na dokładne alarmy, używamy `setExactAndAllowWhileIdle`;
 *     w przeciwnym razie `setWindow` z 15-minutowym oknem i NIE obiecujemy punktualności.
 *  2. Periodyczny `WorkManager` (raz dziennie) jest wyłącznie siatką bezpieczeństwa:
 *     minimalny interwał pracy okresowej to 15 minut i nie gwarantuje on czasu wykonania.
 *  3. Po restarcie telefonu, zmianie czasu/strefy/daty oraz przy otwarciu aplikacji
 *     przeliczamy wszystko od nowa i nadrabiamy pominięte przypomnienia.
 *  4. Każdy slot ma klucz antyduplikacyjny zapisywany w `reminder_log`, więc to samo
 *     przypomnienie nie zostanie pokazane dwa razy.
 */
class ReminderScheduler(
    private val context: Context,
    private val database: TerminownikDatabase,
    private val settingsRepository: SettingsRepository,
    private val notification: ReminderNotification,
) {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private val itemDao = database.itemDao()
    private val reminderLogDao = database.reminderLogDao()
    private val alarmDao = database.scheduledAlarmDao()

    /** Pełne przeliczenie: anulowanie starych alarmów, zaplanowanie nowych, nadrobienie zaległości. */
    suspend fun rescheduleAll() {
        val settings = settingsRepository.settings.first()
        cancelAllAlarms()
        if (!settings.reminderEnabled) return

        val today = PlFormat.todayEpochDay()
        val items = itemDao.getAll().map { it.toDomain() }
        val slots = ReminderPlanner.planAll(items, today, settings)
        deliverOrSchedule(slots, settings)
    }

    /** Przeliczenie przypomnień jednej pozycji po jej edycji, potwierdzeniu daty lub zmianie statusu. */
    suspend fun rescheduleItem(itemId: Long) {
        val settings = settingsRepository.settings.first()
        cancelItemAlarms(itemId)
        if (!settings.reminderEnabled) return

        val item = itemDao.getById(itemId)?.toDomain() ?: return
        val slots = ReminderPlanner.plan(item, PlFormat.todayEpochDay(), settings)
        deliverOrSchedule(slots, settings)
    }

    suspend fun cancelItem(itemId: Long) {
        cancelItemAlarms(itemId)
    }

    suspend fun cancelAll() {
        cancelAllAlarms()
        notification.cancelAll()
    }

    /**
     * Nadrobienie przypomnień po ponownym otwarciu aplikacji / restarcie telefonu.
     * Wołane też przez `ReminderReconcileWorker`.
     */
    suspend fun catchUp() {
        val settings = settingsRepository.settings.first()
        if (!settings.reminderEnabled) return
        val today = PlFormat.todayEpochDay()
        val items = itemDao.getAll().map { it.toDomain() }
        val slots = ReminderPlanner.planAll(items, today, settings)
        val sent = reminderLogDao.allKeys().toSet()
        val missed = slots.filter { it.key !in sent && it.isPast(today) }
        if (missed.isNotEmpty()) publishMissed(missed)
    }

    private suspend fun deliverOrSchedule(slots: List<ReminderSlot>, settings: AppSettings) {
        if (slots.isEmpty()) return
        val sent = reminderLogDao.allKeys().toSet()
        val now = System.currentTimeMillis()
        val missed = mutableListOf<ReminderSlot>()

        slots.forEach { slot ->
            if (slot.key in sent) return@forEach
            val trigger = triggerMillis(slot, settings)
            if (trigger <= now) {
                missed += slot
            } else {
                scheduleAlarm(slot, trigger)
            }
        }
        if (missed.isNotEmpty()) publishMissed(missed)
    }

    private suspend fun publishMissed(missed: List<ReminderSlot>) {
        if (missed.size == 1) {
            val slot = missed.first()
            notification.publish(slot, slot.key.hashCode())
        } else {
            // Wiele zaległych przypomnień łączymy w jedno powiadomienie zbiorcze.
            notification.publishSummary(
                count = missed.size,
                names = missed.map { it.itemName }.distinct(),
                notificationId = SUMMARY_NOTIFICATION_ID,
            )
        }
        val now = System.currentTimeMillis()
        missed.forEach { slot ->
            reminderLogDao.insert(
                ReminderLogEntity(
                    reminderKey = slot.key,
                    itemId = slot.itemId,
                    scheduledFireEpochDay = slot.fireEpochDay,
                    sentAt = now,
                    kind = slot.kind.name,
                ),
            )
        }
    }

    private fun triggerMillis(slot: ReminderSlot, settings: AppSettings): Long {
        val zone = PlFormat.zone()
        val dateTime = LocalDate.ofEpochDay(slot.fireEpochDay)
            .atTime(settings.reminderHourOfDay, settings.reminderMinute)
        val millis = dateTime.atZone(zone).toInstant().toEpochMilli()
        // Zmiana strefy czasowej mogła przesunąć wyznaczoną chwilę – nie planujemy w przeszłości.
        return if (millis <= System.currentTimeMillis()) System.currentTimeMillis() + FALLBACK_DELAY_MS else millis
    }

    private suspend fun scheduleAlarm(slot: ReminderSlot, triggerAtMillis: Long) {
        val manager = alarmManager ?: return
        val requestCode = slot.key.hashCode()
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_KEY, slot.key)
            putExtra(ReminderAlarmReceiver.EXTRA_ITEM_ID, slot.itemId)
            putExtra(ReminderAlarmReceiver.EXTRA_EXPIRY_EPOCH_DAY, slot.expiryEpochDay)
            putExtra(ReminderAlarmReceiver.EXTRA_KIND, slot.kind.name)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (PermissionState.canScheduleExactAlarms(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            // Bez zgody na dokładne alarmy: okno 15 minut, bez obietnicy punktualności.
            manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, INEXACT_WINDOW_MS, pending)
        }
        // Zapis w bazie pozwala anulować dokładnie ten alarm po edycji lub usunięciu pozycji,
        // także po restarcie telefonu (PendingIntent nie przeżywa procesu bez tej wiedzy).
        persistScheduled(slot, triggerAtMillis)
    }

    private suspend fun cancelItemAlarms(itemId: Long) {
        alarmDao.getForItem(itemId).forEach { stored -> cancelPending(stored.reminderKey) }
        alarmDao.deleteForItem(itemId)
    }

    private suspend fun cancelAllAlarms() {
        alarmDao.getAll().forEach { stored -> cancelPending(stored.reminderKey) }
        alarmDao.deleteAll()
    }

    private fun cancelPending(key: String) {
        val manager = alarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    /** Zapisuje zaplanowany alarm, żeby można go było później anulować (także po restarcie). */
    suspend fun persistScheduled(slot: ReminderSlot, triggerAtMillis: Long) {
        alarmDao.upsert(
            ScheduledAlarmEntity(
                reminderKey = slot.key,
                itemId = slot.itemId,
                requestCode = slot.key.hashCode(),
                fireAtMillis = triggerAtMillis,
            ),
        )
    }

    suspend fun markSent(slot: ReminderSlot) {
        reminderLogDao.insert(
            ReminderLogEntity(
                reminderKey = slot.key,
                itemId = slot.itemId,
                scheduledFireEpochDay = slot.fireEpochDay,
                sentAt = System.currentTimeMillis(),
                kind = slot.kind.name,
            ),
        )
    }

    suspend fun removeScheduled(key: String) = alarmDao.delete(key)

    companion object {
        const val SUMMARY_NOTIFICATION_ID = 900001
        private const val INEXACT_WINDOW_MS = 15L * 60 * 1000
        private const val FALLBACK_DELAY_MS = 5_000L
    }
}
