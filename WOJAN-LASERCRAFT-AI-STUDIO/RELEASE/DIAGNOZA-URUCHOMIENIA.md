# DIAGNOZA: dlaczego aplikacja na telefonie się wywala

Data: 2026-10-29 · dotyczy zrzutów ekranu z 09:51–09:54 (telefon użytkownika)

## 1. Co dokładnie jest w logu (cytat, nie domysł)

```
java.lang.RuntimeException: Unable to start activity
  ComponentInfo{pl.wojan.hobby.lasercraft/pl.wojan.hobby.lasercraft.MainActivity}
Caused by: java.lang.SecurityException: pl.wojan.hobby.lasercraft:
  One of RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED should be specified
  when a receiver isn't being registered exclusively for system broadcasts
    at com.capacitorjs.plugins.share.SharePlugin.load(SharePlugin.java:)
    at com.getcapacitor.PluginHandle.loadInstance(PluginHandle.java:115)
    at com.getcapacitor.PluginHandle.<init>(PluginHandle.java:65)
    at com.getcapacitor.Bridge.registerAllPlugins(Bridge.java:625)
    at com.getcapacitor.Bridge.<init>(Bridge.java:223)
    at com.getcapacitor.BridgeActivity.onCreate(BridgeActivity.java:36)
    at pl.wojan.hobby.lasercraft.MainActivity.onCreate(MainActivity.java:15)
```

**To nie jest nasza aplikacja.** Pakiet w logu to `pl.wojan.hobby.lasercraft`, a nasz
program to `pl.wojan.lasercraft`. W logu widać też, że aplikacja jest **powłoką Capacitor**
(`com.getcapacitor.Bridge`, `BridgeActivity`, wtyczka `SharePlugin`) — czyli stroną WWW
w WebView, a nie programem natywnym. Pliki na WhatsAppie mają 3,0 MB i nazwę
`wojan-lasercraft-lite-shell-v0.1.0-A.apk` — nasze wydanie nazywa się
`WOJAN-LaserCraft-AI-Android-Release.apk` i ma **828 894 B (0,79 MB)**.

## 2. Przyczyna techniczna (konkret, jedna linia)

Od Androida 14 (API 34) każda aplikacja z `targetSdk ≥ 34` **musi** podać flagę przy
dynamicznej rejestracji odbiornika:

```java
// źle (Capacitor < 5.7 / 6):
context.registerReceiver(receiver, filter);
// dobrze:
context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
```

Wtyczka `SharePlugin` z Capacitora rejestruje odbiornik przy `load()` bez tej flagi →
`SecurityException` → `ActivityThread` nie może wystartować `MainActivity` → ekran
„aplikacja ciągle się zatrzymuje”. Telefon z Androidem 16 wymusza to tym mocniej.

To jest błąd **w tamtym kodzie** (powłoce Capacitor), nie w naszym. Nasz APK nie ma
ani jednego `registerReceiver` — sprawdzone na pliku, który leży w `RELEASE/` (patrz pkt 3).

## 3. Dowód, że nasz plik jest inny (wykonane polecenia i wynik)

```
$ sha256sum RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk
e793bf65faf8b7fe7a85a327cdab95491504a661fafb46ebeebe495d9bdf2821

$ python3 tools/apk_guard.py RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk
BRAMA APK: RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk
  · biblioteki natywne: 0
  · rozmiar pliku: 1421720 B (nieskompresowany)
WYNIK: plik jest naszą aplikacją natywną (bez WebView) — wolno wydać
```

Przeszukanie całego `classes.dex` naszego wydania:

| szukany ciąg | wynik |
|---|---|
| `capacitor`, `Capacitor`, `getcapacitor` | brak |
| `BridgeActivity`, `SharePlugin` | brak |
| `android/webkit/WebView` | brak |
| `registerReceiver`, `RECEIVER_EXPORTED` | **brak** |
| klas w pakiecie `pl/wojan` | 150 (własny rdzeń: Core, Draw, MainActivity, TabSet…) |

