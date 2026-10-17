# WOJAN AI FACTORY — testy i statusy (uczciwie)

## Co zostało REALNIE wykonane i sprawdzone

| Test | Wynik | Dowód |
|---|---|---|
| Silnik: failover po 429 z zapisem rozmowy, wyczerpany model nie wraca | **PASS** | `engine/test/router_failover.test.js` |
| Silnik: LOCAL_ONLY nie sięga chmury; MANUAL = jeden model | **PASS** | `engine/test/router_failover.test.js` |
| Silnik: błąd niedostępności ≠ wyczerpanie limitu | **PASS** | `engine/test/router_failover.test.js` |
| Pipeline: ZADANIE→…→EXE/APK z realną pętlą DIAGNOSTYKA→POPRAWKA (kompilacja fail→fix→ok) | **PASS** | `engine/test/pipeline.test.js` |
| Trwałość stanu po "restarcie" (nowy Store, ten sam plik) | **PASS** | `engine/test/pipeline.test.js` |
| Kontrakt API zgodny z Android ApiClient (status/projekty/logi/artefakty/role, Bearer auth) | **PASS** | `engine/test/api.test.js` |
| Protokół Ollama: realne HTTP `/api/chat`, format żądania/odpowiedzi | **PASS** (stub serwera HTTP — patrz uwaga) | `engine/test/ollama_protocol.test.js` |
| 429 → QuotaError (bez obchodzenia limitów), connection refused → UnavailableError | **PASS** | tamże |
| Protokół OpenAI-compatible (OmniRoute/OpenRouter): /chat/completions + Bearer + quota mapping | **PASS** (stub HTTP) | tamże |
| APK: build ECJ→j2smali→dex→AXML→zip→podpis v1+v2+v3 | **PASS** | `ApkSignerTool verify --print-certs` OK |
| APK: niezależna walidacja (androguard: manifest, 42/42 klas w dex, podpisy) | **PASS** | odczyt APK androguardiem |
| Setup.exe / EXE: struktura PE x64 GUI, kompletne archiwum instalacyjne, importy DLL | **PASS** | parser PE (własny) |
| Testy silnika zbiorczo | **10/10 PASS** | `cd engine && node --test "test/*.test.js"` |

Uwaga o „stub": testy protokołów Ollama/OpenAI używają **prawdziwego serwera HTTP**
(node:http) implementującego dokładnie protokół providerów — to test warstwy protokołu,
nie żywego modelu LLM.

## NIEUKOŃCZONE (z konkretną przeszkodą)

| Test | Status | Przeszkoda |
|---|---|---|
| Uruchomienie EXE na żywym Windows 11 (wejście UI, pipeline, serwer dla telefonu) | **NIEUKOŃCZONE** | środowisko budowania nie ma Windows ani wine — nie da się wykonać PE; struktura/linkowanie zweryfikowane, uruchomienie wymaga Twojego PC |
| Setup.exe: instalacja na Windows 11 (kopiowanie, skrót, deinstalacja) | **NIEUKOŃCZONE** | jw. — brak Windows w środowisku |
| APK: instalacja i uruchomienie na fizycznym telefonie + połączenie z PC | **NIEUKOŃCZONE** | brak fizycznego urządzenia/emulatora Android w środowisku; APK zwalidowany statycznie (dex, manifest, podpisy) |
| Żywy model LLM przez Ollama (lokalnie/Kaggle) | **NIEUKOŃCZONE** | brak GPU i modelu GGUF w środowisku (pliki modeli są duże/LFS); protokół przetestowany na stubie; na Twoim PC: `ollama serve` + test poniżej |
| Żywy OmniRoute / Hermes | **NIEUKOŃCZONE** | wymagają instalacji na Twoim PC (patrz BUILD.md §6) |
| Podpis APK kluczem produkcyjnym | **OCZEKUJE DECYZJI WŁAŚCICIELA** | klucz dev jest w repo zgodnie z polityką; produkcyjny musi być Twój — patrz README |

## Testy do wykonania NA TWOIM PC (receptury)

1. **EXE**: `Setup.exe` → uruchom WOJAN-AI-FACTORY.exe → zakładka Status musi pokazać
   „Serwer API nasłuchuje". Kliknij „Nowe zadanie", wpisz np. „kalkulator konsolowy C",
   obserwuj fazy w Logach do `EXE/APK`.
2. **APK**: zainstaluj `WOJAN-AI-FACTORY-Release.apk` (`adb install`), podaj
   `http://<IP-PC>:8765` + token z zakładki Ustawienia → Wyślij zadanie → sprawdź logi
   i pobranie artefaktu.
3. **Ollama**: `ollama serve && ollama run llama3.1:8b "cześć"` → w `wojan-config.json`
   ustaw `ollama.base`; tryb LOCAL ONLY w aplikacji; odpowiedź modelu musi trafić do logów.
4. **Failover**: wyczerp model (lub ustaw zły klucz) → aplikacja ma zapisać rozmowę
   i przejść na zapasowy — dokładnie jak testy Node.
5. **Restart**: zamknij i otwórz aplikację → projekty/logi/artefakty nienaruszone
   (`data/store.json`).
