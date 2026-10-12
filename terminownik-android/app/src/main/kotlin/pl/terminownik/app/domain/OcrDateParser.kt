package pl.terminownik.app.domain

import java.text.Normalizer
import java.time.LocalDate

enum class OcrConfidence { HIGH, MEDIUM, LOW }

/**
 * Kandydat na datę odczytaną z etykiety.
 *
 * Specyfikacja: „każda data odczytana automatycznie wymaga potwierdzenia” oraz
 * „błędny lub niejednoznaczny OCR ma prowadzić do poprawienia daty”.
 * Dlatego parser zawsze zwraca [issueCode] dla zapisów niejednoznacznych, a warstwa UI
 * ustawia `ExpiryDate.confirmed = false` dopóki użytkownik nie zatwierdzi daty.
 */
data class OcrDateCandidate(
    val epochDay: Long,
    val precision: DayPrecision,
    val type: DateType,
    val confidence: OcrConfidence,
    val sourceText: String,
    val matchedLabel: String?,
    val issueCode: String?,
)

data class OcrParseResult(
    val candidates: List<OcrDateCandidate>,
    val linesScanned: Int,
) {
    val best: OcrDateCandidate? get() = candidates.firstOrNull()
    val isEmpty: Boolean get() = candidates.isEmpty()
}

/** Kody problemów zwracane do UI (tłumaczone na komunikaty w zasobach). */
object OcrIssue {
    const val AMBIGUOUS_ORDER = "AMBIGUOUS_ORDER"
    const val ORDER_MMDD = "ORDER_MMDD"
    const val MONTH_ONLY = "MONTH_ONLY"
    const val TWO_DIGIT_YEAR = "TWO_DIGIT_YEAR"
    const val IMPLAUSIBLE_YEAR = "IMPLAUSIBLE_YEAR"
    const val NO_LABEL = "NO_LABEL"
}

/**
 * Parser dat ważności z tekstu rozpoznanego przez ML Kit Text Recognition.
 *
 * Działa wyłącznie na tekście – bez sieci, bez płatnego API. Rozpoznaje polskie etykiety
 * („należy spożyć do”, „najlepiej spożyć przed”) także bez polskich znaków, bo OCR często
 * gubi znaki diakrytyczne.
 */
object OcrDateParser {

    /** Etykiety „należy spożyć do” – termin przydatności do spożycia. */
    private val USE_BY_LABELS = listOf(
        "nalezy spozyc do dnia",
        "nalezy spozyc do",
        "nalezy zuzyc do",
        "spozyc do dnia",
        "termin przydatnosci do spozycia",
        "termin przydatnosci",
        "use by",
        "use-by",
    )

    /** Etykiety „najlepiej spożyć przed” – data minimalnej trwałości. */
    private val BEST_BEFORE_LABELS = listOf(
        "najlepiej spozyc przed koncem",
        "najlepiej spozyc przed",
        "najlepiej zuzyc przed",
        "data minimalnej trwalosci",
        "minimalna trwalosc",
        "best before",
        "best before end",
        "bb end",
    )

    /** Etykiety ogólne – data jest, ale rodzaj nie został wskazany. */
    private val GENERIC_LABELS = listOf(
        "data waznosci",
        "wazna do",
        "waznosc",
        "data wazn",
        "exp",
        "expiry",
        "expiration",
        "expires",
    )

    // Granice dopasowania muszą wykluczać także separatory: inaczej w zapisie "03/04/2027"
    // wzorzec MM.RRRR złapałby fragment "04/2027" i stworzył fałszywego kandydata.
    private const val LEFT_BOUNDARY = """(?<![\d.\-/])"""
    private const val RIGHT_BOUNDARY = """(?![\d.\-/])"""

    private val FULL_DATE = Regex(LEFT_BOUNDARY + """(\d{1,2})([.\-/])(\d{1,2})[.\-/](\d{4})""" + RIGHT_BOUNDARY)
    private val ISO_DATE = Regex(LEFT_BOUNDARY + """(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})""" + RIGHT_BOUNDARY)
    private val MONTH_YEAR = Regex(LEFT_BOUNDARY + """(\d{1,2})[.\-/](\d{4})""" + RIGHT_BOUNDARY)
    private val MONTH_SHORT_YEAR = Regex(LEFT_BOUNDARY + """(\d{1,2})[.\-/](\d{2})""" + RIGHT_BOUNDARY)

