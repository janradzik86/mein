package pl.terminownik.app.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Analizator klatek kamery: wykrywa kody ML Kit-em z MODELEM WBUDOWANYM w APK.
 *
 * `com.google.mlkit:barcode-scanning` (nie wariant `play-services-*`) zawiera model w aplikacji,
 * więc skanowanie działa offline od pierwszego uruchomienia, bez Google Play i bez sieci.
 */
class MlKitBarcodeAnalyzer(
    private val onDetected: (ScannedBarcode) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build(),
    )

    @Volatile
    private var delivered = false

    override fun analyze(imageProxy: ImageProxy) {
        if (delivered) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                if (delivered) return@addOnSuccessListener
                val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() } ?: return@addOnSuccessListener
                delivered = true
                onDetected(
                    ScannedBarcode(
                        rawValue = first.rawValue ?: "",
                        displayValue = first.displayValue,
                        formatName = formatName(first.format),
                        formatCode = first.format,
                    ),
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun reset() {
        delivered = false
    }

    companion object {
        /** Nazwy formatów używane później do decyzji, czy kod może przenosić termin (GS1). */
        fun formatName(format: Int): String = when (format) {
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_PDF417 -> "PDF_417"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            else -> "UNKNOWN"
        }
    }
}
