package pl.terminownik.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formatowanie zgodne z polską lokalizacją (brief: poprawne formaty PLN/liczb/dat). */
class PlFormatTest {

    private val day = LocalDate.of(2027, 3, 5).toEpochDay()

    @Test
    fun `data w formacie polskim dd MM rrrr`() {
        assertEquals("05.03.2027", PlFormat.formatDate(day))
    }

    @Test
    fun `data miesięczna bez dnia`() {
        assertEquals("03.2027", PlFormat.formatMonthOnly(day))
    }

    @Test
    fun `termin z oznaczeniem miesiąca i potwierdzenia`() {
        val monthOnly = ExpiryDate(day, DayPrecision.MONTH_ONLY, DateType.USE_BY, DateSource.MANUAL, confirmed = true)
        val formatted = PlFormat.formatExpiry(monthOnly)
        assertTrue("Brak oznaczenia końca miesiąca: $formatted", formatted.contains("koniec miesiąca"))

        val unconfirmed = ExpiryDate(day, DayPrecision.EXACT, DateType.USE_BY, DateSource.OCR, confirmed = false)
        assertTrue(PlFormat.formatExpiry(unconfirmed).contains("potwierdzenia"))
    }

    @Test
    fun `ilość używa przecinka jako separatora dziesiętnego`() {
        assertEquals("1,5", PlFormat.formatQuantity(1.5))
        assertEquals("2", PlFormat.formatQuantity(2.0))
        assertEquals("0,25", PlFormat.formatQuantity(0.25))
        assertEquals("", PlFormat.formatQuantity(null))
    }

    @Test
    fun `ilość z jednostką`() {
        assertEquals("1,5 kg", PlFormat.formatQuantityWithUnit(1.5, "kg"))
        assertEquals("3 szt.", PlFormat.formatQuantityWithUnit(3.0, "szt."))
        assertEquals("2", PlFormat.formatQuantityWithUnit(2.0, null))
    }

    @Test
    fun `odległość w dniach po polsku`() {
        assertEquals("dziś", PlFormat.formatDayDistance(0))
        assertEquals("jutro", PlFormat.formatDayDistance(1))
        assertEquals("wczoraj", PlFormat.formatDayDistance(-1))
        assertEquals("za 5 dni", PlFormat.formatDayDistance(5))
        assertEquals("5 dni temu", PlFormat.formatDayDistance(-5))
    }

    @Test
    fun `rozmiar pliku w jednostkach czytelnych`() {
        assertEquals("512 B", PlFormat.formatFileSize(512))
        assertEquals("1 kB", PlFormat.formatFileSize(1024))
        assertEquals("1,5 MB", PlFormat.formatFileSize(1024L * 1024 * 3 / 2))
    }

    @Test
    fun `nazwa kopii zapasowej jest bezpieczna jako nazwa pliku`() {
        val name = PlFormat.backupFileName("Terminownik")
        assertTrue(name.startsWith("terminownik-"))
        assertTrue(name.endsWith(".zip"))
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains(" "))
        // Znak specjalny w nazwie nie może przedostać się do nazwy pliku.
        val dirty = PlFormat.backupFileName("../złośliwa nazwa")
        assertFalse(dirty.contains(".."))
        assertFalse(dirty.contains('/'))
    }

    @Test
    fun `znacznik czasu do nazw plików ma stabilny format`() {
        val stamp = PlFormat.timestampForFile()
        assertTrue("Nieoczekiwany format: $stamp", Regex("""^\d{8}-\d{4}$""").matches(stamp))
    }

    @Test
    fun `dziś zwraca bieżący dzień epoki`() {
        assertEquals(LocalDate.now(PlFormat.zone()).toEpochDay(), PlFormat.todayEpochDay())
    }
}
