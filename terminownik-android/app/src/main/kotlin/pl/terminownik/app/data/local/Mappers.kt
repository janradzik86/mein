package pl.terminownik.app.data.local

import pl.terminownik.app.domain.BarcodeName
import pl.terminownik.app.domain.DateSource
import pl.terminownik.app.domain.DateType
import pl.terminownik.app.domain.DayPrecision
import pl.terminownik.app.domain.ExpiryDate
import pl.terminownik.app.domain.ItemStatus
import pl.terminownik.app.domain.PantryItem
import pl.terminownik.app.domain.Section
import pl.terminownik.app.domain.ShoppingItem

fun ItemEntity.toDomain(): PantryItem = PantryItem(
    id = id,
    section = Section.fromRaw(section),
    name = name,
    quantity = quantity,
    unit = unit,
    storageLocation = storageLocation,
    photoPath = photoPath,
    batchCode = batchCode,
    barcode = barcode,
    barcodeFormat = barcodeFormat,
    expiryDate = expiryEpochDay?.let {
        ExpiryDate(
            epochDay = it,
            dayPrecision = DayPrecision.entries.firstOrNull { p -> p.name == expiryDayPrecision }
                ?: DayPrecision.EXACT,
            type = DateType.fromRaw(expiryDateType),
            source = DateSource.fromRaw(expiryDateSource),
            confirmed = expiryConfirmed,
        )
    },
    note = note,
    status = ItemStatus.fromRaw(status),
    reminderEnabled = reminderEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    statusChangedAt = statusChangedAt,
    backupUid = backupUid,
)

fun PantryItem.toEntity(): ItemEntity = ItemEntity(
    id = id,
    section = section.name,
    name = name,
    quantity = quantity,
    unit = unit,
    storageLocation = storageLocation,
    photoPath = photoPath,
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
    backupUid = backupUid,
)

fun BarcodeNameEntity.toDomain(): BarcodeName = BarcodeName(
    barcode = barcode,
    name = name,
    unit = unit,
    section = Section.fromRaw(section),
    updatedAt = updatedAt,
    useCount = useCount,
)

fun BarcodeName.toEntity(): BarcodeNameEntity = BarcodeNameEntity(
    barcode = barcode,
    name = name,
    unit = unit,
    section = section.name,
    updatedAt = updatedAt,
    useCount = useCount,
)

fun ShoppingItemEntity.toDomain(): ShoppingItem = ShoppingItem(
    id = id,
    name = name,
    quantity = quantity,
    unit = unit,
    sectionHint = sectionHint?.let { Section.fromRaw(it) },
    done = done,
    note = note,
    createdAt = createdAt,
    sourceItemId = sourceItemId,
)

fun ShoppingItem.toEntity(): ShoppingItemEntity = ShoppingItemEntity(
    id = id,
    name = name,
    quantity = quantity,
    unit = unit,
    sectionHint = sectionHint?.name,
    done = done,
    note = note,
    createdAt = createdAt,
    sourceItemId = sourceItemId,
)
