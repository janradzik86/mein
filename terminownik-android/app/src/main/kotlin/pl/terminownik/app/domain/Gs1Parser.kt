package pl.terminownik.app.domain

import java.time.LocalDate

/** Data wyciągnięta z elementu GS1 razem z informacją o precyzji. */
data class Gs1Date(val epochDay: Long, val precision: DayPrecision)

/**
 * Wynik parsowania łańcucha elementów GS1 (GS1 DataMatrix / GS1-128 / GS1 QR).
 *
 * Jeśli kod NIE zawiera AI 17, pole [expiry] jest null – nigdy nie zgadujemy terminu
 * na podstawie samego identyfikatora produktu.
 */
data class Gs1ParseResult(
    val gtin: String? = null,
    val lot: String? = null,
    val serial: String? = null,
    val expiry: Gs1Date? = null,
    val productionDate: Gs1Date? = null,
    val count: Double? = null,
    val netWeightKg: Double? = null,
    val volumeLitres: Double? = null,
    val recognizedAis: List<String> = emptyList(),
    val unrecognized: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val hasAnything: Boolean
        get() = gtin != null || lot != null || serial != null || expiry != null ||
            productionDate != null || count != null || netWeightKg != null || volumeLitres != null

    /** AI 17 z DD=00 albo nieprawidłowy zapis → wymagamy potwierdzenia daty. */
    val expiryNeedsConfirmation: Boolean
        get() = expiry == null || expiry.precision != DayPrecision.EXACT || errors.isNotEmpty()
}

/**
 * Parser łańcucha elementów GS1.
 *
 * Obsługa: AI 01 (GTIN), 10 (partia), 11 (data produkcji), 17 (termin ważności),
 * 21 (numer seryjny), 30 (liczba sztuk), 310n (masa netto w kg), 315n (objętość w l).
 *
 * Separatorem FNC1 jest znak GS (U+001D); akceptujemy też notację „^FNC1” i prefiksy
 * symbologii AIM (np. „]d2”). Kod wejściowy traktujemy jako niezaufany: nic nie rzucamy
 * wyjątkiem, zwracamy listę błędów.
 */
object Gs1Parser {

    const val GS: Char = '\u001D'

    private const val MAX_VARIABLE_DATA = 30

    /** AI o stałej długości danych (bez AI: sama liczba cyfr danych). */
    private val FIXED_LENGTH_DATA = mapOf(
        "00" to 18,
        "01" to 14,
        "02" to 14,
        "11" to 6,
        "12" to 6,
        "13" to 6,
        "15" to 6,
        "16" to 6,
        "17" to 6,
        "20" to 2,
    )

    private val MEASURE_AI = Regex("^3[1-6][0-9][0-9]$")

    fun parse(raw: String?, todayEpochDay: Long = PlFormat.todayEpochDay()): Gs1ParseResult {
        if (raw.isNullOrBlank()) {
            return Gs1ParseResult(errors = listOf("Pusty kod"))
        }
        var input = raw.trim()
        // Prefiks symbologii AIM: ]d2 (DataMatrix), ]C1 (GS1-128), ]Q3 (QR), ]e0.
        if (input.length >= 3 && input[0] == ']') {
            input = input.substring(3)
        }
        input = input.replace("^FNC1", GS.toString())
        input = input.trimStart(GS)

        var gtin: String? = null
        var lot: String? = null
        var serial: String? = null
        var expiry: Gs1Date? = null
        var production: Gs1Date? = null
        var count: Double? = null
        var weightKg: Double? = null
        var volumeL: Double? = null
        val recognized = mutableListOf<String>()
        val unrecognized = mutableListOf<String>()
        val errors = mutableListOf<String>()

        var index = 0
        var guard = 0
        while (index < input.length && guard++ < 64) {
            while (index < input.length && input[index] == GS) index++
            if (index >= input.length) break

            val ai = readApplicationIdentifier(input, index)
            if (ai == null) {
                unrecognized += input.substring(index).take(20)
                errors += "Nie rozpoznano AI na pozycji ${index + 1}"
                break
            }
            index += ai.value.length

            val dataLength = dataLengthFor(ai.value, input, index)
            if (dataLength <= 0 || index + dataLength > input.length) {
                unrecognized += ai.value
                errors += "AI ${ai.value}: brak danych lub dane ucięte"
                break
            }
            val data = input.substring(index, index + dataLength)
            index += dataLength

            when {
                ai.value == "01" -> {
                    gtin = data
                    recognized += "01"
                    if (!BarcodeValidator.isCheckDigitValid(data)) errors += "AI 01: błędna cyfra kontrolna GTIN"
                }

                ai.value == "10" -> {
                    lot = data.ifBlank { null }
                    recognized += "10"
                }

                ai.value == "21" -> {
                    serial = data.ifBlank { null }
                    recognized += "21"
                }

                ai.value == "30" -> {
                    count = data.toDoubleOrNull()
                    recognized += "30"
                    if (count == null) errors += "AI 30: nieprawidłowa liczba"
                }

                ai.value == "17" -> {
                    recognized += "17"
                    val parsed = parseGs1Date(data, todayEpochDay)
                    if (parsed.first != null) {
                        expiry = parsed.first
                    } else {
                        errors += "AI 17: ${parsed.second}"
                    }
                }

                ai.value == "11" -> {
                    recognized += "11"
                    val parsed = parseGs1Date(data, todayEpochDay)
                    if (parsed.first != null) {
                        production = parsed.first
                    } else {
                        errors += "AI 11: ${parsed.second}"
                    }
                }

                MEASURE_AI.matches(ai.value) && ai.value.startsWith("310") -> {
                    recognized += ai.value
                    weightKg = parseMeasure(data, ai.value[3])
                    if (weightKg == null) errors += "AI ${ai.value}: nieprawidłowa masa"
                }

                MEASURE_AI.matches(ai.value) && ai.value.startsWith("315") -> {
                    recognized += ai.value
                    volumeL = parseMeasure(data, ai.value[3])
                    if (volumeL == null) errors += "AI ${ai.value}: nieprawidłowa objętość"
                }

                else -> {
                    unrecognized += ai.value
                }
            }
        }

        if (guard >= 64) errors += "Kod GS1 jest zbyt długi lub uszkodzony"

        val result0 = Gs1ParseResult(
            gtin = gtin,
            lot = lot,
            serial = serial,
            expiry = expiry,
            productionDate = production,
            count = count,
            netWeightKg = weightKg,
            volumeLitres = volumeL,
            recognizedAis = recognized,
            unrecognized = unrecognized,
            errors = errors,
        )
        // Kod, z którego nic nie dało się odczytać, musi być zgłoszony jako błąd:
        // UI pokazuje wtedy „brak danych GS1 – wpisz datę ręcznie”, a nie pustą ciszę.
        if (!result0.hasAnything && errors.isEmpty()) {
            errors += "Kod nie zawiera rozpoznanych danych GS1"
        }

        return result0.copy(errors = errors)
    }

