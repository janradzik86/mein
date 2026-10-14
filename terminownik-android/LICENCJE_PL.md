# Biblioteki, licencje i źródła danych — Terminownik 1.0.0

Lista zgodna z `gradle/libs.versions.toml` (wszystkie wersje przypięte, bez `+` i bez wydań alpha/beta).

## 1. Narzędzia buildu

| Narzędzie | Wersja | Licencja |
| --- | --- | --- |
| Android Gradle Plugin | 9.3.2 | Apache-2.0 |
| Kotlin | 2.3.0 | Apache-2.0 |
| KSP | 2.3.4 | Apache-2.0 |
| Gradle Wrapper | 9.7.1 (+ `distributionSha256Sum`) | Apache-2.0 |
| Desugar JDK libs | 2.1.4 | GNU GPL v2 z Classpath Exception |

## 2. Biblioteki uruchomieniowe

| Biblioteka | Wersja | Licencja | Rola w aplikacji |
| --- | --- | --- | --- |
| androidx.core:core-ktx | 1.15.0 | Apache-2.0 | rozszerzenia platformy |
| androidx.core:core-splashscreen | 1.0.1 | Apache-2.0 | ekran startowy |
| androidx.activity:activity-compose | 1.9.3 | Apache-2.0 | host Compose, selektory plików/zdjęć |
| androidx.lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) | 2.10.0 | Apache-2.0 | ViewModel, `collectAsStateWithLifecycle` |
| androidx.navigation:navigation-compose | 2.8.5 | Apache-2.0 | nawigacja między ekranami |
| androidx.compose (BOM) | 2025.09.01 | Apache-2.0 | interfejs |
| androidx.compose.material3 + material-icons-extended | z BOM | Apache-2.0 | komponenty i ikony Material 3 |
| androidx.room (runtime, ktx, compiler) | 2.8.3 | Apache-2.0 | baza danych |
| androidx.datastore:datastore-preferences | 1.2.0 | Apache-2.0 | ustawienia |
| androidx.work:work-runtime-ktx | 2.10.0 | Apache-2.0 | porządkowanie przypomnień |
| kotlinx-coroutines-android | 1.10.1 | Apache-2.0 | współbieżność |
| kotlinx-serialization-json | 1.8.0 | Apache-2.0 | format kopii zapasowej |
| androidx.camera (core, camera2, lifecycle, view) | 1.6.1 | Apache-2.0 | podgląd i zdjęcie etykiety |
| com.google.mlkit:barcode-scanning | 17.3.0 | Google APIs Terms of Service (modele wbudowane); zależności Apache-2.0 | skanowanie kodów |
| com.google.mlkit:text-recognition | 16.0.1 | Google APIs Terms of Service (model łaciński wbudowany); zależności Apache-2.0 | OCR daty |
| io.coil-kt:coil-compose | 2.7.0 | Apache-2.0 | wyświetlanie zdjęć lokalnych |

## 3. Biblioteki testowe (nie wchodzą do APK release)

| Biblioteka | Wersja | Licencja |
| --- | --- | --- |
| junit:junit | 4.13.2 | EPL-1.0 |
| androidx.test.ext:junit | 1.3.0 | Apache-2.0 |
| androidx.test:runner | 1.7.0 | Apache-2.0 |
| androidx.test.espresso:espresso-core | 3.7.0 | Apache-2.0 |
| androidx.room:room-testing | 2.8.3 | Apache-2.0 |
| androidx.work:work-testing | 2.10.0 | Apache-2.0 |
| androidx.compose.ui:ui-test-junit4 | z BOM | Apache-2.0 |
| kotlinx-coroutines-test | 1.10.1 | Apache-2.0 |

## 4. Źródła danych

| Źródło | Czy używane | Uwagi |
| --- | --- | --- |
| Zewnętrzne API produktów (płatne lub darmowe) | **nie** | zgodnie ze specyfikacją: żadnej integracji z bazą produktów |
| Baza kodów EAN/UPC w sieci | **nie** | brak dostępu do internetu w aplikacji |
| Tabela elementów GS1 (AI 01, 10, 11, 17, 21, 30, 310n, 315n) | tak, **zaimplementowana lokalnie** | `domain/Gs1Parser.kt` — własna implementacja parsowania, bez zewnętrznych plików danych |
| Słownik kod → nazwa | tak, **tworzony wyłącznie z wpisów użytkownika** | `barcode_names` w lokalnej bazie |
| Modele ML Kit | tak, **wbudowane w APK** | nie są pobierane z sieci przy pierwszym uruchomieniu |
| Analityka / crash reporting | **nie** | brak takich SDK |
| Treści i tłumaczenia | własne | 375 stringów w `res/values/strings.xml` (język polski) |

## 5. Pełne teksty licencji

* Apache License 2.0 — `https://www.apache.org/licenses/LICENSE-2.0`
* Eclipse Public License 1.0 (JUnit) — `https://www.eclipse.org/legal/epl-v10.html`
* GNU GPL v2 z Classpath Exception (desugar_jdk_libs) — `https://openjdk.org/legal/gplv2+ce.html`
* Google APIs Terms of Service (ML Kit) — `https://developers.google.com/terms`

W zbudowanym APK listę zależności wraz z licencjami można wygenerować standardowo przez
`:app:dependencies` oraz raport `licensee`/`oss-licenses`, jeśli właściciel będzie chciał dołączyć
osobny ekran z tekstami licencji (obecnie ekran „Biblioteki i licencje" zawiera listę skrótową
z `ui/screens/AboutScreens.kt`).

## 6. Znaki i nazwy

„Terminownik" to nazwa własna tej aplikacji. Nazwy Android, Google Play, ML Kit i Jetpack Compose
są znakami towarowymi Google LLC i zostały użyte wyłącznie w celu opisu technologii.
