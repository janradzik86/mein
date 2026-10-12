package pl.terminownik.app.data.backup

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pl.terminownik.app.BuildConfig
import pl.terminownik.app.data.local.BarcodeNameEntity
import pl.terminownik.app.data.local.ItemEntity
import pl.terminownik.app.data.local.ReminderLogEntity
import pl.terminownik.app.data.local.ShoppingItemEntity
import pl.terminownik.app.data.local.TerminownikDatabase
import pl.terminownik.app.data.local.toDomain
import pl.terminownik.app.data.photo.PhotoStorage
import pl.terminownik.app.data.settings.SettingsRepository
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.DayPrecision
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.PantryItem
import pl.terminownik.app.domain.PlFormat
import pl.terminownik.app.domain.Section
import pl.terminownik.app.domain.ThemeMode
import pl.terminownik.app.notifications.ReminderScheduler

/** Podsumowanie eksportu pokazywane użytkownikowi. */
data class ExportSummary(
    val items: Int,
    val photos: Int,
    val barcodeNames: Int,
    val shoppingItems: Int,
    val bytes: Long,
)

/** Podsumowanie importu: ile odtworzono i ile rekordów odrzucono jako nieprawidłowe. */
data class RestoreSummary(
    val items: Int,
    val photos: Int,
    val barcodeNames: Int,
    val shoppingItems: Int,
    val skippedItems: Int,
    val warnings: List<String>,
)

enum class RestoreMode {
    /** Usuwa bieżące dane i wstawia dane z kopii (domyślne). */
    REPLACE_ALL,

    /** Dokłada dane z kopii, pomijając pozycje już obecne (po `backupUid`). */
    MERGE,
}

/** Wynik operacji na kopii: sukces albo kod błędu do przetłumaczenia w UI. */
sealed class BackupResult<out T> {
    data class Success<T>(val value: T) : BackupResult<T>()
    data class Failure(val code: String, val details: String) : BackupResult<Nothing>()
}

/**
 * Eksport i import pełnej kopii zapasowej (specyfikacja: wersjonowany format, walidacja,
 * załączniki i bezpieczne odtwarzanie).
 *
 * Plik wejściowy jest traktowany jako NIEZAUFANY: kontrolujemy ścieżki wpisów, liczbę
 * wpisów, rozmiary i współczynnik kompresji, weryfikujemy sumy SHA-256, sanityzujemy
 * tekst i liczby, a odtworzenie wykonujemy w jednej transakcji bazy.
 */
