package pl.terminownik.app.scan

import pl.terminownik.app.domain.BarcodeValidator
import pl.terminownik.app.domain.Gs1Parser
import pl.terminownik.app.domain.PlFormat

/**
 * Interpretacja zeskanowanego kodu.
 *
 * Kluczowa zasada ze specyfikacji: sam EAN/UPC identyfikuje produkt i NIE pozwala wyznaczyć
 * terminu ważności. Termin może pochodzić wyłącznie z symbolu GS1 z AI 17, z OCR albo z wpisu ręcznego.
 *
 * Klasa jest czysta (bez Androida), więc jej logikę można testować na JVM.
 */
object BarcodeInterpreter {

    fun interpret(
        scanned: ScannedBarcode,
        knownName: String?,
        knownUnit: String?,
        todayEpochDay: Long = PlFormat.todayEpochDay(),
    ): BarcodeInterpretation {
        val digits = BarcodeValidator.normalize(scanned.rawValue) ?: scanned.rawValue.trim()
        val canCarryExpiry = BarcodeValidator.canCarryExpiryDate(scanned.formatName) ||
            scanned.rawValue.contains(Gs1Parser.GS)

        // Parsowanie GS1 tylko wtedy, gdy symbol faktycznie może przenosić dane GS1.
        val gs1 = if (canCarryExpiry) Gs1Parser.parse(scanned.rawValue, todayEpochDay) else null
        val gs1Usable = gs1?.takeIf { it.hasAnything }

        val validProductCode = if (canCarryExpiry) {
            digits.length >= 8
        } else {
            BarcodeValidator.isCheckDigitValid(digits)
        }

        val expiry = gs1Usable?.expiry
        val messageCode = when {
            !validProductCode -> ScanMessage.INVALID_CHECK_DIGIT
            expiry == null && gs1Usable != null && gs1Usable.errors.isNotEmpty() -> ScanMessage.GS1_PARSE_ERROR
            expiry == null && gs1Usable != null -> ScanMessage.GS1_NO_DATE
            expiry == null -> ScanMessage.EAN_NO_EXPIRY
            expiry.precision != pl.terminownik.app.domain.DayPrecision.EXACT -> ScanMessage.GS1_DATE_AMBIGUOUS
            else -> ScanMessage.OK
        }

        return BarcodeInterpretation(
            barcode = digits,
            formatName = scanned.formatName,
            validProductCode = validProductCode,
            canCarryExpiry = canCarryExpiry,
            gs1 = gs1Usable,
            knownName = knownName,
            knownUnit = knownUnit,
            proposedExpiryEpochDay = expiry?.epochDay,
            proposedPrecision = expiry?.precision,
            // Każdy odczyt automatyczny wymaga potwierdzenia (specyfikacja, funkcja 7).
            needsConfirmation = true,
            messageCode = messageCode,
        )
    }
}
