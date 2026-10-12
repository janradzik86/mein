package pl.terminownik.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pl.terminownik.app.MainActivity
import pl.terminownik.app.R
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.ReminderKind
import pl.terminownik.app.domain.ReminderSlot
import pl.terminownik.app.domain.Section

/**
 * Budowanie i publikowanie powiadomienia o terminie.
 *
 * Nie obiecujemy dostarczenia co do sekundy: to ograniczenie systemu (Doze, ograniczenia
 * pracy w tle), dlatego przypomnienia planujemy na wybraną godzinę, a aplikacja nadrabia
 * zaległości przy najbliższym uruchomieniu.
 */
class ReminderNotification(private val context: Context) {

    fun publish(slot: ReminderSlot, notificationId: Int) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ITEM_ID, slot.itemId)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val expiryText = PlFormat.formatDate(slot.expiryEpochDay)
        val typeText = when (slot.dateType) {
            DateType.USE_BY -> context.getString(R.string.date_type_use_by)
            DateType.BEST_BEFORE -> context.getString(R.string.date_type_best_before)
            DateType.UNSPECIFIED -> context.getString(R.string.date_type_unspecified)
        }
        val sectionText = when (slot.section) {
            Section.PANTRY -> context.getString(R.string.section_pantry)
            Section.MEDICINE -> context.getString(R.string.section_medicine)
        }
        val title = if (slot.kind == ReminderKind.DUE) {
            context.getString(R.string.notification_title_due)
        } else {
            context.getString(R.string.notification_title_lead)
        }
        val body = context.getString(R.string.notification_body, slot.itemName, expiryText, typeText, sectionText)

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // Brak uprawnień = ciche pominięcie; nie rzucamy wyjątkiem (Android 13+).
        if (PermissionState.notificationsGranted(context)) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /**
     * Jedno powiadomienie zbiorcze, gdy do nadrobienia jest więcej przypomnień
     * (np. telefon był wyłączony przez tydzień). Chroni to użytkownika przed lawiną powiadomień.
     */
    fun publishSummary(count: Int, names: List<String>, notificationId: Int) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DEADLINES, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(R.string.notification_summary_title, count)
        val body = names.take(4).joinToString(", ").ifBlank {
            context.getString(R.string.notification_summary_body_generic)
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        if (PermissionState.notificationsGranted(context)) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
