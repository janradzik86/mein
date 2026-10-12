package pl.terminownik.app.domain

/**
 * Eksport CSV (specyfikacja, funkcja 12).
 *
 * Format dopasowany do polskiego Excela: separator średnik, CRLF, BOM UTF-8,
 * daty jako dd.MM.yyyy, liczby z przecinkiem dziesiętnym.
 *
 * Nagłówki przekazywane są z warstwy UI (zasoby stringów), dzięki czemu ta klasa
 * pozostaje czysto JVM-owa i testowalna.
 */
object CsvExport {

    const val SEPARATOR: Char = ';'
    const val BOM: String = "\uFEFF"
    private const val EOL = "\r\n"

    /** Etykiety kolumn – UI podstawia tu teksty z zasobów. */
    data class ItemColumns(
        val section: String,
        val name: String,
        val quantity: String,
        val unit: String,
        val location: String,
        val batch: String,
        val barcode: String,
        val expiry: String,
        val dateType: String,
        val dateSource: String,
        val confirmed: String,
        val status: String,
        val note: String,
        val createdAt: String,
        val updatedAt: String,
    )

    data class Labels(
        val pantry: String,
        val medicine: String,
        val useBy: String,
        val bestBefore: String,
        val unspecified: String,
        val sourceManual: String,
        val sourceGs1: String,
        val sourceOcr: String,
        val sourceImport: String,
        val statusActive: String,
        val statusUsed: String,
        val statusDiscarded: String,
        val yes: String,
        val no: String,
    )

    fun escape(value: String?): String {
        if (value == null) return ""
        val needsQuotes = value.contains(SEPARATOR) || value.contains('"') ||
            value.contains('\n') || value.contains('\r') || value.contains(EOL)
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun exportItems(
        items: List<PantryItem>,
        columns: ItemColumns,
        labels: Labels,
    ): String = buildString {
        append(BOM)
        append(
            listOf(
                columns.section, columns.name, columns.quantity, columns.unit, columns.location,
                columns.batch, columns.barcode, columns.expiry, columns.dateType, columns.dateSource,
                columns.confirmed, columns.status, columns.note, columns.createdAt, columns.updatedAt,
            ).joinToString(SEPARATOR.toString()) { escape(it) },
        )
        append(EOL)
        items.forEach { item ->
            val expiry = item.expiryDate
            append(
                listOf(
                    sectionLabel(item.section, labels),
                    item.name,
                    // W CSV liczby zapisujemy z przecinkiem (polski Excel), ale bez jednostki.
                    PlFormat.formatQuantity(item.quantity),
                    item.unit.orEmpty(),
                    item.storageLocation.orEmpty(),
                    item.batchCode.orEmpty(),
                    item.barcode.orEmpty(),
                    if (expiry == null) "" else PlFormat.formatExpiry(expiry),
                    dateTypeLabel(expiry?.type, labels),
                    dateSourceLabel(expiry?.source, labels),
                    when {
                        expiry == null -> ""
                        expiry.confirmed && expiry.dayPrecision == DayPrecision.EXACT -> labels.yes
                        else -> labels.no
                    },
                    statusLabel(item.status, labels),
                    item.note.orEmpty(),
                    if (item.createdAt == 0L) "" else PlFormat.formatDateTime(item.createdAt),
                    if (item.updatedAt == 0L) "" else PlFormat.formatDateTime(item.updatedAt),
                ).joinToString(SEPARATOR.toString()) { escape(it) },
            )
            append(EOL)
        }
    }

    fun exportShoppingList(
        items: List<ShoppingItem>,
        headerName: String,
        headerQuantity: String,
        headerUnit: String,
        headerSection: String,
        headerDone: String,
        headerNote: String,
        labels: Labels,
    ): String = buildString {
        append(BOM)
        append(
            listOf(headerName, headerQuantity, headerUnit, headerSection, headerDone, headerNote)
                .joinToString(SEPARATOR.toString()) { escape(it) },
        )
        append(EOL)
        items.forEach { item ->
            append(
                listOf(
                    item.name,
                    PlFormat.formatQuantity(item.quantity),
                    item.unit.orEmpty(),
                    item.sectionHint?.let { sectionLabel(it, labels) } ?: "",
                    if (item.done) labels.yes else labels.no,
                    item.note.orEmpty(),
                ).joinToString(SEPARATOR.toString()) { escape(it) },
            )
            append(EOL)
        }
    }

    fun sectionLabel(section: Section, labels: Labels): String = when (section) {
        Section.PANTRY -> labels.pantry
        Section.MEDICINE -> labels.medicine
    }

    fun dateTypeLabel(type: DateType?, labels: Labels): String = when (type) {
        DateType.USE_BY -> labels.useBy
        DateType.BEST_BEFORE -> labels.bestBefore
        DateType.UNSPECIFIED, null -> labels.unspecified
    }

    fun dateSourceLabel(source: DateSource?, labels: Labels): String = when (source) {
        DateSource.MANUAL -> labels.sourceManual
        DateSource.GS1_DATAMATRIX -> labels.sourceGs1
        DateSource.OCR -> labels.sourceOcr
        DateSource.BACKUP_IMPORT -> labels.sourceImport
        null -> ""
    }

    fun statusLabel(status: ItemStatus, labels: Labels): String = when (status) {
        ItemStatus.ACTIVE -> labels.statusActive
        ItemStatus.USED -> labels.statusUsed
        ItemStatus.DISCARDED -> labels.statusDiscarded
    }
}
