package pl.terminownik.app.domain

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Polskie formatowanie dat, liczb i jednostek.
 *
 * Klasa jest czysto JVM-owa (java.time + java.text), dzięki czemu logika formatowania
 * jest testowalna bez Robolectric i bez emulatora.
 */
object PlFormat {

    /** Ustawienia regionalne wymuszamy jawnie – nie zależymy od domyślnej lokalizacji telefonu. */
    val LOCALE: Locale = Locale.forLanguageTag("pl-PL")

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", LOCALE)
    private val monthYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.yyyy", LOCALE)
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", LOCALE)
    private val fileStampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", LOCALE)

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun todayEpochDay(zone: ZoneId = zone()): Long = LocalDate.now(zone).toEpochDay()

    fun localDateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun formatDate(epochDay: Long): String = localDateOf(epochDay).format(dateFormatter)

    /** Data znana tylko co do miesiąca – pokazujemy „11.2026”, a nie „30.11.2026”. */
    fun formatMonthOnly(epochDay: Long): String = localDateOf(epochDay).format(monthYearFormatter)

    fun formatDateTime(epochMillis: Long, zone: ZoneId = zone()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTimeFormatter)

    fun formatDate(epochMillis: Long, zone: ZoneId = zone()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(dateFormatter)

    /**
     * Format daty zależny od precyzji: przy MONTH_ONLY nie udajemy, że znamy dokładny dzień.
     */
    fun formatExpiry(expiry: ExpiryDate): String = when (expiry.dayPrecision) {
        DayPrecision.MONTH_ONLY -> formatMonthOnly(expiry.epochDay) + " (koniec miesiąca)"
        DayPrecision.AMBIGUOUS_ORDER -> formatDate(expiry.epochDay) + " (do potwierdzenia)"
        DayPrecision.EXACT -> formatDate(expiry.epochDay)
    }

    /**
     * Format liczbowy z POLSKIM separatorem dziesiętnym ustawionym jawnie.
     *
     * Nie polegamy wyłącznie na danych lokalizacyjnych JVM/urządzenia: na okrojonych
     * runtime'ach (brak modułu jdk.localedata) `DecimalFormatSymbols(pl-PL)` potrafi
     * zwrócić separator kropkowy. Wymuszenie przecinka gwarantuje spójny zapis w UI
     * i w eksportowanym CSV.
     */
    private fun decimalFormat(maxFractionDigits: Int): DecimalFormat {
        val symbols = DecimalFormatSymbols(LOCALE).apply {
            decimalSeparator = ','
        }
        // Wzorzec używa '.' jako placeholdera separatora i nie grupuje tysięcy:
        // ilości w spisie są małe, a CSV musi być jednoznaczne dla Excela.
        val pattern = buildString {
            append('0')
            if (maxFractionDigits > 0) {
                append('.')
            }
            repeat(maxFractionDigits) { append('#') }
        }
        return DecimalFormat(pattern, symbols).apply {
            roundingMode = RoundingMode.HALF_UP
            isDecimalSeparatorAlwaysShown = false
            isGroupingUsed = false
        }
    }

    /** Ilość: 1,5 kg / 3 szt. / 250 ml – bez zbędnych zer, polski separator dziesiętny. */
    fun formatQuantity(quantity: Double?): String {
        if (quantity == null) return ""
        if (quantity.isNaN() || quantity.isInfinite()) return ""
        val digits = when {
            kotlin.math.abs(quantity - quantity.toLong()) < 1e-9 -> 0
            kotlin.math.abs(quantity * 10 - (quantity * 10).toLong()) < 1e-9 -> 1
            kotlin.math.abs(quantity * 100 - (quantity * 100).toLong()) < 1e-9 -> 2
            else -> 3
        }
        return decimalFormat(digits).format(quantity)
    }

    fun formatQuantityWithUnit(quantity: Double?, unit: String?): String {
        val q = formatQuantity(quantity)
        return when {
            q.isEmpty() && unit.isNullOrBlank() -> ""
            q.isEmpty() -> unit.orEmpty()
            unit.isNullOrBlank() -> q
            else -> "$q $unit"
        }
    }

    /**
     * Polska odmiana: „dziś”, „jutro”, „za 3 dni”, „wczoraj”, „4 dni temu”.
     * W języku polskim forma „dni” jest wspólna dla 2–4 i 5+, więc logika jest prosta,
     * ale „1 dzień” wymaga osobnej formy.
     */
    fun formatDayDistance(days: Long): String = when {
        days == 0L -> "dziś"
        days == 1L -> "jutro"
        days == -1L -> "wczoraj"
        days > 1L -> "za $days dni"
        days < -1L -> "${-days} dni temu"
        else -> "dziś"
    }

    fun formatCount(count: Int, singular: String, plural: String): String {
        val abs = kotlin.math.abs(count)
        val word = if (abs == 1) singular else plural
        return "$count $word"
    }

    /** Nazwy jednostek proponowane w formularzu (bez wymuszania – pole jest wolnym tekstem). */
    val SUGGESTED_UNITS: List<String> = listOf(
        "szt.", "opak.", "g", "kg", "ml", "l", "szklanka", "puszka", "butelka", "słoik", "tabletka", "kapsułka",
    )

    val SUGGESTED_LOCATIONS: List<String> = listOf(
        "Lodówka", "Zamrażarka", "Szafka kuchenna", "Spiżarnia", "Piwnica", "Apteczka", "Łazienka", "Pojazd",
    )

    /** Znacznik czasu do nazw plików, np. `20261012-1830`. */
    fun timestampForFile(zone: ZoneId = zone()): String =
        LocalDateTime.now(zone).format(fileStampFormatter)

    /** Rozmiar w jednostkach czytelnych dla użytkownika, z polskim separatorem dziesiętnym. */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("kB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return "${formatQuantity(value)} ${units[index]}"
    }

    /** Nazwa pliku kopii zapasowej, np. `terminownik-20261012-1830.zip`. */
    fun backupFileName(prefix: String): String {
        val slug = prefix.lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "kopia" }
        return "$slug-${timestampForFile()}.zip"
    }
}
