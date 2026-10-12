package pl.terminownik.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.terminownik.app.domain.AppSettings
import pl.terminownik.app.ui.TerminownikApp
import pl.terminownik.app.ui.theme.TerminownikTheme

/**
 * Jedyna aktywność aplikacji (single-activity + Compose Navigation).
 *
 * Ekran startowy: systemowy splash (core-splashscreen) trzymany do wczytania ustawień,
 * a następnie krótkie wdrożenie użytkownika przy pierwszym uruchomieniu.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Identyfikator pozycji do otwarcia po tapnięciu w przypomnienie. */
        const val EXTRA_ITEM_ID = "extra_item_id"

        /** Otwarcie widoku terminów z powiadomienia zbiorczego. */
        const val EXTRA_OPEN_DEADLINES = "extra_open_deadlines"
    }

    private var uiReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !uiReady }

        val container = (application as TerminownikApplication).container
        enableEdgeToEdge()

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            TerminownikTheme(themeMode = settings.themeMode) {
                TerminownikApp(
                    container = container,
                    settings = settings,
                    onReady = { uiReady = true },
                    startItemId = intentItemId(),
                    startOnDeadlines = intent?.getBooleanExtra(EXTRA_OPEN_DEADLINES, false) == true,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun intentItemId(): Long = intent?.getLongExtra(EXTRA_ITEM_ID, -1L) ?: -1L
}
