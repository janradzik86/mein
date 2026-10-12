package pl.terminownik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy logiki biznesowej uruchamiane przez JUnit w `./gradlew test`.
 *
 * Scenariusze są współdzielone z lokalnym runnerem (`tools/verify-domain.sh`), więc ten sam
 * zestaw reguł jest weryfikowany zarówno bez Gradle (lokalnie), jak i w CI.
 */
class DomainScenariosTest {

    private val results: List<ScenarioResult> = DomainScenarios.runAll()

    @Test
    fun `wszystkie scenariusze biznesowe są zaliczone`() {
        val failures = DomainScenarios.failures(results)
        assertTrue(
            "Nieudane scenariusze (${failures.size}):\n" +
                failures.joinToString("\n") { " - ${it.group} / ${it.name}: ${it.message}" },
            failures.isEmpty(),
        )
    }

    @Test
    fun `zestaw scenariuszy obejmuje wszystkie grupy funkcjonalne`() {
        val groups = results.map { it.group }.toSet()
        listOf(
            "GS1",
            "Kody",
            "Terminy",
            "Przypomnienia",
            "OCR",
            "Formatowanie",
            "Kopia zapasowa",
        ).forEach { expected ->
            assertTrue(
                "Brak grupy scenariuszy: $expected (są: ${groups.sorted()})",
                groups.any { it.contains(expected, ignoreCase = true) },
            )
        }
    }

    @Test
    fun `liczba scenariuszy nie zmniejszyła się`() {
        // Ochrona przed przypadkowym usunięciem pokrycia testowego.
        assertTrue("Oczekiwano co najmniej 67 scenariuszy, jest ${results.size}", results.size >= 67)
    }

    @Test
    fun `każdy scenariusz ma nazwę i grupę`() {
        results.forEach { result ->
            assertTrue(result.name.isNotBlank())
            assertTrue(result.group.isNotBlank())
        }
        assertEquals(results.size, results.distinctBy { "${it.group}/${it.name}" }.size)
    }
}
