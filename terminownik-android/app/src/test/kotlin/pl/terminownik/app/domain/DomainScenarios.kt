package pl.terminownik.app.domain

import java.time.LocalDate
import pl.terminownik.app.data.backup.ArchiveEntry
import pl.terminownik.app.data.backup.BackupContract
import pl.terminownik.app.data.backup.BackupEntryInfo
import pl.terminownik.app.data.backup.BackupManifestData
import pl.terminownik.app.data.backup.BackupValidation
import pl.terminownik.app.data.backup.BackupValidator

/** Wynik pojedynczego scenariusza testowego. */
data class ScenarioResult(val group: String, val name: String, val passed: Boolean, val message: String?)

/**
 * Scenariusze testowe logiki biznesowej – bez zależności od JUnit ani od Androida.
 *
 * Ten sam kod jest wykonywany:
 *  - w teście jednostkowym JUnit (`DomainLogicTest`) podczas `./gradlew test`,
 *  - lokalnie bez Gradle (`tools/verify-domain.sh` → `LocalScenarioRunner`).
 *
 * Scenariusze odpowiadają kryteriom odbioru ze specyfikacji.
 */
object DomainScenarios {

    /** Stała data „dziś”, żeby wyniki były deterministyczne. */
    val TODAY: LocalDate = LocalDate.of(2026, 10, 12)
    val TODAY_EPOCH_DAY: Long = TODAY.toEpochDay()

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private fun scenario(group: String, name: String, block: () -> Unit): ScenarioResult = try {
        block()
        ScenarioResult(group, name, true, null)
    } catch (t: Throwable) {
        ScenarioResult(group, name, false, (t.message ?: t.toString()).take(400))
    }

    private fun assertEq(expected: Any?, actual: Any?, ctx: String) {
        if (expected != actual) throw AssertionError("$ctx: oczekiwano <$expected>, otrzymano <$actual>")
    }

    private fun assertTrue(condition: Boolean, ctx: String) {
        if (!condition) throw AssertionError("$ctx: warunek nie został spełniony")
    }

    private fun assertNull(value: Any?, ctx: String) {
        if (value != null) throw AssertionError("$ctx: oczekiwano null, otrzymano <$value>")
    }

    private fun assertNotNull(value: Any?, ctx: String): Unit =
        if (value == null) throw AssertionError("$ctx: oczekiwano wartości, otrzymano null") else Unit

