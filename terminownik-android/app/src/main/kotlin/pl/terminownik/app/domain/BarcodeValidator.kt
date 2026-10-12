package pl.terminownik.app.domain

/** Rodzina kodu – potrzebna, żeby NIE próbować wyciągać terminu z samego EAN/UPC. */
enum class BarcodeFamily { EAN_8, UPC_A, EAN_13, GTIN_14, GS1_2D, UNKNOWN }

object BarcodeValidator {

    private val SYMBOLS_CARRYING_GS1_DATA = setOf(
        "DATA_MATRIX",
        "DATA_MATRIX_GS1",
        "QR_CODE",
        "QR_CODE_GS1",
        "PDF_417",
        "PDF417",
        "AZTEC",
        "GS1_128",
        "CODE_128",
    )

    /** Zostawia tylko cyfry; odrzuca śmieci i puste wejście. */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        return if (digits.isEmpty()) null else digits
    }

    /**
     * Kontrola cyfry kontrolnej GTIN (mod 10). Działa dla EAN-8, UPC-A, EAN-13 i GTIN-14,
     * bo wszystkie te kody są podzbiorami GTIN.
     */
    fun isCheckDigitValid(digits: String): Boolean {
        if (digits.length < 8 || digits.length > 14 || !digits.all { it.isDigit() }) return false
        val padded = digits.padStart(14, '0')
        var sum = 0
        for (i in 0 until 13) {
            val weight = if (i % 2 == 0) 3 else 1
            sum += (padded[i] - '0') * weight
        }
        val expected = (10 - sum % 10) % 10
        return expected == padded[13] - '0'
    }

    fun familyOf(digits: String?, formatName: String?): BarcodeFamily {
        val d = digits ?: return BarcodeFamily.UNKNOWN
        val upper = formatName?.uppercase()?.replace("-", "_")
        if (upper != null && upper in SYMBOLS_CARRYING_GS1_DATA && d.length != 8 && d.length != 12 && d.length != 13 && d.length != 14) {
            return BarcodeFamily.GS1_2D
        }
        return when (d.length) {
            8 -> BarcodeFamily.EAN_8
            12 -> BarcodeFamily.UPC_A
            13 -> BarcodeFamily.EAN_13
            14 -> BarcodeFamily.GTIN_14
            else -> if (upper != null && upper in SYMBOLS_CARRYING_GS1_DATA) BarcodeFamily.GS1_2D else BarcodeFamily.UNKNOWN
        }
    }

    /**
     * ZASADA ZE SPECYFIKACJI: „Nie próbuj wyznaczać terminu ważności z samego EAN”.
     *
     * EAN/UPC identyfikuje TYLKO produkt. Termin może pochodzić wyłącznie z:
     *  - symbolu GS1 zawierającego AI 17 (najczęściej GS1 DataMatrix),
     *  - OCR etykiety,
     *  - wpisu ręcznego.
     */
    fun canCarryExpiryDate(formatName: String?): Boolean {
        val upper = formatName?.uppercase()?.replace("-", "_") ?: return false
        return upper in SYMBOLS_CARRYING_GS1_DATA
    }

    fun isValidProductCode(raw: String?, formatName: String?): Boolean {
        val digits = normalize(raw) ?: return false
        if (canCarryExpiryDate(formatName)) return digits.length >= 8
        return isCheckDigitValid(digits)
    }
}
