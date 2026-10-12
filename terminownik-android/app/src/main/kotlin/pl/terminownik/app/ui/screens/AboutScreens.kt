package pl.terminownik.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.terminownik.app.BuildConfig
import pl.terminownik.app.R
import pl.terminownik.app.ui.components.NoticeBar
import pl.terminownik.app.ui.components.NoticeTone

/** Wspólna ramka ekranu informacyjnego z przyciskiem „wstecz”. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(
    title: String,
    content: @Composable () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** O aplikacji: czym jest, co robi, czego nie robi, wersja i zastrzeżenia. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    InfoScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.labelMedium,
        )
        HorizontalDivider()
        Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyMedium)
        NoticeBar(
            text = stringResource(R.string.settings_offline_note),
            icon = Icons.Filled.Info,
            tone = NoticeTone.INFO,
        )
        HorizontalDivider()
        Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
        DisclaimerList()
    }
}

/** Zastrzeżenia wymagane przez specyfikację (żywność, leki, kody, daty niepotwierdzone). */
@Composable
fun DisclaimerList() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.disclaimer_food,
            R.string.disclaimer_medicine,
            R.string.disclaimer_barcodes,
            R.string.disclaimer_unconfirmed,
            R.string.disclaimer_month_only,
        ).forEach { res ->
            Row {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp).height(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Prywatność: prawdziwy opis przepływu danych (aplikacja w pełni lokalna). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    InfoScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) {
        NoticeBar(
            text = stringResource(R.string.settings_offline_note),
            icon = Icons.Filled.Info,
            tone = NoticeTone.INFO,
        )
        listOf(
            R.string.privacy_1,
            R.string.privacy_2,
            R.string.privacy_3,
            R.string.privacy_4,
            R.string.privacy_5,
            R.string.privacy_6,
            R.string.privacy_7,
            R.string.privacy_8,
            R.string.privacy_9,
        ).forEachIndexed { index, res ->
            Column {
                Text(
                    text = "${index + 1}. ${stringResource(res)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
        DisclaimerList()
    }
}

/** Biblioteki i licencje – zgodne z faktycznymi zależnościami z `gradle/libs.versions.toml`. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    InfoScaffold(title = stringResource(R.string.licenses_title), onBack = onBack) {
        Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
        LIBRARIES.forEach { (name, license) ->
            Column(Modifier.fillMaxWidth()) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.licenses_data_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Rzeczywista lista zależności runtime (bez bibliotek testowych).
 * Wszystkie są na licencjach Apache-2.0, z wyjątkiem ML Kit (warunki Google) i
 * kotlin-stdlib (Apache-2.0).
 */
private val LIBRARIES = listOf(
    "Kotlin + kotlin-stdlib (JetBrains)" to "Apache License 2.0",
    "Jetpack Compose + Material 3 (AndroidX)" to "Apache License 2.0",
    "AndroidX Core KTX, Activity, Lifecycle, ViewModel" to "Apache License 2.0",
    "AndroidX Navigation Compose" to "Apache License 2.0",
    "AndroidX Room (baza SQLite)" to "Apache License 2.0",
    "AndroidX DataStore Preferences (ustawienia)" to "Apache License 2.0",
    "AndroidX WorkManager (porządkowanie przypomnień)" to "Apache License 2.0",
    "AndroidX Core Splashscreen" to "Apache License 2.0",
    "Kotlinx Coroutines" to "Apache License 2.0",
    "Kotlinx Serialization JSON (format kopii)" to "Apache License 2.0",
    "CameraX (podgląd i zdjęcie etykiety)" to "Apache License 2.0",
    "ML Kit Barcode Scanning (modele wbudowane)" to "Google APIs Terms of Service / Apache 2.0 (zależności)",
    "ML Kit Text Recognition (model łaciński, wbudowany)" to "Google APIs Terms of Service / Apache 2.0 (zależności)",
    "Coil (wczytywanie zdjęć)" to "Apache License 2.0",
    "Desugar JDK libs" to "GNU GPL v2 z Classpath Exception",
)
