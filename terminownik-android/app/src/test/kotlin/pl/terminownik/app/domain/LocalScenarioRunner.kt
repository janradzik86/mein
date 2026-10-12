package pl.terminownik.app.domain

/**
 * Uruchamia scenariusze bez Gradle i bez JUnit – używane przez `tools/verify-domain.sh`
 * do lokalnej weryfikacji logiki biznesowej (patrz TEST_REPORT.md).
 *
 * Nie jest częścią APK: leży w sourceSet testów, a w buildzie release nie jest kompilowane.
 */
object LocalScenarioRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val results = DomainScenarios.runAll()
        var currentGroup = ""
        results.forEach { r ->
            if (r.group != currentGroup) {
                currentGroup = r.group
                println("")
                println("== $currentGroup ==")
            }
            val mark = if (r.passed) "PASS" else "FAIL"
            println("  [$mark] ${r.name}${r.message?.let { "  ->  $it" } ?: ""}")
        }
        val failed = results.filterNot { it.passed }
        println("")
        println("Razem: ${results.size}, zaliczone: ${results.size - failed.size}, nieudane: ${failed.size}")
        if (failed.isNotEmpty()) {
            println("NIEUDANE SCENARIUSZE:")
            failed.forEach { println(" - ${it.group} / ${it.name}: ${it.message}") }
            kotlin.system.exitProcess(1)
        }
        println("Wszystkie scenariusze logiki biznesowej zaliczone.")
    }
}