    // ---------------------------------------------------------------------------------------------
    // GS1 DataMatrix (specyfikacja: funkcja 5 – obsługa informacji z GS1 DataMatrix)
    // ---------------------------------------------------------------------------------------------
    private fun gs1Scenarios(): List<ScenarioResult> {
        val g = "GS1"
        return listOf(
            scenario(g, "AI 01+17+10: GTIN, dokładny termin i partia") {
                val raw = "0105901234123457" + "17" + "261130" + "\u001D" + "10PARTIA42"
                val r = Gs1Parser.parse(raw, TODAY_EPOCH_DAY)
                assertEq("05901234123457", r.gtin, "gtin")
                val e = r.expiry ?: throw AssertionError("brak expiry")
                assertEq(day(2026, 11, 30), e.epochDay, "expiry.epochDay")
                assertEq(DayPrecision.EXACT, e.precision, "expiry.precision")
                assertEq("PARTIA42", r.lot, "lot")
                assertTrue(r.errors.isEmpty(), "brak błędów, było: ${r.errors}")
                assertTrue(!r.expiryNeedsConfirmation, "dokładna data nie wymaga potwierdzenia")
            },
            scenario(g, "AI 17 z DD=00 → tylko miesiąc i wymóg potwierdzenia") {
                val r = Gs1Parser.parse("0105901234123457" + "17" + "261100", TODAY_EPOCH_DAY)
                val e = r.expiry ?: throw AssertionError("brak expiry")
                assertEq(DayPrecision.MONTH_ONLY, e.precision, "precision")
                assertEq(day(2026, 11, 30), e.epochDay, "ostatni dzień miesiąca")
                assertTrue(r.expiryNeedsConfirmation, "miesiąc bez dnia wymaga potwierdzenia")
            },
            scenario(g, "AI 17 z nieprawidłowym miesiącem → błąd, bez daty") {
                val r = Gs1Parser.parse("17261330", TODAY_EPOCH_DAY)
                assertNull(r.expiry, "expiry")
                assertTrue(r.errors.isNotEmpty(), "zgłoszono błąd")
            },
            scenario(g, "AI 17 z dniem spoza zakresu miesiąca (31.02) → błąd") {
                val r = Gs1Parser.parse("17270231", TODAY_EPOCH_DAY)
                assertNull(r.expiry, "expiry nie może istnieć")
                assertTrue(r.errors.any { "dzień" in it }, "komunikat o dniu: ${r.errors}")
            },
            scenario(g, "Sam EAN-13 nie daje terminu ważności") {
                // ZASADA: „Nie próbuj wyznaczać terminu ważności z samego EAN”.
                val r = Gs1Parser.parse("5901234123457", TODAY_EPOCH_DAY)
                assertNull(r.expiry, "expiry z samego EAN")
                assertTrue(!BarcodeValidator.canCarryExpiryDate("EAN_13"), "EAN nie przenosi terminu")
                assertTrue(BarcodeValidator.canCarryExpiryDate("DATA_MATRIX"), "DataMatrix może przenosić termin")
            },
            scenario(g, "Prefiks symbologii AIM ]d2 jest usuwany") {
                val r = Gs1Parser.parse("]d2" + "0105901234123457" + "17" + "270115", TODAY_EPOCH_DAY)
                assertEq("05901234123457", r.gtin, "gtin")
                assertEq(day(2027, 1, 15), r.expiry!!.epochDay, "expiry")
            },
            scenario(g, "Notacja ^FNC1 jest obsługiwana") {
                // Poprawny zapis: AI 10 + "ABC" + FNC1 + AI 17 + YYMMDD
                val r = Gs1Parser.parse("10ABC^FNC1" + "17" + "261231", TODAY_EPOCH_DAY)
                assertEq("ABC", r.lot, "lot")
                assertEq(day(2026, 12, 31), r.expiry!!.epochDay, "expiry")
            },
            scenario(g, "Reguła stulecia: YY=05 → 2005 dla daty produkcji (okno -50/+49)") {
                val r = Gs1Parser.parse("11050110", TODAY_EPOCH_DAY)
                val prod = r.productionDate ?: throw AssertionError("brak productionDate")
                assertEq(day(2005, 1, 10), prod.epochDay, "rok 2005")
            },
            scenario(g, "AI 3103: masa netto 1,250 kg") {
                val r = Gs1Parser.parse("3103001250", TODAY_EPOCH_DAY)
                val w = r.netWeightKg ?: throw AssertionError("brak netWeightKg")
                assertTrue(kotlin.math.abs(w - 1.25) < 1e-9, "masa 1.25, było $w")
            },
            scenario(g, "AI 30: liczba sztuk") {
                val r = Gs1Parser.parse("0105901234123457" + "3012", TODAY_EPOCH_DAY)
                val c = r.count ?: throw AssertionError("brak count")
                assertTrue(kotlin.math.abs(c - 12.0) < 1e-9, "liczba sztuk 12")
            },
            scenario(g, "Kod uszkodzony / pusty nie rzuca wyjątku") {
                assertTrue(Gs1Parser.parse(null, TODAY_EPOCH_DAY).errors.isNotEmpty(), "null → błąd")
                assertTrue(Gs1Parser.parse("", TODAY_EPOCH_DAY).errors.isNotEmpty(), "pusty → błąd")
                assertTrue(Gs1Parser.parse("9999999999", TODAY_EPOCH_DAY).errors.isNotEmpty(), "śmieci → błąd")
            },
            scenario(g, "AI 21 (numer seryjny) z separatorem GS") {
                val r = Gs1Parser.parse("0105901234123457" + "21SER123\u001D" + "10L1", TODAY_EPOCH_DAY)
                assertEq("SER123", r.serial, "serial")
                assertEq("L1", r.lot, "lot")
            },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Kody kreskowe
    // ---------------------------------------------------------------------------------------------
    private fun barcodeScenarios(): List<ScenarioResult> {
        val g = "Kody"
        return listOf(
            scenario(g, "Cyfra kontrolna EAN-13 (poprawna i błędna)") {
                assertTrue(BarcodeValidator.isCheckDigitValid("5901234123457"), "poprawny EAN-13")
                assertTrue(!BarcodeValidator.isCheckDigitValid("5901234123458"), "błędna cyfra kontrolna")
            },
            scenario(g, "Cyfra kontrolna EAN-8") {
                assertTrue(BarcodeValidator.isCheckDigitValid("96385074"), "poprawny EAN-8")
                assertTrue(!BarcodeValidator.isCheckDigitValid("96385075"), "błędny EAN-8")
            },
            scenario(g, "Rodzina kodu po długości") {
                assertEq(BarcodeFamily.EAN_8, BarcodeValidator.familyOf("96385074", "EAN_8"), "EAN-8")
                assertEq(BarcodeFamily.EAN_13, BarcodeValidator.familyOf("5901234123457", "EAN_13"), "EAN-13")
                assertEq(BarcodeFamily.UPC_A, BarcodeValidator.familyOf("012345678905", "UPC_A"), "UPC-A")
            },
            scenario(g, "Normalizacja: śmieci i brak cyfr") {
                assertNull(BarcodeValidator.normalize("   "), "spacje")
                assertNull(BarcodeValidator.normalize("ABC"), "brak cyfr")
                assertEq("5901234123457", BarcodeValidator.normalize(" 590-1234123457 "), "myślniki")
            },
            scenario(g, "Błędny EAN nie przechodzi walidacji produktu") {
                assertTrue(!BarcodeValidator.isValidProductCode("1234567890123", "EAN_13"), "zły checksum")
                assertTrue(BarcodeValidator.isValidProductCode("5901234123457", "EAN_13"), "dobry checksum")
            },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Reguły terminów (specyfikacja: funkcje 8, 9; kryteria odbioru)
    // ---------------------------------------------------------------------------------------------
    private fun expiryScenarios(): List<ScenarioResult> {
        val g = "Terminy"
        return listOf(
            scenario(g, "Kubełki: po terminie / dziś / kończące się / OK") {
                val lead = 7
                assertEq(
                    ExpiryBucket.EXPIRED,
                    ExpiryRules.assess(expiry(TODAY_EPOCH_DAY - 1), TODAY_EPOCH_DAY, lead).bucket,
                    "wczoraj",
                )
                assertEq(
                    ExpiryBucket.DUE_TODAY,
                    ExpiryRules.assess(expiry(TODAY_EPOCH_DAY), TODAY_EPOCH_DAY, lead).bucket,
                    "dziś",
                )
                assertEq(
                    ExpiryBucket.EXPIRING_SOON,
                    ExpiryRules.assess(expiry(TODAY_EPOCH_DAY + 5), TODAY_EPOCH_DAY, lead).bucket,
                    "za 5 dni",
                )
                assertEq(
                    ExpiryBucket.OK,
                    ExpiryRules.assess(expiry(TODAY_EPOCH_DAY + 30), TODAY_EPOCH_DAY, lead).bucket,
                    "za 30 dni",
                )
            },
            scenario(g, "Brak daty → kubełek NO_DATE, bez przypomnienia") {
                val a = ExpiryRules.assess(null, TODAY_EPOCH_DAY, 7)
                assertEq(ExpiryBucket.NO_DATE, a.bucket, "kubełek")
                assertTrue(!a.countsForReminder, "brak przypomnienia")
            },
            scenario(g, "Niepotwierdzony odczyt OCR nie tworzy przypomnienia") {
                val unconfirmed = ExpiryDate(
                    epochDay = TODAY_EPOCH_DAY + 3,
                    dayPrecision = DayPrecision.EXACT,
                    type = DateType.BEST_BEFORE,
                    source = DateSource.OCR,
                    confirmed = false,
                )
                val a = ExpiryRules.assess(unconfirmed, TODAY_EPOCH_DAY, 7)
                assertEq(ExpiryBucket.UNCONFIRMED, a.bucket, "kubełek")
                assertTrue(a.needsConfirmation, "wymaga potwierdzenia")
                assertTrue(!a.countsForReminder, "kryterium odbioru: fałszywe przypomnienie")
            },
            scenario(g, "Data tylko z miesiącem (MONTH_ONLY) wymaga potwierdzenia") {
                val monthOnly = ExpiryDate(TODAY_EPOCH_DAY + 20, DayPrecision.MONTH_ONLY, DateType.USE_BY, DateSource.GS1_DATAMATRIX, true)
                val a = ExpiryRules.assess(monthOnly, TODAY_EPOCH_DAY, 7)
                assertEq(ExpiryBucket.UNCONFIRMED, a.bucket, "kubełek")
                assertTrue(!a.countsForReminder, "bez przypomnienia do czasu potwierdzenia")
            },
            scenario(g, "Zużycie jednej partii nie usuwa ani nie zmienia drugiej") {
                val batchA = item(id = 1, barcode = "5901234123457", epochDay = TODAY_EPOCH_DAY + 2)
                val batchB = item(id = 2, barcode = "5901234123457", epochDay = TODAY_EPOCH_DAY + 40)
                val usedA = batchA.copy(status = ItemStatus.USED, statusChangedAt = 1L)
                val aAssess = ExpiryRules.assess(usedA, TODAY_EPOCH_DAY, 7)
                val bAssess = ExpiryRules.assess(batchB, TODAY_EPOCH_DAY, 7)
                assertTrue(!aAssess.countsForReminder, "zużyta partia nie przypomina")
                assertTrue(bAssess.countsForReminder, "druga partia pozostaje aktywna")
                assertEq(2, listOf(batchA, batchB).distinctBy { it.id }.size, "partie są osobnymi wierszami")
            },
            scenario(g, "Rodzaj daty jest zachowany (należy spożyć do vs najlepiej spożyć przed)") {
                val useBy = ExpiryRules.assess(expiry(TODAY_EPOCH_DAY + 1, DateType.USE_BY), TODAY_EPOCH_DAY, 7)
                val bestBefore = ExpiryRules.assess(expiry(TODAY_EPOCH_DAY + 1, DateType.BEST_BEFORE), TODAY_EPOCH_DAY, 7)
                assertEq(DateType.USE_BY, useBy.dateType, "use_by")
                assertEq(DateType.BEST_BEFORE, bestBefore.dateType, "best_before")
                assertTrue(useBy.dateType != bestBefore.dateType, "rozróżnienie rodzajów dat")
            },
            scenario(g, "Filtry widoków: kończące się / po terminie / brak daty") {
                val soon = ExpiryRules.assess(expiry(TODAY_EPOCH_DAY + 2), TODAY_EPOCH_DAY, 7)
                val expired = ExpiryRules.assess(expiry(TODAY_EPOCH_DAY - 4), TODAY_EPOCH_DAY, 7)
                val noDate = ExpiryRules.assess(null, TODAY_EPOCH_DAY, 7)
                assertTrue(ExpiryRules.matchesFilter(soon, ExpiryFilter.EXPIRING), "kończące się")
                assertTrue(!ExpiryRules.matchesFilter(soon, ExpiryFilter.EXPIRED), "kończące się ≠ po terminie")
                assertTrue(ExpiryRules.matchesFilter(expired, ExpiryFilter.EXPIRED), "po terminie")
                assertTrue(ExpiryRules.matchesFilter(noDate, ExpiryFilter.NO_DATE), "brak daty")
                assertTrue(ExpiryRules.matchesFilter(noDate, ExpiryFilter.ALL), "widok wszystkiego")
            },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Przypomnienia (specyfikacja: funkcja 10, brak duplikatów)
    // ---------------------------------------------------------------------------------------------
    private fun reminderScenarios(): List<ScenarioResult> {
        val g = "Przypomnienia"
        val settings = AppSettings(reminderEnabled = true, reminderLeadDays = 7, remindOnDueDay = true)
        return listOf(
            scenario(g, "Plan: slot z wyprzedzeniem i slot w dniu terminu") {
                val slots = ReminderPlanner.plan(item(10, epochDay = TODAY_EPOCH_DAY + 7), TODAY_EPOCH_DAY, settings)
                assertEq(2, slots.size, "liczba slotów")
                assertEq(TODAY_EPOCH_DAY, slots.first { it.kind == ReminderKind.LEAD }.fireEpochDay, "dzień LEAD")
                assertEq(TODAY_EPOCH_DAY + 7, slots.first { it.kind == ReminderKind.DUE }.fireEpochDay, "dzień DUE")
            },
            scenario(g, "Brak daty / brak potwierdzenia → brak przypomnień") {
                assertTrue(ReminderPlanner.plan(item(11, epochDay = null), TODAY_EPOCH_DAY, settings).isEmpty(), "brak daty")
                val unconfirmed = item(12, epochDay = TODAY_EPOCH_DAY + 3).let {
                    it.copy(expiryDate = it.expiryDate!!.copy(confirmed = false, source = DateSource.OCR))
                }
                assertTrue(ReminderPlanner.plan(unconfirmed, TODAY_EPOCH_DAY, settings).isEmpty(), "niepotwierdzona")
            },
            scenario(g, "Wyłączone globalnie lub dla pozycji → brak przypomnień") {
                assertTrue(ReminderPlanner.plan(item(13, epochDay = TODAY_EPOCH_DAY + 3), TODAY_EPOCH_DAY, settings.copy(reminderEnabled = false)).isEmpty(), "globalnie")
                assertTrue(ReminderPlanner.plan(item(14, epochDay = TODAY_EPOCH_DAY + 3).copy(reminderEnabled = false), TODAY_EPOCH_DAY, settings).isEmpty(), "dla pozycji")
            },
            scenario(g, "Zużyte i wyrzucone nie przypominają") {
                assertTrue(ReminderPlanner.plan(item(15, epochDay = TODAY_EPOCH_DAY + 3).copy(status = ItemStatus.USED), TODAY_EPOCH_DAY, settings).isEmpty(), "USED")
                assertTrue(ReminderPlanner.plan(item(16, epochDay = TODAY_EPOCH_DAY + 3).copy(status = ItemStatus.DISCARDED), TODAY_EPOCH_DAY, settings).isEmpty(), "DISCARDED")
            },
            scenario(g, "Edycja daty zmienia klucz → przypomnienie jest aktualizowane, nie dublowane") {
                val before = ReminderPlanner.plan(item(17, epochDay = TODAY_EPOCH_DAY + 10), TODAY_EPOCH_DAY, settings)
                val after = ReminderPlanner.plan(item(17, epochDay = TODAY_EPOCH_DAY + 20), TODAY_EPOCH_DAY, settings)
                assertTrue(before.map { it.key }.intersect(after.map { it.key }.toSet()).isEmpty(), "klucze muszą się różnić")
            },
            scenario(g, "Zmiana wyprzedzenia też zmienia klucz") {
                val k1 = ReminderPlanner.keyFor(1, TODAY_EPOCH_DAY + 10, 7, ReminderKind.LEAD)
                val k2 = ReminderPlanner.keyFor(1, TODAY_EPOCH_DAY + 10, 3, ReminderKind.LEAD)
                assertTrue(k1 != k2, "różne wyprzedzenie → różny klucz")
            },
            scenario(g, "Sloty przeszłe są oznaczane do nadrobienia, przyszłe do zaplanowania") {
                val slots = ReminderPlanner.plan(item(18, epochDay = TODAY_EPOCH_DAY + 2), TODAY_EPOCH_DAY, settings)
                assertTrue(ReminderPlanner.overdueSlots(slots, TODAY_EPOCH_DAY).isNotEmpty(), "LEAD już minął")
                assertTrue(ReminderPlanner.upcomingSlots(slots, TODAY_EPOCH_DAY).isNotEmpty(), "DUE przed nami")
            },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // OCR dat (specyfikacja: funkcje 6, 7, 8)
    // ---------------------------------------------------------------------------------------------
    private fun ocrScenarios(): List<ScenarioResult> {
        val g = "OCR"
        return listOf(
            scenario(g, "„Najlepiej spożyć przed: 12.10.2026” → BEST_BEFORE, wysoka pewność") {
                val r = OcrDateParser.parse(listOf("Najlepiej spożyć przed: 12.10.2026"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(day(2026, 10, 12), c.epochDay, "data")
                assertEq(DateType.BEST_BEFORE, c.type, "rodzaj daty")
                assertEq(DayPrecision.EXACT, c.precision, "precyzja")
                assertEq(OcrConfidence.HIGH, c.confidence, "pewność")
                assertNull(c.issueCode, "brak problemu")
            },
            scenario(g, "„NALEZY SPOZYC DO 2026-11-30” (bez polskich znaków, ISO) → USE_BY") {
                val r = OcrDateParser.parse(listOf("NALEZY SPOZYC DO 2026-11-30"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(day(2026, 11, 30), c.epochDay, "data")
                assertEq(DateType.USE_BY, c.type, "rodzaj daty")
            },
            scenario(g, "Etykieta w poprzedniej linii jest łączona z datą") {
                val r = OcrDateParser.parse(listOf("Data ważności", "15.03.2027"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(day(2027, 3, 15), c.epochDay, "data")
                assertNotNull(c.matchedLabel, "etykieta")
            },
            scenario(g, "„11.2026” → tylko miesiąc, wymaga doprecyzowania") {
                val r = OcrDateParser.parse(listOf("Najlepiej spożyć przed końcem 11.2026"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(DayPrecision.MONTH_ONLY, c.precision, "precyzja")
                assertEq(day(2026, 11, 30), c.epochDay, "ostatni dzień miesiąca")
                assertEq(OcrIssue.MONTH_ONLY, c.issueCode, "kod problemu")
            },
            scenario(g, "Zapis kropkowy 03.04.2027 przy polskiej etykiecie jest jednoznaczny (DD.MM)") {
                val r = OcrDateParser.parse(listOf("Najlepiej spożyć przed 03.04.2027"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(day(2027, 4, 3), c.epochDay, "3 kwietnia 2027")
                assertEq(DayPrecision.EXACT, c.precision, "konwencja DD.MM")
                assertNull(c.issueCode, "brak problemu")
                assertEq(OcrConfidence.HIGH, c.confidence, "pewność")
            },
            scenario(g, "Niejednoznaczny zapis z ukośnikiem 03/04/2027 → niska pewność i wymóg poprawienia") {
                val r = OcrDateParser.parse(listOf("Najlepiej spożyć przed 03/04/2027"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(DayPrecision.AMBIGUOUS_ORDER, c.precision, "precyzja")
                assertEq(OcrIssue.AMBIGUOUS_ORDER, c.issueCode, "kod problemu")
                assertEq(OcrConfidence.LOW, c.confidence, "pewność")
                assertEq(day(2027, 4, 3), c.epochDay, "przyjęto konwencję DD.MM")
            },
            scenario(g, "Zapis MM.DD.RRRR (drugi człon > 12) jest porządkowany i oznaczany") {
                val r = OcrDateParser.parse(listOf("Best before 03/25/2027"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(day(2027, 3, 25), c.epochDay, "data po uporządkowaniu")
                assertEq(OcrIssue.ORDER_MMDD, c.issueCode, "kod problemu")
            },
            scenario(g, "Niemożliwa data 32.13.2026 nie daje kandydata") {
                val r = OcrDateParser.parse(listOf("Najlepiej spożyć przed 32.13.2026"), TODAY_EPOCH_DAY)
                assertTrue(r.candidates.none { it.epochDay == day(2026, 13, 32) }, "brak niemożliwej daty")
                assertTrue(r.candidates.isEmpty(), "żadna data nie została wymyślona: ${r.candidates}")
            },
            scenario(g, "Nieprawdopodobny rok jest oznaczany, a nie ukrywany") {
                val r = OcrDateParser.parse(listOf("Najlepiej spożyć przed 12.10.1899"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(OcrIssue.IMPLAUSIBLE_YEAR, c.issueCode, "kod problemu")
                assertEq(OcrConfidence.LOW, c.confidence, "pewność")
            },
            scenario(g, "Cena i tekst bez etykiety nie tworzą fałszywej daty") {
                val r = OcrDateParser.parse(listOf("Cena 12,99 zł", "Masa netto 500 g", "Kod 5901234123457"), TODAY_EPOCH_DAY)
                assertTrue(r.candidates.isEmpty(), "brak kandydatów, było: ${r.candidates}")
            },
            scenario(g, "MM.RR bez etykiety nie jest datą (ochrona przed numerami partii)") {
                val r = OcrDateParser.parse(listOf("Partia 12/26"), TODAY_EPOCH_DAY)
                assertTrue(r.candidates.isEmpty(), "bez etykiety nie zgadujemy: ${r.candidates}")
            },
            scenario(g, "MM.RR z etykietą jest kandydatem o niskiej pewności") {
                val r = OcrDateParser.parse(listOf("Najlepiej spożyć przed 12/26"), TODAY_EPOCH_DAY)
                val c = assertNotNullBest(r)
                assertEq(DayPrecision.MONTH_ONLY, c.precision, "precyzja")
                assertEq(day(2026, 12, 31), c.epochDay, "grudzień 2026")
            },
            scenario(g, "Wiele kandydatów jest sortowane: pewne i najbliższe dziś najpierw") {
                val r = OcrDateParser.parse(
                    listOf("Najlepiej spożyć przed 20.10.2026", "Partia 03.04.2027", "Seria 12.2099"),
                    TODAY_EPOCH_DAY,
                )
                assertTrue(r.candidates.size >= 2, "co najmniej dwie daty: ${r.candidates.size}")
                assertEq(day(2026, 10, 20), r.candidates.first().epochDay, "pierwsza = pewna i najbliższa")
            },
            scenario(g, "Pusty wsad OCR nie wysypuje się") {
                assertTrue(OcrDateParser.parse(emptyList(), TODAY_EPOCH_DAY).candidates.isEmpty(), "pusta lista")
                assertTrue(OcrDateParser.parse(listOf("", "   "), TODAY_EPOCH_DAY).candidates.isEmpty(), "puste linie")
            },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Formatowanie PL i CSV (specyfikacja: poprawne formatowanie liczb i dat, eksport CSV)
    // ---------------------------------------------------------------------------------------------
    private fun formatScenarios(): List<ScenarioResult> {
        val g = "Formatowanie"
        return listOf(
            scenario(g, "Data w formacie dd.MM.yyyy") {
                assertEq("12.10.2026", PlFormat.formatDate(TODAY_EPOCH_DAY), "data")
            },
            scenario(g, "Ilości: polski przecinek, bez zbędnych zer") {
                assertEq("1,5", PlFormat.formatQuantity(1.5), "1,5")
                assertEq("3", PlFormat.formatQuantity(3.0), "3")
                assertEq("250", PlFormat.formatQuantity(250.0), "250")
                assertEq("0,25", PlFormat.formatQuantity(0.25), "0,25")
                assertEq("", PlFormat.formatQuantity(null), "null")
                assertEq("1,5 kg", PlFormat.formatQuantityWithUnit(1.5, "kg"), "z jednostką")
            },
            scenario(g, "Odległość w dniach po polsku") {
                assertEq("dziś", PlFormat.formatDayDistance(0), "0")
                assertEq("jutro", PlFormat.formatDayDistance(1), "1")
                assertEq("wczoraj", PlFormat.formatDayDistance(-1), "-1")
                assertEq("za 3 dni", PlFormat.formatDayDistance(3), "3")
                assertEq("5 dni temu", PlFormat.formatDayDistance(-5), "-5")
            },
            scenario(g, "Data tylko z miesiącem nie udaje dokładnej") {
                val text = PlFormat.formatExpiry(ExpiryDate(day(2026, 11, 30), DayPrecision.MONTH_ONLY))
                assertTrue(text.startsWith("11.2026"), "zaczyna się od MM.RRRR, było: $text")
                assertTrue(!text.startsWith("30.11.2026"), "nie udaje dokładnego dnia")
            },
            scenario(g, "CSV: BOM, CRLF, separator i ucieczki") {
                val csv = CsvExport.exportItems(
                    listOf(item(21, epochDay = TODAY_EPOCH_DAY + 5, name = "Mleko; wiejskie 2%")),
                    columns(),
                    labels(),
                )
                assertTrue(csv.startsWith(CsvExport.BOM), "BOM UTF-8")
                assertTrue(csv.contains("\r\n"), "CRLF")
                assertTrue(csv.contains("\"Mleko; wiejskie 2%\""), "cytat przy separatorze w wartości")
                assertEq(2, csv.split("\r\n").filter { it.isNotBlank() }.size, "nagłówek + 1 wiersz")
            },
            scenario(g, "CSV: cudzysłów w nazwie jest podwajany") {
                assertEq("\"Kasza \"\"gryczana\"\"\"", CsvExport.escape("Kasza \"gryczana\""), "eskejping")
                assertEq("zwykła", CsvExport.escape("zwykła"), "bez eskejpingu")
                assertEq("", CsvExport.escape(null), "null")
            },
            scenario(g, "CSV: dwie partie tego samego kodu dają dwa wiersze") {
                val items = listOf(
                    item(22, barcode = "5901234123457", epochDay = TODAY_EPOCH_DAY + 2),
                    item(23, barcode = "5901234123457", epochDay = TODAY_EPOCH_DAY + 60),
                )
                val csv = CsvExport.exportItems(items, columns(), labels())
                val rows = csv.split("\r\n").filter { it.isNotBlank() }
                assertEq(3, rows.size, "nagłówek + 2 partie")
                assertEq(2, rows.count { "5901234123457" in it }, "oba wiersze z tym samym kodem")
            },
            scenario(g, "CSV listy zakupowej") {
                val csv = CsvExport.exportShoppingList(
                    listOf(ShoppingItem(1, "Jajka", 10.0, "szt.", Section.PANTRY, false, null, 0L, null)),
                    "Nazwa", "Ilość", "Jednostka", "Sekcja", "Kupione", "Notatka",
                    labels(),
                )
                assertTrue(csv.contains("Jajka"), "nazwa")
                assertTrue(csv.contains("Spiżarnia"), "etykieta sekcji z zasobów")
            },
        )
    }


    // ---------------------------------------------------------------------------------------------
    // Kopia zapasowa: walidacja niezaufanego pliku (specyfikacja: limity, ścieżki, sumy kontrolne)
    // ---------------------------------------------------------------------------------------------
    private fun backupScenarios(): List<ScenarioResult> {
        val g = "Kopia zapasowa"
        return listOf(
            scenario(g, "Niebezpieczne ścieżki ZIP są odrzucane (zip-slip)") {
                val bad = listOf(
                    "../evil.json",
                    "../../secret",
                    "/etc/passwd",
                    "photos/../../outside.jpg",
                    "C:\\Windows\\system32",
                    "backup\\items.json",
                    "",
                    "   ",
                    "items.json\u0000.jpg",
                    "unexpected.txt",
                    "deep/nested/photos/x.jpg",
                )
                bad.forEach { path ->
                    assertTrue(!BackupValidator.isSafeEntryPath(path), "ścieżka <$path> musi być odrzucona")
                }
            },
            scenario(g, "Poprawne ścieżki archiwum są akceptowane") {
                val good = listOf(
                    "manifest.json",
                    "items.json",
                    "barcode_names.json",
                    "shopping.json",
                    "settings.json",
                    "reminder_log.json",
                    "photos/8f14e45f-ea5c-4b1a.jpg",
                )
                good.forEach { path ->
                    assertTrue(BackupValidator.isSafeEntryPath(path), "ścieżka <$path> musi być przyjęta")
                }
            },
            scenario(g, "Struktura archiwum: przekroczenie limitów") {
                val tooMany = (1..BackupContract.MAX_ENTRIES + 1).map {
                    ArchiveEntry("photos/f$it.jpg", 10L, 5L, false)
                }
                val r1 = BackupValidator.validateArchiveStructure(tooMany)
                assertTrue(r1 is BackupValidation.Invalid && r1.code == BackupContract.Error.TOO_MANY_ENTRIES, "limit wpisów: $r1")

                val tooBig = listOf(ArchiveEntry("items.json", BackupContract.MAX_JSON_ENTRY_BYTES + 1, 10L, false))
                val r2 = BackupValidator.validateArchiveStructure(tooBig)
                assertTrue(r2 is BackupValidation.Invalid && r2.code == BackupContract.Error.ENTRY_TOO_LARGE, "limit rozmiaru wpisu: $r2")

                val bomb = listOf(ArchiveEntry("items.json", 50L * 1024 * 1024, 1024L, false))
                val r3 = BackupValidator.validateArchiveStructure(bomb)
                assertTrue(r3 is BackupValidation.Invalid, "bomba/limit: $r3")
            },
            scenario(g, "Współczynnik kompresji: bomba ZIP jest odrzucana") {
                val bomb = listOf(ArchiveEntry("items.json", 20L * 1024 * 1024, 1024L, false))
                val r = BackupValidator.validateArchiveStructure(bomb)
                assertTrue(
                    r is BackupValidation.Invalid &&
                        (r.code == BackupContract.Error.COMPRESSION_BOMB || r.code == BackupContract.Error.ENTRY_TOO_LARGE),
                    "oczekiwano odrzucenia bomby, było: $r",
                )
            },
            scenario(g, "Poprawna struktura archiwum przechodzi") {
                val ok = listOf(
                    ArchiveEntry("manifest.json", 400L, 200L, false),
                    ArchiveEntry("items.json", 20_000L, 4_000L, false),
                    ArchiveEntry("photos/abc-1.jpg", 300_000L, 280_000L, false),
                )
                assertNull(BackupValidator.validateArchiveStructure(ok), "struktura OK")
            },
            scenario(g, "Manifest z nowszego formatu jest odrzucany") {
                val r = BackupValidator.validateManifest(manifest(formatVersion = BackupContract.FORMAT_VERSION + 1))
                assertTrue(r is BackupValidation.Invalid && r.code == BackupContract.Error.FORMAT_TOO_NEW, "$r")
            },
            scenario(g, "Manifest ze starszego wspieranego formatu: przyjęty z ostrzeżeniem") {
                val r = BackupValidator.validateManifest(manifest(formatVersion = 1), 1..2)
                assertTrue(r is BackupValidation.Ok, "$r")
                assertTrue((r as BackupValidation.Ok).warnings.contains("FORMAT_OLDER"), "ostrzeżenie o starszym formacie")
            },
            scenario(g, "Manifest z innej aplikacji jest odrzucany") {
                val r = BackupValidator.validateManifest(manifest(app = "com.inna.aplikacja"))
                assertTrue(r is BackupValidation.Invalid && r.code == BackupContract.Error.WRONG_APP, "$r")
            },
            scenario(g, "Zbyt wiele pozycji w kopii jest odrzucane") {
                val r = BackupValidator.validateManifest(manifest(counts = mapOf("items" to BackupContract.MAX_ITEMS + 1)))
                assertTrue(r is BackupValidation.Invalid && r.code == BackupContract.Error.TOO_MANY_ITEMS, "$r")
            },
            scenario(g, "Pusta kopia jest przyjęta, ale z ostrzeżeniem") {
                val r = BackupValidator.validateManifest(manifest(counts = mapOf("items" to 0, "shopping" to 0)))
                assertTrue(r is BackupValidation.Ok, "$r")
                assertTrue((r as BackupValidation.Ok).warnings.contains("EMPTY_BACKUP"), "ostrzeżenie o pustej kopii")
            },
            scenario(g, "Suma kontrolna SHA-256") {
                val bytes = "terminownik".toByteArray()
                val hex = BackupValidator.sha256Hex(bytes)
                assertTrue(BackupValidator.verifyChecksum(hex, bytes), "poprawna suma")
                assertTrue(BackupValidator.verifyChecksum(hex.uppercase(), bytes), "wielkość liter nie ma znaczenia")
                assertTrue(!BackupValidator.verifyChecksum(hex, "inne".toByteArray()), "inna zawartość")
                assertEq(64, hex.length, "długość hex")
            },
            scenario(g, "Nazwy plików zdjęć") {
                assertTrue(BackupValidator.isSafePhotoFileName("abc-123.jpg"), "poprawna")
                assertTrue(!BackupValidator.isSafePhotoFileName("../x.jpg"), "ścieżka")
                assertTrue(!BackupValidator.isSafePhotoFileName("ABC.JPG"), "wielkie litery")
                assertTrue(!BackupValidator.isSafePhotoFileName("a.png"), "złe rozszerzenie")
                assertTrue(!BackupValidator.isSafePhotoFileName(""), "pusta")
            },
            scenario(g, "Daty z kopii: zakres wiarygodności") {
                assertTrue(BackupValidator.isPlausibleEpochDay(TODAY_EPOCH_DAY + 30, TODAY_EPOCH_DAY), "za miesiąc")
                assertTrue(BackupValidator.isPlausibleEpochDay(TODAY_EPOCH_DAY - 300, TODAY_EPOCH_DAY), "w przeszłości")
                assertTrue(!BackupValidator.isPlausibleEpochDay(1L, TODAY_EPOCH_DAY), "rok 1970 + 1 dzień")
                assertTrue(!BackupValidator.isPlausibleEpochDay(TODAY_EPOCH_DAY + 365 * 200, TODAY_EPOCH_DAY), "200 lat wprzód")
            },
            scenario(g, "Sanityzacja tekstu i liczb z kopii") {
                assertEq("Mleko", BackupValidator.sanitizeText("  Mleko\u0000  "), "znaki sterujące i białe")
                assertNull(BackupValidator.sanitizeText("   "), "tylko spacje")
                assertNull(BackupValidator.sanitizeText(null), "null")
                assertEq("ab", BackupValidator.sanitizeText("abcdefgh", 2), "cięcie do limitu")
                assertNull(BackupValidator.sanitizeQuantity(Double.NaN), "NaN")
                assertNull(BackupValidator.sanitizeQuantity(-1.0), "ujemna")
                assertNull(BackupValidator.sanitizeQuantity(Double.POSITIVE_INFINITY), "nieskończoność")
                assertEq(2.5, BackupValidator.sanitizeQuantity(2.5), "poprawna")
            },
        )
    }

    private fun manifest(
        formatVersion: Int = BackupContract.FORMAT_VERSION,
        app: String = BackupContract.APP_ID,
        counts: Map<String, Int> = mapOf("items" to 3, "shopping" to 1),
    ) = BackupManifestData(
        formatVersion = formatVersion,
        app = app,
        appVersionName = "1.0.0",
        createdAtEpochMillis = 1_760_000_000_000L,
        timezone = "Europe/Warsaw",
        counts = counts,
        entries = listOf(BackupEntryInfo(BackupContract.MANIFEST_PATH, "abc", 12L)),
    )

    private fun assertNotNullBest(result: OcrParseResult): OcrDateCandidate {
        val best = result.best
        if (best == null) throw AssertionError("brak kandydata na datę (linie: ${result.linesScanned})")
        return best
    }

    private fun expiry(epochDay: Long?, type: DateType = DateType.BEST_BEFORE): ExpiryDate? =
        epochDay?.let { ExpiryDate(it, DayPrecision.EXACT, type, DateSource.MANUAL, true) }

    private fun item(
        id: Long,
        name: String = "Produkt testowy",
        barcode: String? = null,
        epochDay: Long? = TODAY_EPOCH_DAY + 10,
    ): PantryItem = PantryItem(
        id = id,
        section = Section.PANTRY,
        name = name,
        quantity = 1.0,
        unit = "szt.",
        barcode = barcode,
        expiryDate = expiry(epochDay),
        status = ItemStatus.ACTIVE,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun columns() = CsvExport.ItemColumns(
        section = "Sekcja",
        name = "Nazwa",
        quantity = "Ilość",
        unit = "Jednostka",
        location = "Miejsce przechowywania",
        batch = "Partia",
        barcode = "Kod kreskowy",
        expiry = "Data ważności",
        dateType = "Rodzaj daty",
        dateSource = "Źródło daty",
        confirmed = "Data potwierdzona",
        status = "Status",
        note = "Notatka",
        createdAt = "Utworzono",
        updatedAt = "Zaktualizowano",
    )

    private fun labels() = CsvExport.Labels(
        pantry = "Spiżarnia",
        medicine = "Apteczka",
        useBy = "Należy spożyć do",
        bestBefore = "Najlepiej spożyć przed",
        unspecified = "Nie określono",
        sourceManual = "Wpis ręczny",
        sourceGs1 = "GS1 DataMatrix",
        sourceOcr = "OCR etykiety",
        sourceImport = "Kopia zapasowa",
        statusActive = "Aktywna",
        statusUsed = "Zużyta",
        statusDiscarded = "Wyrzucona",
        yes = "tak",
        no = "nie",
    )

    /** Uruchamia wszystkie scenariusze i zwraca wyniki. */
    fun runAll(): List<ScenarioResult> =
        gs1Scenarios() + barcodeScenarios() + expiryScenarios() + reminderScenarios() +
        ocrScenarios() + formatScenarios() + backupScenarios()

    fun failures(results: List<ScenarioResult> = runAll()): List<ScenarioResult> = results.filterNot { it.passed }
}
