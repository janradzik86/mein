package pl.terminownik.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Kanały powiadomień (wymagane od Androida 8).
 *
 * Jeden kanał dla przypomnień o terminach; użytkownik może zmienić jego ważność
 * w ustawieniach systemu, co nie psuje logiki aplikacji.
 */
object NotificationChannels {

    const val REMINDERS = "terminownik_przypomnienia"

    fun create(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            REMINDERS,
            context.getString(pl.terminownik.app.R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(pl.terminownik.app.R.string.channel_reminders_description)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
