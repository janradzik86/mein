package pl.terminownik.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eksport CSV: separator, BOM, końce linii i cytowanie (Excel PL). */
class CsvExportTest {

    private val columns = CsvExport.ItemColumns(
        section = "Sekcja",
        name = "Nazwa",
        quantity = "Ilość",
        unit = "Jednostka",
        location = "Miejsce",
        batch = "Partia",
        barcode = "Kod",
        expiry = "Termin",
        dateType = "Rodzaj daty",
        dateSource = "Źródło",
        confirmed = "Potwierdzona",
        status = "Stan",
        note = "Notatka",
        createdAt = "Dodano",
        updatedAt = "Zaktualizowano",
    )

    private val labels = CsvExport.Labels(
        pantry = "Spiżarnia",
        medicine = "Apteczka",
        useBy = "Należy spożyć do",
        bestBefore = "Najlepiej spożyć przed",
        unspecified = "Nie określono",
        sourceManual = "Ręcznie",
        sourceGs1 = "GS1",
        sourceOcr = "OCR",
        sourceImport = "Kopia",
        statusActive = "Aktywna",
        statusUsed = "Zużyta",
        statusDiscarded = "Wyrzucona",
        yes = "tak",
        no = "nie",
    )

    private fun item(name: String, note: String? = null) = PantryItem(
        id = 1,
        section = Section.PANTRY,
        name = name,
        quantity = 1.5,
        unit = "kg",
        storageLocation = "Lodówka",
        batchCode = "A1",
        barcode = "5901234123457",
        expiryDate = ExpiryDate(
            epochDay = LocalDate.of(2027, 3, 5).toEpochDay(),
            type = DateType.USE_BY,
            source = DateSource.MANUAL,
        ),
        note = note,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `plik zaczyna się od BOM i używa CRLF oraz średnika`() {
        val csv = CsvExport.exportItems(listOf(item("Jogurt")), columns, labels)
        assertTrue(csv.startsWith(CsvExport.BOM))
        assertTrue(csv.contains("\r\n"))
        assertFalse("CSV nie może używać gołych LF", csv.replace("\r\n", "").contains("\n"))
        assertTrue(csv.contains(';'))
        assertFalse("CSV nie może być rozdzielany przecinkiem", csv.contains(","))
    }

    @Test
    fun `nagłówki pochodzą z zasobów przekazanych przez UI`() {
        val csv = CsvExport.exportItems(emptyList(), columns, labels)
        val firstLine = csv.removePrefix(CsvExport.BOM).substringBefore("\r\n")
        assertEquals(
            listOf("Sekcja", "Nazwa", "Ilość", "Jednostka", "Miejsce", "Partia", "Kod", "Termin",
                "Rodzaj daty", "Źródło", "Potwierdzona", "Stan", "Notatka", "Dodano", "Zaktualizowano")
                .joinToString(";"),
            firstLine,
        )
    }

    @Test
    fun `wartości z separatorem są cytowane zgodnie z RFC4180`() {
        assertEquals("\"a;b\"", CsvExport.escape("a;b"))
        assertEquals("\"ze \"\"cytatem\"\"\"", CsvExport.escape("ze \"cytatem\""))
        assertEquals("zwykła", CsvExport.escape("zwykła"))
        assertEquals("", CsvExport.escape(null))
    }

    @Test
    fun `wiersz zawiera polskie etykiety słownikowe`() {
        val csv = CsvExport.exportItems(listOf(item("Jogurt", "otwarty; wczoraj")), columns, labels)
        val row = csv.removePrefix(CsvExport.BOM).lineSequence().drop(1).first()
        assertTrue(row.startsWith("Spiżarnia;Jogurt;"))
        assertTrue("Brak etykiety rodzaju daty: $row", row.contains("Należy spożyć do"))
        assertTrue("Notatka z średnikiem musi być cytowana: $row", row.contains("\"otwarty; wczoraj\""))
        assertTrue(row.contains("05.03.2027"))
    }

    @Test
    fun `pusta lista daje sam nagłówek`() {
        val csv = CsvExport.exportItems(emptyList(), columns, labels)
        val lines = csv.removePrefix(CsvExport.BOM).split("\r\n").filter { it.isNotBlank() }
        assertEquals(1, lines.size)
    }

    @Test
    fun `eksport listy zakupów ma własne nagłówki`() {
        val csv = CsvExport.exportShoppingList(
            items = listOf(
                ShoppingItem(id = 1, name = "Mleko", quantity = 2.0, unit = "szt.", sectionHint = Section.PANTRY, done = true),
            ),
            headerName = "Nazwa",
            headerQuantity = "Ilość",
            headerUnit = "Jednostka",
            headerSection = "Sekcja",
            headerDone = "Kupione",
            headerNote = "Notatka",
            labels = labels,
        )
        assertTrue(csv.startsWith(CsvExport.BOM))
        val lines = csv.removePrefix(CsvExport.BOM).split("\r\n").filter { it.isNotBlank() }
        assertEquals("Nazwa;Ilość;Jednostka;Sekcja;Kupione;Notatka", lines[0])
        assertTrue(lines[1].startsWith("Mleko;2;szt.;Spiżarnia;tak"))
    }
}
