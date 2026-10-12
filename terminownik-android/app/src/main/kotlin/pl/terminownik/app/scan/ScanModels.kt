package pl.terminownik.app.scan

import pl.terminownik.app.domain.Gs1ParseResult

/** Surowy wynik skanowania kodu z ML Kit. */
data class ScannedBarcode(
    val rawValue: String,
    val displayValue: String?,
    val formatName: String,
    val formatCode: Int,
)

/** Rezultat interpretacji kodu na potrzeby formularza dodawania pozycji. */
data class BarcodeInterpretation(
    val barcode: String,
    val formatName: String,
    val validProductCode: Boolean,
    val canCarryExpiry: Boolean,
    val gs1: Gs1ParseResult?,
    val knownName: String?,
    val knownUnit: String?,
    /** Data z GS1 – zawsze do potwierdzenia przez użytkownika. */
    val proposedExpiryEpochDay: Long?,
    val proposedPrecision: pl.terminownik.app.domain.DayPrecision?,
    val needsConfirmation: Boolean,
    val messageCode: String?,
)

object ScanMessage {
    const val OK = "OK"
    const val INVALID_CHECK_DIGIT = "INVALID_CHECK_DIGIT"
    const val EAN_NO_EXPIRY = "EAN_NO_EXPIRY"
    const val GS1_NO_DATE = "GS1_NO_DATE"
    const val GS1_DATE_AMBIGUOUS = "GS1_DATE_AMBIGUOUS"
    const val GS1_PARSE_ERROR = "GS1_PARSE_ERROR"
    const val CAMERA_DENIED = "CAMERA_DENIED"
    const val NO_CAMERA = "NO_CAMERA"
}
