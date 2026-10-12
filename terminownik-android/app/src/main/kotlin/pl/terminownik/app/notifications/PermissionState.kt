package pl.terminownik.app.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Stan uprawnień potrzebnych do przypomnień. */
enum class ExactAlarmState { GRANTED, DENIED, NOT_APPLICABLE }

object PermissionState {

    fun notificationsGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * Android 12+ (API 31) wymaga specjalnego dostępu do dokładnych alarmów.
     * Na niższych wersjach dokładne alarmy są dostępne bez dodatkowej zgody.
     */
    fun exactAlarmState(context: Context): ExactAlarmState =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(AlarmManager::class.java)
            if (manager?.canScheduleExactAlarms() == true) ExactAlarmState.GRANTED else ExactAlarmState.DENIED
        } else {
            ExactAlarmState.NOT_APPLICABLE
        }

    fun canScheduleExactAlarms(context: Context): Boolean =
        exactAlarmState(context) != ExactAlarmState.DENIED

    /** Intent do systemowych ustawień dokładnych alarmów (nie nadajemy ich sobie sami). */
    fun exactAlarmSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
    )
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
