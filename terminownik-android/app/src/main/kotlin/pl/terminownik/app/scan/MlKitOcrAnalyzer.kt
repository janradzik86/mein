package pl.terminownik.app.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Rozpoznawanie tekstu etykiety MODELEM WBUDOWANYM (łacina), całkowicie offline.
 *
 * Zwraca linie tekstu – daty wyciąga z nich [pl.terminownik.app.domain.OcrDateParser],
 * a każda propozycja wymaga potwierdzenia użytkownika.
 */
object MlKitOcrAnalyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { block -> block.lines.map { it.text } }
                if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(emptyList())
            }
    }

    fun close() {
        recognizer.close()
    }
}
