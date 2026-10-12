package pl.terminownik.app.domain

/** Pozycja wraz z oceną terminu – model używany bezpośrednio przez listy w UI. */
data class ItemWithExpiry(
    val item: PantryItem,
    val assessment: ExpiryAssessment,
)

/** Podsumowanie liczników sekcji (badge na nawigacji). */
data class SectionSummary(
    val section: Section,
    val activeCount: Int,
    val expiringSoon: Int,
    val expired: Int,
    val noDate: Int,
    val unconfirmed: Int,
)
