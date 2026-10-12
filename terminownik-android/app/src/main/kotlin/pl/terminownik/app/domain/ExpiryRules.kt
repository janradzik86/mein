package pl.terminownik.app.domain

/** Kubełki widoków wymaganych przez specyfikację (funkcja 9). */
enum class ExpiryBucket {
    /** Data minęła – produkt po terminie. */
    EXPIRED,

    /** Termin mija dziś. */
    DUE_TODAY,

    /** Termin w zasięgu skonfigurowanego wyprzedzenia przypomnienia. */
    EXPIRING_SOON,

    /** Termin odległy. */
    OK,

    /** Brak daty ważności. */
    NO_DATE,

    /** Data odczytana automatycznie, jeszcze niepotwierdzona przez użytkownika. */
    UNCONFIRMED,
}

/**
 * Wynik oceny terminu.
 *
 * @param countsForReminder czy pozycja kwalifikuje się do przypomnienia.
 *   Niepotwierdzony lub niejednoznaczny odczyt NIGDY nie tworzy przypomnienia
 *   (kryterium odbioru: „Niepoprawny odczyt daty nie tworzy fałszywego przypomnienia”).
 */
data class ExpiryAssessment(
    val bucket: ExpiryBucket,
    val daysFromToday: Long?,
    val dateType: DateType?,
    val countsForReminder: Boolean,
    val needsConfirmation: Boolean,
)

object ExpiryRules {

    fun assess(item: PantryItem, todayEpochDay: Long, leadDays: Int): ExpiryAssessment =
        assess(item.expiryDate, todayEpochDay, leadDays, item.status, item.reminderEnabled)

    fun assess(
        expiry: ExpiryDate?,
        todayEpochDay: Long,
        leadDays: Int,
        status: ItemStatus = ItemStatus.ACTIVE,
        reminderEnabled: Boolean = true,
    ): ExpiryAssessment {
        if (expiry == null) {
            return ExpiryAssessment(ExpiryBucket.NO_DATE, null, null, countsForReminder = false, needsConfirmation = false)
        }
        val days = expiry.epochDay - todayEpochDay
        if (!expiry.confirmed || expiry.dayPrecision != DayPrecision.EXACT) {
            return ExpiryAssessment(
                bucket = ExpiryBucket.UNCONFIRMED,
                daysFromToday = days,
                dateType = expiry.type,
                countsForReminder = false,
                needsConfirmation = true,
            )
        }
        val lead = leadDays.coerceAtLeast(0).toLong()
        val bucket = when {
            days < 0 -> ExpiryBucket.EXPIRED
            days == 0L -> ExpiryBucket.DUE_TODAY
            days <= lead -> ExpiryBucket.EXPIRING_SOON
            else -> ExpiryBucket.OK
        }
        // Przypominamy tylko o pozycjach aktywnych, z terminem dzisiejszym lub przyszłym.
        val counts = status == ItemStatus.ACTIVE && reminderEnabled && days >= 0
        return ExpiryAssessment(bucket, days, expiry.type, countsForReminder = counts, needsConfirmation = false)
    }

    /** Czy pozycja mieści się w danym widoku (kończące się / po terminie / brak daty). */
    fun matchesFilter(assessment: ExpiryAssessment, filter: ExpiryFilter): Boolean = when (filter) {
        ExpiryFilter.ALL -> true
        ExpiryFilter.EXPIRING -> assessment.bucket == ExpiryBucket.EXPIRING_SOON ||
            assessment.bucket == ExpiryBucket.DUE_TODAY
        ExpiryFilter.EXPIRED -> assessment.bucket == ExpiryBucket.EXPIRED
        ExpiryFilter.NO_DATE -> assessment.bucket == ExpiryBucket.NO_DATE
        ExpiryFilter.UNCONFIRMED -> assessment.bucket == ExpiryBucket.UNCONFIRMED
    }

    /**
     * Sortowanie listy: najpierw to, co wymaga działania (po terminie, dziś, kończące się),
     * potem reszta rosnąco po liczbie dni; na końcu pozycje bez daty i niepotwierdzone.
     */
    fun sortKey(assessment: ExpiryAssessment): Long = when (assessment.bucket) {
        ExpiryBucket.EXPIRED -> -1_000_000L + (assessment.daysFromToday ?: 0L)
        ExpiryBucket.DUE_TODAY -> 0L
        ExpiryBucket.EXPIRING_SOON -> assessment.daysFromToday ?: 1L
        ExpiryBucket.OK -> assessment.daysFromToday ?: Long.MAX_VALUE / 2
        ExpiryBucket.UNCONFIRMED -> Long.MAX_VALUE - 2
        ExpiryBucket.NO_DATE -> Long.MAX_VALUE - 1
    }
}

enum class ExpiryFilter { ALL, EXPIRING, EXPIRED, NO_DATE, UNCONFIRMED }
