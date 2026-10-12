package pl.terminownik.app

import android.content.Context
import pl.terminownik.app.data.PantryRepository
import pl.terminownik.app.data.backup.BackupRepository
import pl.terminownik.app.data.local.TerminownikDatabase
import pl.terminownik.app.data.photo.PhotoStorage
import pl.terminownik.app.data.settings.SettingsRepository
import pl.terminownik.app.notifications.ReminderNotification
import pl.terminownik.app.domain.OcrDateCandidate
import pl.terminownik.app.notifications.ReminderScheduler
import pl.terminownik.app.scan.BarcodeInterpretation

/**
 * Ręczny kontener zależności (bez frameworka DI).
 *
 * Świadomy wybór: aplikacja ma jeden moduł i kilkanaście zależności, więc prosty
 * kontener jest łatwiejszy do zrozumienia, szybszy w uruchomieniu i nie wymaga
 * generatora kodu. Rozdział UI / logika / dane jest zachowany: warstwy UI korzystają
 * wyłącznie z repozytoriów, a logika domenowa nie zna Androida.
 */
class AppContainer(private val context: Context) {

    val database: TerminownikDatabase by lazy { TerminownikDatabase.get(context) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val photoStorage: PhotoStorage by lazy { PhotoStorage(context) }

    val reminderNotification: ReminderNotification by lazy { ReminderNotification(context) }

    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(context, database, settingsRepository, reminderNotification)
    }

    val pantryRepository: PantryRepository by lazy {
        PantryRepository(database, settingsRepository, photoStorage, reminderScheduler)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(context, database, settingsRepository, photoStorage, reminderScheduler)
    }

    /**
     * Jednorazowy bufor przekazania wyniku skanowania/OCR do formularza.
     *
     * Nawigacja Compose przekazuje argumenty jako typy proste, a wynik skanu jest obiektem,
     * więc stosujemy bufor w kontenerze: zapis przy przejściu, odczyt i wyczyszczenie w formularzu.
     * Aplikacja jest jednoprocessowa, więc `@Volatile` wystarcza.
     */
    @Volatile
    var pendingScan: BarcodeInterpretation? = null

    @Volatile
    var pendingOcr: OcrDateCandidate? = null

    fun consumePendingScan(): BarcodeInterpretation? = pendingScan.also { pendingScan = null }

    fun consumePendingOcr(): OcrDateCandidate? = pendingOcr.also { pendingOcr = null }
}
