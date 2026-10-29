# POPRAWKA 1.0.4 — dlaczego telefon mówi „aplikacji nie zainstalowano"

Data: 2026-10-29 · Dotyczy: `WOJAN-LaserCraft-AI-Android-Release.apk`

## Przyczyna (potwierdzona dokumentacją AOSP + Kodeksem instalatora)

Od Androida 11 (API 30) system **odrzuca instalację** APK, w którym `resources.arsc`
jest **spakowany (deflate)** albo **nie jest wyrównany do 4 bajtów** — i to niezależnie
od podpisu. W źródłach AOSP (`ParsingPackageUtils.parseBaseApk`):

```java
if (assets.containsAllocatedTable()) {                       // tabela zasobów NIE jest mmap-owalna
    ... input.error(INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED,
        "Targeting R+ (version 30 and above) requires the resources.arsc of installed"
        " APKs to be stored uncompressed and aligned on a 4-byte boundary");
}
```

Objaw na telefonie to dokładnie **„Aplikacji nie zainstalowano"** (bez dodatkowego kodu,
w logach `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED`, w `adb`: kod `-124`).

W APK 1.0.2 i 1.0.3, budowanych przez apktool, `resources.arsc` był **deflate**:

```
resources.arsc              deflate   ← Android 11+ odrzuca taki APK przy instalacji
```

(apktool domyślnie pakuje zasoby; `zipalign` też nie był robiony.)

Dla porządku — cała historia instalacji:

| wersja | co blokowało instalację |
|---|---|
| 1.0.0 / 1.0.1 | `classes.dex` w wersji **036**, której ART nie zna (dexopt odrzuca plik) |
| 1.0.2 / 1.0.3 | DEX już poprawny, ale `resources.arsc` **spakowany** → `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED` |
| **1.0.4** | `resources.arsc` **STORED** + wyrównany do 4 B, `classes.dex` STORED + 4 B, podpis v1+v2+v3 |

## Poprawka

1. `tools/apktool.yml` (generowany w `tools/build_android.sh`): `resourcesAreCompressed: false`.
2. Po zbudowaniu APK jest **przepakowywany** przez `tools/pack_apk.py`:
   * `resources.arsc` → **ZIP_STORED**, dopełniony poprawnym polem extra (id `0xD935`, jak `zipalign`),
     tak aby dane zaczynały się pod adresem podzielnym przez 4,
   * `classes.dex` → ZIP_STORED, również wyrównany (mmap w ART).
3. `tools/build_android.sh` ma teraz **twarde kontrole** i przerywa build, jeśli:
   * `resources.arsc` jest spakowany,
   * `resources.arsc` lub `classes.dex` (niepakowany) nie są wyrównane do 4 B,
   * brakuje `AndroidManifest.xml`, `classes.dex` albo `resources.arsc`,
   * wersja DEX nie jest obsługiwana przez ART (`dexcheck`),
   * DEX ma naruszenia weryfikatora (`dexverify`) lub nierozwiązane odwołania (`dexlink`).

## Weryfikacja gotowego pliku (1.0.4)

Układ ZIP (to jest dokładnie to, czego wymaga Android):

```
wpis                                       metoda    offset danych  align4
resources.arsc                             STORED               44  TAK
AndroidManifest.xml                        deflate           11249  -
classes.dex                                STORED            12244  TAK
assets/fonts/…                             deflate               …  -
```

Kontrole:

```
APK: 17 wpisów, classes.dex magic=b'dex\n037\x00' -> OK
resources.arsc: STORED, offset 44 (4B OK)
classes.dex: STORED, offset 12244
dexcheck:  566 code_item, 0 błędów
dexlink:   150 klas, 6218 wywołań, 0 problemów
dexverify: 150 klas, 6218 wywołań, 0 naruszeń weryfikacji ART
apksigner: v1=true, v2=true, v3=true
androguard: is_valid_APK=True · pl.wojan.lasercraft 1.0.4 (5) · minSdk 24 · targetSdk 36
            label z resources.arsc = „WOJAN LaserCraft AI", ikona res/mipmap-xxxhdpi-v4/ic_launcher.png
```

## Instalacja

1. Jeśli na telefonie została wcześniejsza wersja — **odinstaluj ją najpierw**
   (Ustawienia → Aplikacje → WOJAN LaserCraft AI → Odinstaluj). Niepotrzebne, jeśli
   żadna wersja nie dała się zainstalować.
2. Otwórz `WOJAN-LaserCraft-AI-Android-Release.apk`, zezwól na instalację z tego źródła.
3. Suma kontrolna: `SHA256SUMS.txt`.

## Czego nadal nie da się potwierdzić bez urządzenia

APK nie był instalowany na fizycznym telefonie ani emulatorze — w środowisku budowania
nie ma Androida. Tym razem usunięta przyczyna jest jednak **warunkiem formalnym** instalacji
na Androidzie 11+ i jest sprawdzana w buildzie (STORED + 4 B), a nie tylko „na oko".
Jeśli system nadal odmówi instalacji, potrzebny będzie **dokładny komunikat** albo wynik
`adb install` (np. `INSTALL_FAILED_…`) — wtedy trafię w kolejną przyczynę bez zgadywania.