Nasza aplikacja rysuje interfejs własnym kodem (klasa `Draw`, płótno Androida) —
nie ma w niej WebView, więc nie może powtórzyć tego wyjątku.

## 3a. Twój telefon (z drugiego zrzutu): Samsung SM-S931B, Android 17 (API 37), arm64-v8a

Sprawdziłem, czy **nasze** wydanie w ogóle może działać na tym urządzeniu — bez tego
cała diagnoza byłaby niepełna:

| wymóg Androida 17 | nasz APK | wynik |
|---|---|---|
| `minSdk` ≥ 24 (starsze nie instalują się na Androidzie 15+) | 24 | OK |
| `targetSdk` ≤ 37 | 36 | OK |
| biblioteki natywne zgodne ze stronami 16 KB (wymóg dla .so od Androida 15) | **0 bibliotek .so** — czysty DEX | nie dotyczy |
| ABI: urządzenie arm64-v8a | brak kodu natywnego, więc każda ABI | OK |
| uprawnienia | `INTERNET`, `RECORD_AUDIO` (mikrofon do dyktowania) | OK |
| audyt API (czy nie wołamy czegoś nowszego niż minSdk) | 1032 wywołań, 0 klas nowszych niż 24 | OK |

Pole „Android System WebView 156… aktywny” na zrzucie **nie pochodzi z naszego programu** —
nasz APK nie zawiera `WebView` ani jednego odwołania do niego (tabela w pkt 3). To kolejny
dowód, że raport dotyczy powłoki Capacitor, która w WebView wyświetla stronę.

## 4. Jak sprawdzić na telefonie, który plik się instaluje

0. **Nazwa na liście aplikacji**: nasza to **„WOJAN LaserCraft AI”** (z „AI” na końcu,
   sprawdzone w manifeście: `application-label:'WOJAN LaserCraft AI'`). Tamta powłoka
   nazywa się „WOJAN LaserCraft” bez „AI” — dlatego ikony łatwo pomylić.
1. **Rozmiar pliku**: nasze wydanie 0,79 MB; powłoka Capacitor 3,0 MB. Jeśli plik ma
   3 MB — to nie jest nasza aplikacja.
2. **Ustawienia → Aplikacje → WOJAN LaserCraft AI → O aplikacji**: pakiet musi być
   `pl.wojan.lasercraft`. Jeśli widać `pl.wojan.hobby.lasercraft` — to tamta powłoka.
3. Nazwa pliku: `WOJAN-LaserCraft-AI-Android-Release.apk` (dokładnie tak).
4. Przed instalacją odinstaluj starą powłokę (długie przytrzymanie ikony → Odinstaluj),
   żeby nie było pomyłki, którą aplikację uruchamiasz.

## 5. Co zrobiłem, żeby to się nie powtórzyło

Dodałem bramę `tools/apk_guard.py` i wpiąłem ją do `tools/build_android_smali.sh`.
Budowanie APK kończy się teraz **błędem**, jeśli plik zawiera Capacitora, WebView,
`registerReceiver` albo nie ma pakietu `pl.wojan.lasercraft` i klas własnego rdzenia.
Brama została przetestowana na atrapie APK z tymi śladami — blokuje (9 pozycji `[STOP]`),
a nasze wydanie przepuszcza.

## 6. Jeśli chcesz naprawić tamtą powłokę (nie moje wydanie)

Najkrótsza droga, w kolejności skuteczności:

1. **Usunąć wtyczkę Share** — jeśli powłoka nie udostępnia plików, `SharePlugin` jest zbędny.
2. **Podnieść Capacitor** do wersji, która przekazuje `RECEIVER_NOT_EXPORTED`
   (Capacitor 6.x z aktualnymi wtyczkami) i przebudować.
3. **Własna łatka** na `SharePlugin.load`: `registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)`.

Czego jednak nie zmieni żadna z tych poprawek: taka powłoka pozostaje aplikacją
webową w WebView, a wymaganie brzmi „żadnego PWA udającego aplikację natywną”.
Dlatego nie buduję tej powłoki i nie wydaję jej jako WOJAN LaserCraft AI.
