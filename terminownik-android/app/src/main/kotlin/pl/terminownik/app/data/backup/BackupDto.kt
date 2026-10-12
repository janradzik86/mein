package pl.terminownik.app.data.backup

import kotlinx.serialization.Serializable

/**
 * Schematy JSON kopii zapasowej.
 *
 * Wszystkie pola mają wartości domyślne, a deserializator ignoruje nieznane klucze,
 * dzięki czemu kopia z nowszej wersji aplikacji nie wysypuje importu, tylko traci
 * nieznane pola (i jest to zgłaszane użytkownikowi przez walidator manifestu).
 */
@Serializable
data class BackupManifestDto(
    val formatVersion: Int = 0,
    val app: String = "",
    val appVersionName: String = "",
    val createdAtEpochMillis: Long = 0L,
    val timezone: String? = null,
    val counts: Map<String, Int> = emptyMap(),
    val entries: List<BackupEntryDto> = emptyList(),
) {
    fun toData() = BackupManifestData(
        formatVersion = formatVersion,
        app = app,
        appVersionName = appVersionName,
        createdAtEpochMillis = createdAtEpochMillis,
        timezone = timezone,
        counts = counts,
        entries = entries.map { BackupEntryInfo(it.path, it.sha256, it.bytes) },
    )
}

@Serializable
data class BackupEntryDto(
    val path: String = "",
    val sha256: String = "",
    val bytes: Long = 0L,
)

@Serializable
data class BackupItemDto(
    val backupUid: String,
    val section: String = "PANTRY",
    val name: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val storageLocation: String? = null,
    val photoFile: String? = null,
    val batchCode: String? = null,
    val barcode: String? = null,
    val barcodeFormat: String? = null,
    val expiryEpochDay: Long? = null,
    val expiryDayPrecision: String? = null,
    val expiryDateType: String? = null,
    val expiryDateSource: String? = null,
    val expiryConfirmed: Boolean = true,
    val note: String? = null,
    val status: String = "ACTIVE",
    val reminderEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val statusChangedAt: Long? = null,
)

@Serializable
data class BackupBarcodeNameDto(
    val barcode: String,
    val name: String,
    val unit: String? = null,
    val section: String = "PANTRY",
    val updatedAt: Long = 0L,
    val useCount: Int = 1,
)

@Serializable
data class BackupShoppingDto(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val sectionHint: String? = null,
    val done: Boolean = false,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupSettingsDto(
    val themeMode: String = "SYSTEM",
    val reminderEnabled: Boolean = true,
    val reminderLeadDays: Int = 7,
    val reminderHourOfDay: Int = 9,
    val reminderMinute: Int = 0,
    val remindOnDueDay: Boolean = true,
    val showArchived: Boolean = false,
)

@Serializable
data class BackupReminderLogDto(
    val reminderKey: String,
    val itemIdBackupUid: String? = null,
    val scheduledFireEpochDay: Long = 0L,
    val sentAt: Long = 0L,
    val kind: String = "LEAD",
)