    private data class Ai(val value: String)

    private fun readApplicationIdentifier(input: String, start: Int): Ai? {
        // AI mają 2–4 cyfry; próbujemy od najdłuższego, bo np. „310n” jest 4-cyfrowe.
        for (length in intArrayOf(4, 3, 2)) {
            if (start + length > input.length) continue
            val candidate = input.substring(start, start + length)
            if (!candidate.all { it.isDigit() }) continue
            if (isKnownAi(candidate)) return Ai(candidate)
        }
        return null
    }

    private fun isKnownAi(ai: String): Boolean = when (ai.length) {
        2 -> ai in FIXED_LENGTH_DATA || ai == "10" || ai == "21" || ai == "30" || ai == "37" || ai == "90" ||
            ai in setOf("91", "92", "93", "94", "95", "96", "97", "98", "99")

        3 -> ai.startsWith("24") || ai.startsWith("25") || ai.startsWith("40") || ai.startsWith("41") ||
            ai.startsWith("42") || ai == "310" || ai == "370"

        4 -> MEASURE_AI.matches(ai) || ai.startsWith("39") || ai.startsWith("43") || ai.startsWith("70") ||
            ai.startsWith("80") || ai.startsWith("81") || ai.startsWith("82")

        else -> false
    }

    /** Długość danych dla AI: stała z tabeli, 6 dla miar 31xx–36xx, w przeciwnym razie do separatora GS. */
    private fun dataLengthFor(ai: String, input: String, from: Int): Int {
        FIXED_LENGTH_DATA[ai]?.let { return it }
        if (MEASURE_AI.matches(ai)) return 6
        var end = from
        while (end < input.length && input[end] != GS && end - from < MAX_VARIABLE_DATA) end++
        return end - from
    }

    private fun parseMeasure(data: String, decimalDigit: Char): Double? {
        if (data.length != 6 || !data.all { it.isDigit() }) return null
        val decimals = decimalDigit - '0'
        if (decimals !in 0..6) return null
        val value = data.toLongOrNull() ?: return null
        val divisor = Math.pow(10.0, decimals.toDouble())
        return value / divisor
    }

    /**
     * Data GS1 w formacie YYMMDD.
     *
     * DD = 00 oznacza „dzień nieokreślony” – wtedy zwracamy ostatni dzień miesiąca
     * z precyzją MONTH_ONLY, co w UI wymusza potwierdzenie (specyfikacja, funkcja 7).
     */
    internal fun parseGs1Date(yymmdd: String, todayEpochDay: Long): Pair<Gs1Date?, String?> {
        if (yymmdd.length != 6 || !yymmdd.all { it.isDigit() }) {
            return null to "oczekiwano 6 cyfr YYMMDD"
        }
        val yy = yymmdd.substring(0, 2).toInt()
        val mm = yymmdd.substring(2, 4).toInt()
        val dd = yymmdd.substring(4, 6).toInt()
        if (mm !in 1..12) return null to "nieprawidłowy miesiąc: $mm"

        val currentYear = LocalDate.ofEpochDay(todayEpochDay).year
        // Reguła GS1: rok mieści się w oknie [-50, +49] lat względem bieżącego.
        var year = (currentYear / 100) * 100 + yy
        if (year > currentYear + 49) year -= 100
        if (year < currentYear - 50) year += 100

        return if (dd == 0) {
            val lastDay = LocalDate.of(year, mm, 1).lengthOfMonth()
            Gs1Date(LocalDate.of(year, mm, lastDay).toEpochDay(), DayPrecision.MONTH_ONLY) to null
        } else {
            val monthLength = LocalDate.of(year, mm, 1).lengthOfMonth()
            if (dd > monthLength) {
                null to "nieprawidłowy dzień: $dd (miesiąc ma $monthLength dni)"
            } else {
                Gs1Date(LocalDate.of(year, mm, dd).toEpochDay(), DayPrecision.EXACT) to null
            }
        }
    }
}
