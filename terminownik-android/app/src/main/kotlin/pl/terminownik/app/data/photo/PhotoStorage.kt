package pl.terminownik.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import pl.terminownik.app.data.backup.BackupContract
import pl.terminownik.app.data.backup.BackupValidator

/**
 * Zdjęcia pozycji w pamięci prywatnej aplikacji (`filesDir/photos`).
 *
 * Zdjęcia kopiujemy z systemowego pickera (SAF) – bez uprawnień do pamięci urządzenia
 * i bez trzymania czegokolwiek w pamięci współdzielonej.
 */
class PhotoStorage(private val context: Context) {

    companion object {
        const val DIR_NAME = "photos"
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 85
        const val MAX_SOURCE_BYTES = 25L * 1024 * 1024
    }

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * Kopiuje i przeskalowuje obraz wskazany przez [uri].
     * Zwraca nazwę pliku albo null, gdy obraz jest nieczytelny lub za duży.
     */
    fun import(uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        // Plik z pickera traktujemy jako niezaufany: limit rozmiaru źródła.
        val declaredSize = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        if (declaredSize > MAX_SOURCE_BYTES) return@runCatching null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@runCatching null

        val fileName = "${UUID.randomUUID()}.jpg"
        FileOutputStream(File(dir, fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        bitmap.recycle()
        fileName
    }.getOrNull()

    /** Zwraca plik tylko wtedy, gdy nazwa nie wychodzi poza katalog zdjęć. */
    fun fileFor(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        if (fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) return null
        val candidate = File(dir, fileName)
        val root = dir.canonicalPath
        return if (candidate.canonicalPath.startsWith(root + File.separator) && candidate.exists()) candidate else null
    }

    fun exists(fileName: String?): Boolean = fileFor(fileName) != null

    fun delete(fileName: String?) {
        fileFor(fileName)?.delete()
    }

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun count(): Int = dir.listFiles()?.size ?: 0

    /** Usuwa wszystkie zdjęcia – używane przy kasowaniu danych i przy odtwarzaniu kopii. */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Zapisuje bajty zdjęcia o nazwie zwalidowanej wcześniej przez BackupValidator. */
    fun writeBytes(fileName: String, bytes: ByteArray): Boolean = runCatching {
        if (bytes.size > BackupContract.MAX_PHOTO_ENTRY_BYTES) return@runCatching false
        if (!BackupValidator.isSafePhotoFileName(fileName)) return@runCatching false
        File(dir, fileName).writeBytes(bytes)
        true
    }.getOrDefault(false)

}
