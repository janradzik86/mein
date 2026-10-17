# WOJAN AI FACTORY

Niezależna **fabryka aplikacji AI** na Windows 11 + Android. Fabryka buduje projekty docelowe
WYŁĄCZNIE jako **Windows EXE** albo **Android APK Release**, przepływem:

**ZADANIE → PROJEKT → KOD → KOMPILACJA → TEST → DIAGNOSTYKA → POPRAWKA → EXE/APK**

## Produkty

| Plik | Co to jest | Status |
|---|---|---|
| `dist/WOJAN-AI-FACTORY-Setup.exe` | instalator Windows 11 (własny Win32, deinstalator `/uninstall`) | ZBUDOWANY (weryfikacja PE: TAK; uruchomienie na Windows: wymaga Twojego PC — patrz TESTY.md) |
| `dist/WOJAN-AI-FACTORY-Portable.zip` | wariant przenośny (rozpakuj i uruchom) | ZBUDOWANY |
| `dist/WOJAN-AI-FACTORY-Release.apk` | aplikacja Android (Java, podpis v2+v3) | ZBUDOWANY, **zweryfikowany** oficjalnym `ApkSignerTool verify` + androguard |
| `engine/` | silnik AI (router modeli, role agentów, pipeline) | DZIAŁA — 10/10 testów Node |
| `kaggle/wojan-kaggle-ollama.ipynb` | realny notebook Ollama na GPU Kaggle (2× T4) | GOTOWY do odpalenia na Kaggle |
| `desktop/` | źródła EXE (Win32 + DX11 + ImGui + QuickJS-NG) | ZBUDOWANY (PE x64 GUI) |

## Tryby pracy AI (router)

- **LOCAL ONLY** — tylko lokalna Ollama; chmura niedostępna.
- **FREE FIRST** — darmowe modele (Ollama, darmowe tiery) przed płatnymi.
- **AUTO** — lokalne → reszta; automatyczny failover z **zapisem rozmowy** przed przełączeniem.
- **CODING** — preferuje modele programistyczne (np. przez OmniRoute).
- **MANUAL** — jeden wskazany model, bez przełączania.

**Limitów providerów NIGDY się nie obchodzi** — 429/wyczerpany limit = model oznaczony
wyczerpany (na godzinę), zapis stanu i przejście na zapasowy. Pełne testy: `engine/test/`.

## Role agentów (współdzielony stan projektu)

ARCHITEKT → KODER → TESTER → DIAGNOSTA → BUILD AGENT → REVIEWER.

## Integracje

- **OmniRoute** (github.com/diegosouzapw/OmniRoute) — router OpenAI-compatible; konfiguracja
  `{"omniroute": {"base": "http://127.0.0.1:PORT/v1"}}` w `wojan-config.json`.
- **Hermes Agent** (github.com/NousResearch/hermes-agent) — executor przez CLI
  (`hermes -q "<zadanie>" -Q`); adapter: `docs/BUILD.md`.
- **Ollama** — lokalnie na PC oraz opcjonalnie na Kaggle (notebook w `kaggle/`).
- **API chmurowe** — OpenRouter / OpenAI / Gemini (darmowy tier) przez `engine/src/providers.js`.

## Android

Aplikacja (Java, bez WebView/PWA — czysty natywny UI po polsku) łączy się z komputerem
przez LAN: URL + token. Zadanie (ZADANIE) trafia do fabryki na PC, telefon pokazuje
PROJEKT/KOD/KOMPILACJA/LOGI i pobiera pliki wynikowe (EXE/APK). **Telefon nie udaje
kompilatora** — Windows SDK/Android SDK/Gradle pracują NA KOMPUTERZE.

## Bezpieczeństwo

- Token API generowany lokalnie; klucze providerów tylko w `wojan-config.json` (lokalnie).
- Endpoint Kaggle: **tylko** przez Cloudflare Tunnel + Bearer token (nigdy goły port Ollama).
- Klucz podpisu APK: patrz sekcja poniżej — decyzja należy do właściciela.

## Klucz podpisu APK — DECYZJA WŁAŚCICIELA (oczekuje)

Do wydania produkcyjnego potrzebny jest **Twój własny keystore** (nie dev-owy z repo).
Zdecyduj: (a) dostarczysz własny klucz (hasło wpisz w `keystore/keystore.env`, plik jest
w .gitignore — nigdy w kodzie/repo), (b) mam wygenerować nowy niepowtarzalny klucz produkcyjny
i przekazać Ci go poza repozytorium. Do czasu decyzji APK jest podpisany kluczem
**dev** (`keystore/wojan-dev.p12`) — poprawny podpis v2+v3, ale NIE do dystrybucji sklepowej.

## Start szybki

1. Windows: uruchom `WOJAN-AI-FACTORY-Setup.exe` (albo rozpakuj Portable.zip).
2. Skonfiguruj `wojan-config.json` (Ollama/klucze) — patrz `docs/BUILD.md`.
3. Android: zainstaluj `dist/WOJAN-AI-FACTORY-Release.apk`, podaj URL komputera + token.
4. Złóż ZADANIE — fabryka prowadzi projekt do EXE/APK.
