package pl.terminownik.app.domain

/** Rodzaj przypomnienia: z wyprzedzeniem albo w dniu terminu. */
enum class ReminderKind { LEAD, DUE }

/**
 * Slot przypomnienia. [key] jest identyfikatorem antyduplikacyjnym: zmiana daty, wyprzedzenia
 * albo rodzaju przypomnienia zmienia klucz, więc przypomnienie zostanie zaplanowane od nowa,
 * a już wysłane nie zostanie powtórzone (specyfikacja: „Nie wysyłaj duplikatów”,
 * „Edycja lub usunięcie zdarzenia ma aktualizować jego przypomnienie”).
 */
data class ReminderSlot(
    val key: String,
    val itemId: Long,
    val itemName: String,
    val section: Section,
    val expiryEpochDay: Long,
    val fireEpochDay: Long,
    val kind: ReminderKind,
    val dateType: DateType,
) {
    fun isPast(todayEpochDay: Long): Boolean = fireEpochDay < todayEpochDay
}

object ReminderPlanner {

    fun keyFor(itemId: Long, expiryEpochDay: Long, leadDays: Int, kind: ReminderKind): String =
        "i$itemId|e$expiryEpochDay|l$leadDays|${kind.name}"

    /**
     * Planuje przypomnienia dla jednej pozycji.
     *
     * Zwraca pustą listę, gdy:
     *  - przypomnienia są wyłączone globalnie lub dla pozycji,
     *  - pozycja nie jest aktywna (zużyta/wyrzucona),
     *  - brak daty,
     *  - data pochodzi z odczytu automatycznego i NIE została potwierdzona
     *    (kryterium odbioru: niepoprawny odczyt nie tworzy fałszywego przypomnienia).
     */
    fun plan(item: PantryItem, todayEpochDay: Long, settings: AppSettings): List<ReminderSlot> {
        if (!settings.reminderEnabled || !item.reminderEnabled) return emptyList()
        if (item.status != ItemStatus.ACTIVE) return emptyList()
        val expiry = item.expiryDate ?: return emptyList()
        if (!expiry.confirmed || expiry.dayPrecision != DayPrecision.EXACT) return emptyList()

        val slots = mutableListOf<ReminderSlot>()
        val lead = settings.reminderLeadDays.coerceIn(0, 365)

        if (lead > 0) {
            val fireDay = expiry.epochDay - lead
            slots += ReminderSlot(
                key = keyFor(item.id, expiry.epochDay, lead, ReminderKind.LEAD),
                itemId = item.id,
                itemName = item.name,
                section = item.section,
                expiryEpochDay = expiry.epochDay,
                fireEpochDay = fireDay,
                kind = ReminderKind.LEAD,
                dateType = expiry.type,
            )
        }
        if (settings.remindOnDueDay) {
            slots += ReminderSlot(
                key = keyFor(item.id, expiry.epochDay, lead, ReminderKind.DUE),
                itemId = item.id,
                itemName = item.name,
                section = item.section,
                expiryEpochDay = expiry.epochDay,
                fireEpochDay = expiry.epochDay,
                kind = ReminderKind.DUE,
                dateType = expiry.type,
            )
        }
        return slots
    }

    fun planAll(items: List<PantryItem>, todayEpochDay: Long, settings: AppSettings): List<ReminderSlot> =
        items.flatMap { plan(it, todayEpochDay, settings) }

    /** Sloty do natychmiastowego nadrobienia po ponownym otwarciu aplikacji lub restarcie telefonu. */
    fun overdueSlots(slots: List<ReminderSlot>, todayEpochDay: Long): List<ReminderSlot> =
        slots.filter { it.isPast(todayEpochDay) }

    /** Sloty do zaplanowania alarmem (dziś lub w przyszłości). */
    fun upcomingSlots(slots: List<ReminderSlot>, todayEpochDay: Long): List<ReminderSlot> =
        slots.filterNot { it.isPast(todayEpochDay) }
}