class BackupRepository(
    private val context: Context,
    private val database: TerminownikDatabase,
    private val settingsRepository: SettingsRepository,
    private val photoStorage: PhotoStorage,
    private val reminderScheduler: ReminderScheduler,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val itemDao = database.itemDao()
    private val barcodeDao = database.barcodeNameDao()
    private val shoppingDao = database.shoppingDao()
    private val reminderLogDao = database.reminderLogDao()

    // -----------------------------------------------------------------------------------------
    // EKSPORT
    // -----------------------------------------------------------------------------------------

    suspend fun export(targetUri: Uri): BackupResult<ExportSummary> = runCatching {
        val items = itemDao.getAll().map { it.toDomain() }
        val barcodeNames = barcodeDao.getAll()
        val shopping = shoppingDao.getAll()
        val settings = settingsRepository.settings.first()
        val reminderLog = reminderLogDao.getAll()

        val itemsJson = json.encodeToString(
            ListSerializer(BackupItemDto.serializer()),
            items.map { it.toDto() },
        )
        val barcodeJson = json.encodeToString(
            ListSerializer(BackupBarcodeNameDto.serializer()),
            barcodeNames.map {
                BackupBarcodeNameDto(
                    barcode = it.barcode,
                    name = it.name,
                    unit = it.unit,
                    section = it.section,
                    updatedAt = it.updatedAt,
                    useCount = it.useCount,
                )
            },
        )
        val shoppingJson = json.encodeToString(
            ListSerializer(BackupShoppingDto.serializer()),
            shopping.map {
                BackupShoppingDto(
                    name = it.name,
                    quantity = it.quantity,
                    unit = it.unit,
                    sectionHint = it.sectionHint,
                    done = it.done,
                    note = it.note,
                    createdAt = it.createdAt,
                )
            },
        )
        val settingsJson = json.encodeToString(
            BackupSettingsDto.serializer(),
            BackupSettingsDto(
                themeMode = settings.themeMode.name,
                reminderEnabled = settings.reminderEnabled,
                reminderLeadDays = settings.reminderLeadDays,
                reminderHourOfDay = settings.reminderHourOfDay,
                reminderMinute = settings.reminderMinute,
                remindOnDueDay = settings.remindOnDueDay,
                showArchived = settings.showArchived,
            ),
        )
        val uidByItemId = items.associate { it.id to (it.backupUid ?: UUID.randomUUID().toString()) }
        val reminderJson = json.encodeToString(
            ListSerializer(BackupReminderLogDto.serializer()),
            reminderLog.map {
                BackupReminderLogDto(
                    reminderKey = it.reminderKey,
                    itemIdBackupUid = uidByItemId[it.itemId],
                    scheduledFireEpochDay = it.scheduledFireEpochDay,
                    sentAt = it.sentAt,
                    kind = it.kind,
                )
            },
        )

        // Zdjęcia: tylko te, które faktycznie istnieją i są przypisane do pozycji.
        val photoEntries = LinkedHashMap<String, ByteArray>()
        items.forEach { item ->
            val fileName = item.photoPath ?: return@forEach
            val file = photoStorage.fileFor(fileName) ?: return@forEach
            if (file.length() > BackupContract.MAX_PHOTO_ENTRY_BYTES) return@forEach
            val photoName = "${uidByItemId[item.id]}.jpg"
            photoEntries["${BackupContract.PHOTOS_DIR}/$photoName"] = file.readBytes()
        }

        val payloads = linkedMapOf(
            BackupContract.ITEMS_PATH to itemsJson.toByteArray(),
            BackupContract.BARCODE_NAMES_PATH to barcodeJson.toByteArray(),
            BackupContract.SHOPPING_PATH to shoppingJson.toByteArray(),
            BackupContract.SETTINGS_PATH to settingsJson.toByteArray(),
            BackupContract.REMINDER_LOG_PATH to reminderJson.toByteArray(),
        )

        val entries = mutableListOf<BackupEntryInfo>()
        payloads.forEach { (path, bytes) ->
            entries += BackupEntryInfo(path, BackupValidator.sha256Hex(bytes), bytes.size.toLong())
        }
        photoEntries.forEach { (path, bytes) ->
            entries += BackupEntryInfo(path, BackupValidator.sha256Hex(bytes), bytes.size.toLong())
        }

        val manifest = BackupManifestDto(
            formatVersion = BackupContract.FORMAT_VERSION,
            app = BackupContract.APP_ID,
            appVersionName = BuildConfig.VERSION_NAME,
            createdAtEpochMillis = System.currentTimeMillis(),
            timezone = java.util.TimeZone.getDefault().id,
            counts = mapOf(
                "items" to items.size,
                "barcodeNames" to barcodeNames.size,
                "shopping" to shopping.size,
                "reminderLog" to reminderLog.size,
                "photos" to photoEntries.size,
            ),
            entries = entries.map { BackupEntryDto(it.path, it.sha256, it.bytes) },
        )
        val manifestBytes = json.encodeToString(BackupManifestDto.serializer(), manifest).toByteArray()

        var written = 0L
        context.contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                writeEntry(zip, BackupContract.MANIFEST_PATH, manifestBytes)
                written += manifestBytes.size
                payloads.forEach { (path, bytes) ->
                    writeEntry(zip, path, bytes)
                    written += bytes.size
                }
                photoEntries.forEach { (path, bytes) ->
                    writeEntry(zip, path, bytes)
                    written += bytes.size
                }
            }
        } ?: return@runCatching BackupResult.Failure(
            BackupContract.Error.IO_ERROR,
            "Nie udało się otworzyć docelowego pliku do zapisu.",
        )

        BackupResult.Success(
            ExportSummary(
                items = items.size,
                photos = photoEntries.size,
                barcodeNames = barcodeNames.size,
                shoppingItems = shopping.size,
                bytes = written,
            ),
        ) as BackupResult<ExportSummary>
    }.getOrElse { t ->
        BackupResult.Failure(BackupContract.Error.IO_ERROR, t.message ?: t.javaClass.simpleName)
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    // -----------------------------------------------------------------------------------------
    // IMPORT
    // -----------------------------------------------------------------------------------------

    /**
     * Czyta archiwum z niezaufanego źródła i odtwarza dane.
     *
     * Przebieg:
     *  1. kontrola ścieżek/limitów podczas czytania strumienia (zip-slip, zip-bomb),
     *  2. walidacja manifestu i sum SHA-256,
     *  3. sanityzacja rekordów (tekst, liczby, zakres dat),
     *  4. zapis w jednej transakcji, zdjęcia dopiero po udanym zapisie,
     *  5. przeliczenie przypomnień.
     */
    suspend fun restore(sourceUri: Uri, mode: RestoreMode): BackupResult<RestoreSummary> = runCatching {
        val jsonPayloads = HashMap<String, ByteArray>()
        val photoTempDir = File(context.cacheDir, "restore-${System.currentTimeMillis()}")
        photoTempDir.mkdirs()
        val photoChecksums = HashMap<String, String>()

        var entryCount = 0
        var totalUncompressed = 0L

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        entryCount++
                        if (entryCount > BackupContract.MAX_ENTRIES) {
                            return@runCatching BackupResult.Failure(
                                BackupContract.Error.TOO_MANY_ENTRIES,
                                "Za dużo wpisów w archiwum (limit ${BackupContract.MAX_ENTRIES}).",
                            )
                        }
                        val name = entry.name
                        if (!entry.isDirectory && !BackupValidator.isSafeEntryPath(name)) {
                            return@runCatching BackupResult.Failure(
                                BackupContract.Error.UNSAFE_PATH,
                                "Niebezpieczna ścieżka w archiwum: ${name.take(120)}",
                            )
                        }
                        if (!entry.isDirectory) {
                            val limit = if (name.endsWith(".json")) {
                                BackupContract.MAX_JSON_ENTRY_BYTES
                            } else {
                                BackupContract.MAX_PHOTO_ENTRY_BYTES
                            }
                            val bytes = readAtMost(zip, limit)
                            if (bytes == null) {
                                return@runCatching BackupResult.Failure(
                                    BackupContract.Error.ENTRY_TOO_LARGE,
                                    "Wpis $name przekracza limit $limit B.",
                                )
                            }
                            totalUncompressed += bytes.size
                            if (totalUncompressed > BackupContract.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                return@runCatching BackupResult.Failure(
                                    BackupContract.Error.ARCHIVE_TOO_LARGE,
                                    "Archiwum po rozpakowaniu jest za duże.",
                                )
                            }
                            if (name.endsWith(".json")) {
                                jsonPayloads[name] = bytes
                            } else {
                                val fileName = name.substringAfterLast('/')
                                if (!BackupValidator.isSafePhotoFileName(fileName)) {
                                    return@runCatching BackupResult.Failure(
                                        BackupContract.Error.UNSAFE_PATH,
                                        "Nieprawidłowa nazwa zdjęcia: ${fileName.take(60)}",
                                    )
                                }
                                File(photoTempDir, fileName).writeBytes(bytes)
                                photoChecksums["${BackupContract.PHOTOS_DIR}/$fileName"] =
                                    BackupValidator.sha256Hex(bytes)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@runCatching BackupResult.Failure(
                BackupContract.Error.IO_ERROR,
                "Nie udało się otworzyć pliku kopii.",
            )

            val manifestBytes = jsonPayloads[BackupContract.MANIFEST_PATH]
                ?: return@runCatching BackupResult.Failure(
                    BackupContract.Error.MISSING_MANIFEST,
                    "Archiwum nie zawiera manifest.json.",
                )

            val manifestDto = runCatching {
                json.decodeFromString(BackupManifestDto.serializer(), manifestBytes.decodeToString())
            }.getOrElse {
                return@runCatching BackupResult.Failure(
                    BackupContract.Error.CORRUPTED_JSON,
                    "manifest.json jest nieczytelny.",
                )
            }
            val manifest = manifestDto.toData()

            when (val validation = BackupValidator.validateManifest(manifest)) {
                is BackupValidation.Invalid ->
                    return@runCatching BackupResult.Failure(validation.code, validation.details)

                is BackupValidation.Ok -> Unit
            }

            // Weryfikacja sum kontrolnych wpisów zadeklarowanych w manifeście.
            val actualChecksums = HashMap<String, String>()
            jsonPayloads.forEach { (path, bytes) -> actualChecksums[path] = BackupValidator.sha256Hex(bytes) }
            actualChecksums.putAll(photoChecksums)
            manifest.entries.forEach { declared ->
                val actual = actualChecksums[declared.path]
                if (actual != null && !actual.equals(declared.sha256, ignoreCase = true)) {
                    return@runCatching BackupResult.Failure(
                        BackupContract.Error.CHECKSUM_MISMATCH,
                        "Suma kontrolna ${declared.path} nie zgadza się – plik jest uszkodzony.",
                    )
                }
            }

            val warnings = mutableListOf<String>()
            (BackupValidator.validateManifest(manifest) as? BackupValidation.Ok)?.warnings?.let {
                warnings += it
            }

            val itemDtos = decodeList(jsonPayloads[BackupContract.ITEMS_PATH], BackupItemDto.serializer())
                ?: return@runCatching BackupResult.Failure(
                    BackupContract.Error.CORRUPTED_JSON,
                    "items.json jest nieczytelny.",
                )
            if (itemDtos.size > BackupContract.MAX_ITEMS) {
                return@runCatching BackupResult.Failure(
                    BackupContract.Error.TOO_MANY_ITEMS,
                    "Kopia zawiera ${itemDtos.size} pozycji (limit ${BackupContract.MAX_ITEMS}).",
                )
            }
            val barcodeDtos = decodeList(
                jsonPayloads[BackupContract.BARCODE_NAMES_PATH],
                BackupBarcodeNameDto.serializer(),
            ) ?: emptyList()
            val shoppingDtos = decodeList(
                jsonPayloads[BackupContract.SHOPPING_PATH],
                BackupShoppingDto.serializer(),
            ) ?: emptyList()
            val reminderDtos = decodeList(
                jsonPayloads[BackupContract.REMINDER_LOG_PATH],
                BackupReminderLogDto.serializer(),
            ) ?: emptyList()
            // Jeśli plik istniał, ale nie dał się odczytać, mówimy o tym użytkownikowi.
            if (jsonPayloads.containsKey(BackupContract.BARCODE_NAMES_PATH) &&
                decodeList(jsonPayloads[BackupContract.BARCODE_NAMES_PATH], BackupBarcodeNameDto.serializer()) == null
            ) {
                warnings += "CORRUPTED_SECTION:barcode_names"
            }
            if (jsonPayloads.containsKey(BackupContract.SHOPPING_PATH) &&
                decodeList(jsonPayloads[BackupContract.SHOPPING_PATH], BackupShoppingDto.serializer()) == null
            ) {
                warnings += "CORRUPTED_SECTION:shopping"
            }
            val settingsDto = jsonPayloads[BackupContract.SETTINGS_PATH]?.let { bytes ->
                runCatching {
                    json.decodeFromString(BackupSettingsDto.serializer(), bytes.decodeToString())
                }.getOrNull()
            }

            // Sanityzacja rekordów i mapowanie zdjęć.
            val today = PlFormat.todayEpochDay()
            val cleanedItems = mutableListOf<ItemEntity>()
            var skipped = 0
            itemDtos.forEach { dto ->
                val name = BackupValidator.sanitizeText(dto.name, 200)
                if (name == null) {
                    skipped++
                    return@forEach
                }
                val uid = BackupValidator.sanitizeText(dto.backupUid, 64) ?: UUID.randomUUID().toString()
                val expiryDay = dto.expiryEpochDay?.takeIf { BackupValidator.isPlausibleEpochDay(it, today) }
                if (dto.expiryEpochDay != null && expiryDay == null) {
                    warnings += "DATE_OUT_OF_RANGE"
                }
                val photoName = "$uid.jpg"
                val hasPhoto = dto.photoFile != null && File(photoTempDir, photoName).exists()
                cleanedItems += ItemEntity(
                    id = 0L,
                    section = Section.fromRaw(dto.section).name,
                    name = name,
                    quantity = BackupValidator.sanitizeQuantity(dto.quantity),
                    unit = BackupValidator.sanitizeText(dto.unit, 40),
                    storageLocation = BackupValidator.sanitizeText(dto.storageLocation, 120),
                    photoPath = if (hasPhoto) photoName else null,
                    batchCode = BackupValidator.sanitizeText(dto.batchCode, 64),
                    barcode = dto.barcode?.filter { it.isDigit() }?.takeIf { it.length in 8..14 },
                    barcodeFormat = BackupValidator.sanitizeText(dto.barcodeFormat, 32),
                    expiryEpochDay = expiryDay,
                    expiryDayPrecision = DayPrecision.entries
                        .firstOrNull { it.name == dto.expiryDayPrecision }?.name,
                    expiryDateType = dto.expiryDateType?.takeIf { raw ->
                        DateType.entries.any { it.name == raw }
                    },
                    // Data z kopii pozostaje niepotwierdzona, jeśli nie była potwierdzona u źródła.
                    expiryDateSource = DateSource.entries
                        .firstOrNull { it.name == dto.expiryDateSource }?.name ?: DateSource.BACKUP_IMPORT.name,
                    expiryConfirmed = dto.expiryConfirmed,
                    note = BackupValidator.sanitizeText(dto.note, 1000),
                    status = ItemStatus.fromRaw(dto.status).name,
                    reminderEnabled = dto.reminderEnabled,
                    createdAt = dto.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    statusChangedAt = dto.statusChangedAt,
                    backupUid = uid,
                )
            }

            // Zapis w jednej transakcji.
            val uidToNewId = HashMap<String, Long>()
            val restoredPhotos = mutableListOf<Pair<String, String>>()
            database.withTransaction {
                if (mode == RestoreMode.REPLACE_ALL) {
                    itemDao.deleteAll()
                    barcodeDao.deleteAll()
                    shoppingDao.deleteAll()
                    reminderLogDao.deleteAll()
                    database.scheduledAlarmDao().deleteAll()
                }
                cleanedItems.forEach { entity ->
                    val uid = entity.backupUid!!
                    if (mode == RestoreMode.MERGE) {
                        val existing = itemDao.findByBackupUid(uid)
                        if (existing != null) {
                            uidToNewId[uid] = existing.id
                            return@forEach
                        }
                    }
                    val newId = itemDao.insert(entity)
                    uidToNewId[uid] = newId
                    entity.photoPath?.let { restoredPhotos += uid to it }
                }
                barcodeDao.insertAll(
                    barcodeDtos.mapNotNull { dto ->
                        val code = dto.barcode.filter { it.isDigit() }
                        val name = BackupValidator.sanitizeText(dto.name, 200)
                        if (code.length !in 8..14 || name == null) return@mapNotNull null
                        BarcodeNameEntity(
                            barcode = code,
                            name = name,
                            unit = BackupValidator.sanitizeText(dto.unit, 40),
                            section = Section.fromRaw(dto.section).name,
                            updatedAt = dto.updatedAt,
                            useCount = dto.useCount.coerceAtLeast(1),
                        )
                    },
                )
                shoppingDao.insertAll(
                    shoppingDtos.mapNotNull { dto ->
                        val name = BackupValidator.sanitizeText(dto.name, 200) ?: return@mapNotNull null
                        ShoppingItemEntity(
                            name = name,
                            quantity = BackupValidator.sanitizeQuantity(dto.quantity),
                            unit = BackupValidator.sanitizeText(dto.unit, 40),
                            sectionHint = dto.sectionHint?.let { Section.fromRaw(it).name },
                            done = dto.done,
                            note = BackupValidator.sanitizeText(dto.note, 500),
                            createdAt = dto.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                            sourceItemId = null,
                        )
                    },
                )
                reminderLogDao.insertAll(
                    reminderDtos.mapNotNull { dto ->
                        val key = BackupValidator.sanitizeText(dto.reminderKey, 120) ?: return@mapNotNull null
                        val itemId = dto.itemIdBackupUid?.let { uidToNewId[it] } ?: return@mapNotNull null
                        ReminderLogEntity(
                            reminderKey = key,
                            itemId = itemId,
                            scheduledFireEpochDay = dto.scheduledFireEpochDay,
                            sentAt = dto.sentAt,
                            kind = dto.kind,
                        )
                    },
                )
            }

            // Ustawienia zapisujemy PO udanej transakcji bazy, żeby rollback bazy
            // nie zostawił ustawień z kopii przy starych danych.
            settingsDto?.let { dto ->
                settingsRepository.replaceAll(
                    AppSettings(
                        onboardingDone = true,
                        themeMode = runCatching { ThemeMode.valueOf(dto.themeMode) }
                            .getOrDefault(ThemeMode.SYSTEM),
                        reminderEnabled = dto.reminderEnabled,
                        reminderLeadDays = dto.reminderLeadDays.coerceIn(0, 365),
                        reminderHourOfDay = dto.reminderHourOfDay.coerceIn(0, 23),
                        reminderMinute = dto.reminderMinute.coerceIn(0, 59),
                        remindOnDueDay = dto.remindOnDueDay,
                        showArchived = dto.showArchived,
                    ),
                )
            }

            // Zdjęcia kopiujemy dopiero po udanej transakcji.
            if (mode == RestoreMode.REPLACE_ALL) photoStorage.deleteAll()
            var photosRestored = 0
            restoredPhotos.forEach { (uid, photoFileName) ->
                val temp = File(photoTempDir, "$uid.jpg")
                if (temp.exists()) {
                    val ok = photoStorage.writeBytes(photoFileName, temp.readBytes())
                    if (ok) photosRestored++ else warnings += "PHOTO_COPY_FAILED"
                }
            }

            reminderScheduler.cancelAll()
            reminderScheduler.rescheduleAll()

            BackupResult.Success(
                RestoreSummary(
                    items = cleanedItems.size,
                    photos = photosRestored,
                    barcodeNames = barcodeDtos.size,
                    shoppingItems = shoppingDtos.size,
                    skippedItems = skipped,
                    warnings = warnings.distinct(),
                ),
            ) as BackupResult<RestoreSummary>
        } finally {
            photoTempDir.deleteRecursively()
        }
    }.getOrElse { t ->
        BackupResult.Failure(BackupContract.Error.IO_ERROR, t.message ?: t.javaClass.simpleName)
    }

    /**
     * Czyta wpis z limitem bajtów. Zwraca null, gdy wpis przekracza limit.
     * `ZipEntry.size` bywa nieznany (-1) dla strumieni, dlatego liczymy faktycznie czytane bajty.
     */
    private fun readAtMost(zip: ZipInputStream, limit: Long): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = zip.read(chunk)
            if (read <= 0) break
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun <T> decodeList(bytes: ByteArray?, serializer: KSerializer<T>): List<T>? {
        if (bytes == null) return null
        return runCatching {
            json.decodeFromString(ListSerializer(serializer), bytes.decodeToString())
        }.getOrNull()
    }

    private fun PantryItem.toDto(): BackupItemDto = BackupItemDto(
        backupUid = backupUid ?: UUID.randomUUID().toString(),
        section = section.name,
        name = name,
        quantity = quantity,
        unit = unit,
        storageLocation = storageLocation,
        photoFile = photoPath,
        batchCode = batchCode,
        barcode = barcode,
        barcodeFormat = barcodeFormat,
        expiryEpochDay = expiryDate?.epochDay,
        expiryDayPrecision = expiryDate?.dayPrecision?.name,
        expiryDateType = expiryDate?.type?.name,
        expiryDateSource = expiryDate?.source?.name,
        expiryConfirmed = expiryDate?.confirmed ?: true,
        note = note,
        status = status.name,
        reminderEnabled = reminderEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        statusChangedAt = statusChangedAt,
    )

    /** Szybki podgląd zawartości kopii przed importem (bez zapisywania czegokolwiek). */
    suspend fun inspect(sourceUri: Uri): BackupResult<BackupManifestData> = runCatching {
        var manifestBytes: ByteArray? = null
        var entryCount = 0
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && manifestBytes == null) {
                    entryCount++
                    if (entryCount > BackupContract.MAX_ENTRIES) break
                    if (!entry.isDirectory && entry.name == BackupContract.MANIFEST_PATH) {
                        manifestBytes = readAtMost(zip, BackupContract.MAX_JSON_ENTRY_BYTES)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        val bytes = manifestBytes
            ?: return@runCatching BackupResult.Failure(
                BackupContract.Error.MISSING_MANIFEST,
                "Brak manifest.json.",
            )
        val dto = runCatching {
            json.decodeFromString(BackupManifestDto.serializer(), bytes.decodeToString())
        }.getOrElse {
            return@runCatching BackupResult.Failure(
                BackupContract.Error.CORRUPTED_JSON,
                "manifest.json jest nieczytelny.",
            )
        }
        val data = dto.toData()
        when (val validation = BackupValidator.validateManifest(data)) {
            is BackupValidation.Invalid -> BackupResult.Failure(validation.code, validation.details)
            is BackupValidation.Ok -> BackupResult.Success(data)
        }
    }.getOrElse { t ->
        BackupResult.Failure(BackupContract.Error.NOT_A_ZIP, t.message ?: "Nieprawidłowy plik ZIP")
    }
}