    fun parse(lines: List<String>, todayEpochDay: Long = PlFormat.todayEpochDay()): OcrParseResult {
        val candidates = mutableListOf<OcrDateCandidate>()
        var recentLabel: String? = null
        var recentType: DateType? = null
        var recentLabelLine = -10

        lines.forEachIndexed { lineIndex, rawLine ->
            val text = normalize(rawLine)
            if (text.isBlank()) return@forEachIndexed

            val label = detectLabel(text)
            if (label != null) {
                recentLabel = label.first
                recentType = label.second
                recentLabelLine = lineIndex
            }
            // Etykieta bywa w poprzedniej linii („Najlepiej spożyć przed:” / „12.10.2026”).
            val labelInRange = lineIndex - recentLabelLine <= 2
            val labelForLine = if (label != null) label.first else if (labelInRange) recentLabel else null
            val typeForLine = if (label != null) label.second else if (labelInRange) recentType else null

            candidates += extractDates(
                text = text,
                originalLine = rawLine.trim(),
                hasLabel = labelForLine != null,
                label = labelForLine,
                type = typeForLine ?: DateType.UNSPECIFIED,
                todayEpochDay = todayEpochDay,
            )
        }

        val deduped = candidates
            .groupBy { it.epochDay to it.precision }
            .map { (_, group) -> group.minByOrNull { it.confidence.ordinal } ?: group.first() }

        val sorted = deduped.sortedWith(
            compareBy<OcrDateCandidate> { it.confidence.ordinal }
                .thenByDescending { it.matchedLabel != null }
                .thenBy { kotlin.math.abs(it.epochDay - todayEpochDay) },
        )
        return OcrParseResult(sorted, lines.size)
    }

    /** Normalizacja: małe litery, bez znaków diakrytycznych, pojedyncze spacje. */
    internal fun normalize(input: String): String {
        val stripped = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        return stripped.lowercase()
            .replace(Regex("[^a-z0-9.\\-/ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun detectLabel(text: String): Pair<String, DateType>? {
        // Kolejność ma znaczenie: dłuższe i bardziej szczegółowe etykiety sprawdzamy najpierw.
        BEST_BEFORE_LABELS.firstOrNull { text.contains(it) }?.let { return it to DateType.BEST_BEFORE }
        USE_BY_LABELS.firstOrNull { text.contains(it) }?.let { return it to DateType.USE_BY }
        GENERIC_LABELS.firstOrNull { containsToken(text, it) }?.let { return it to DateType.UNSPECIFIED }
        return null
    }

    private fun containsToken(text: String, label: String): Boolean {
        if (label.length > 3) return text.contains(label)
        // Krótkie etykiety („exp”) dopasowujemy tylko jako osobne słowo, żeby nie łapać „export”.
        return Regex("""(^|\s)${Regex.escape(label)}($|\s|[.:])""").containsMatchIn(text)
    }

    private fun extractDates(
        text: String,
        originalLine: String,
        hasLabel: Boolean,
        label: String?,
        type: DateType,
        todayEpochDay: Long,
    ): List<OcrDateCandidate> {
        val results = mutableListOf<OcrDateCandidate>()
        val currentYear = LocalDate.ofEpochDay(todayEpochDay).year

        for (match in ISO_DATE.findAll(text)) {
            val year = match.groupValues[1].toIntOrNull() ?: continue
            val month = match.groupValues[2].toIntOrNull() ?: continue
            val day = match.groupValues[3].toIntOrNull() ?: continue
            resolve(day, month, year, currentYear, todayEpochDay, type, label, hasLabel, originalLine, null)
                ?.let(results::add)
        }

        for (match in FULL_DATE.findAll(text)) {
            val first = match.groupValues[1].toIntOrNull() ?: continue
            val separator = match.groupValues[2]
            val second = match.groupValues[3].toIntOrNull() ?: continue
            val year = match.groupValues[4].toIntOrNull() ?: continue
            var issue: String? = null
            val day: Int
            val month: Int
            when {
                first in 1..31 && second in 1..12 -> {
                    // Konwencja polska: DD.MM.RRRR. Przy kropce/myślniku zapis jest
                    // jednoznaczny zwyczajowo, więc NIE oznaczamy go jako niejednoznaczny.
                    day = first
                    month = second
                }

                first in 1..12 && second in 13..31 -> {
                    // Drugi człon nie może być miesiącem → zapis MM.DD.RRRR, porządkujemy go.
                    day = second
                    month = first
                    issue = OcrIssue.ORDER_MMDD
                }

                else -> continue
            }
            // Ukośnik jest dwuznaczny: 03/04 to 3 kwietnia albo 4 marca.
            // Taki przypadek oznaczamy i wymagamy poprawienia daty (specyfikacja).
            val ambiguousSlash = separator == "/" && first in 1..12 && second in 1..12
            val precision = if (ambiguousSlash) DayPrecision.AMBIGUOUS_ORDER else DayPrecision.EXACT
            if (ambiguousSlash) issue = OcrIssue.AMBIGUOUS_ORDER
            resolve(day, month, year, currentYear, todayEpochDay, type, label, hasLabel, originalLine, issue, precision)
                ?.let(results::add)
        }

        for (match in MONTH_YEAR.findAll(text)) {
            val month = match.groupValues[1].toIntOrNull() ?: continue
            val year = match.groupValues[2].toIntOrNull() ?: continue
            if (month !in 1..12) continue
            monthOnly(month, year, currentYear, todayEpochDay, type, label, hasLabel, originalLine, OcrIssue.MONTH_ONLY)
                ?.let(results::add)
        }

        // MM.RR przyjmujemy tylko wtedy, gdy na etykiecie jest słowne oznaczenie daty –
        // inaczej łatwo pomylić z ceną, numerem partii albo telefonem.
        if (hasLabel) {
            for (match in MONTH_SHORT_YEAR.findAll(text)) {
                val month = match.groupValues[1].toIntOrNull() ?: continue
                val shortYear = match.groupValues[2].toIntOrNull() ?: continue
                if (month !in 1..12) continue
                val year = expandTwoDigitYear(shortYear, currentYear)
                monthOnly(month, year, currentYear, todayEpochDay, type, label, true, originalLine, OcrIssue.TWO_DIGIT_YEAR)
                    ?.let(results::add)
            }
        }

        return results
    }

    internal fun expandTwoDigitYear(shortYear: Int, currentYear: Int): Int {
        val candidate = (currentYear / 100) * 100 + shortYear
        return if (candidate > currentYear + 30) candidate - 100 else candidate
    }

    private fun resolve(
        day: Int,
        month: Int,
        year: Int,
        currentYear: Int,
        todayEpochDay: Long,
        type: DateType,
        label: String?,
        hasLabel: Boolean,
        sourceText: String,
        issue: String?,
        precision: DayPrecision = DayPrecision.EXACT,
    ): OcrDateCandidate? {
        if (month !in 1..12) return null
        val monthLength = LocalDate.of(year, month, 1).lengthOfMonth()
        if (day !in 1..monthLength) return null

        val implausible = year < currentYear - 10 || year > currentYear + 30
        val finalIssue = when {
            implausible -> OcrIssue.IMPLAUSIBLE_YEAR
            issue != null -> issue
            !hasLabel -> OcrIssue.NO_LABEL
            else -> null
        }
        val confidence = when {
            implausible -> OcrConfidence.LOW
            finalIssue == OcrIssue.AMBIGUOUS_ORDER -> OcrConfidence.LOW
            finalIssue == OcrIssue.ORDER_MMDD -> OcrConfidence.MEDIUM
            finalIssue == OcrIssue.NO_LABEL -> OcrConfidence.MEDIUM
            else -> OcrConfidence.HIGH
        }
        return OcrDateCandidate(
            epochDay = LocalDate.of(year, month, day).toEpochDay(),
            precision = precision,
            type = type,
            confidence = confidence,
            sourceText = sourceText,
            matchedLabel = label,
            issueCode = finalIssue,
        )
    }

    private fun monthOnly(
        month: Int,
        year: Int,
        currentYear: Int,
        todayEpochDay: Long,
        type: DateType,
        label: String?,
        hasLabel: Boolean,
        sourceText: String,
        issue: String,
    ): OcrDateCandidate? {
        if (month !in 1..12) return null
        val implausible = year < currentYear - 10 || year > currentYear + 30
        val lastDay = LocalDate.of(year, month, 1).lengthOfMonth()
        val confidence = when {
            implausible -> OcrConfidence.LOW
            !hasLabel -> OcrConfidence.MEDIUM
            issue == OcrIssue.TWO_DIGIT_YEAR -> OcrConfidence.LOW
            else -> OcrConfidence.MEDIUM
        }
        return OcrDateCandidate(
            epochDay = LocalDate.of(year, month, lastDay).toEpochDay(),
            precision = DayPrecision.MONTH_ONLY,
            type = type,
            confidence = confidence,
            sourceText = sourceText,
            matchedLabel = label,
            issueCode = if (implausible) OcrIssue.IMPLAUSIBLE_YEAR else issue,
        )
    }
}

/**
 * Kandydat OCR na datę ważności.
 *
 * Zawsze z `confirmed = false`: zgodnie ze specyfikacją każda data odczytana automatycznie
 * wymaga potwierdzenia użytkownika, a bez potwierdzenia nie tworzy przypomnienia.
 */
fun OcrDateCandidate.toExpiryDate(): ExpiryDate = ExpiryDate(
    epochDay = epochDay,
    dayPrecision = precision,
    type = type,
    source = DateSource.OCR,
    confirmed = false,
)
