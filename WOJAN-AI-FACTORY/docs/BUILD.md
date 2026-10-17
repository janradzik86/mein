# WOJAN AI FACTORY — budowanie (BUILD)

Wszystkie poniższe polecenia były **realnie wykonane** w środowisku budowania; wyniki
leżą w `dist/`. Na Twoim PC powtórzenie wymaga narzędzi z `scripts/fetch-toolchain.sh`.

## 0. Narzędzia

```bash
bash scripts/fetch-toolchain.sh        # ECJ, smali 1.3.3, apksigner 33.0.2, android-34.jar
python3 -m venv .venv && .venv/bin/pip install ziglang cryptography pyaxml jdk4py androguard
# wszystkie skrypty uruchamiaj: .venv/bin/python ...
```

Uwaga: `jdk4py` to JRE (java + keytool) — kompilację Javy robi **ECJ**, podpis i weryfikację
APK — **oficjalny apksigner.jar 33.0.2**.

## 1. Android APK (Release)

```bash
export WOJAN_KEYSTORE_PASS="..."      # NIGDY w kodzie/repo; lokalny env lub keystore/keystore.env
.venv/bin/python android/build/build_apk.py --dev \
    --out dist/WOJAN-AI-FACTORY-Release.apk
```

Potok (zamknięty, w pełni zautomatyzowany):

`javac-like ECJ → j2smali (własny tłumacz JVM→smali) → smali.jar (dex 035) →
pyaxml (binarny AndroidManifest.xml) → zip 4B-aligned → apksigner v1+v2+v3 →
ApkSignerTool verify (MUSI przejść)`

Weryfikacja niezależna: `androguard` czyta manifest, klasy dex (42/42 zgodnych), podpisy v1+v2+v3.

**Znane ograniczenie j2smali:** `multianewarray` (tablice wielowymiarowe) → ClassFormatError.
Kod fabryki tego nie używa; dla własnych projektów dodaj obsługę w `tools/j2smali/j2smali.py`.

## 2. Desktop EXE (Windows 11)

```bash
.venv/bin/python desktop/build/build_exe.py
# → desktop/build/out/WOJAN-AI-FACTORY.exe (PE x64 GUI, DX11 + ImGui + QuickJS-NG)
```

Architektura: Win32 + D3D11 (ImGui) + silnik JS w QuickJS-NG + serwer HTTP (winsock)
dla telefonu + klient HTTP (WinHTTP) do chmury/Ollama/Kaggle. Silnik ten sam co w testach
Node (`engine/`) — jedno źródło logiki.

Znane ograniczenie: środowisko budowania nie ma Windows/wine → **test uruchomieniowy EXE
na żywym Windows 11 jest NIEUKOŃCZONY** z przeszkodą: brak systemu Windows w środowisku.
Struktura PE, importy (d3d11, d3dcompiler_47, winhttp, ws2_32) i linkowanie zweryfikowane.

## 3. Setup.exe + Portable

```bash
.venv/bin/python desktop/installer/build_installer.py
# → dist/WOJAN-AI-FACTORY-Setup.exe   (instalator Win32 + deinstalator /uninstall)
# → dist/WOJAN-AI-FACTORY-Portable.zip
```

Instalator: wybór katalogu, kopiowanie payloadu (doklejonego archiwum w Setup.exe),
skrót na pulpicie (IShellLink), wpis w rejestrze Uninstall, `Setup.exe /uninstall`.

## 4. Silnik AI + testy

```bash
cd engine && node --test "test/*.test.js"     # 10/10
```

## 5. Konfiguracja (`wojan-config.json` obok EXE)

```json
{
  "mode": "AUTO",
  "ollama":   { "base": "http://127.0.0.1:11434", "model": "llama3.1:8b" },
  "omniroute":{ "base": "http://127.0.0.1:PORT/v1", "model": "kod/model" },
  "openrouter": { "apiKey": "sk-or-..." },
  "gemini":   { "apiKey": "..." },
  "token": "GENEROWANY PRZY PIERWSZYM STARCIE",
  "apiPort": 8765
}
```

Kaggle (Ollama na 2× T4): uruchom `kaggle/wojan-kaggle-ollama.ipynb`, wklej secured URL
+ token do `ollama.base` / `ollama.apiKey`. Port 11434 nie jest nigdy wystawiany publicznie.

## 6. Hermes Agent (executor)

Adapter: `hermes -q "<zadanie>" -Q` (oneshot), `--provider/--model/--base-url/--api-key`.
Jeśli masz `hermes` w PATH, możesz go podpiąć jako wykonawcę zadań zamiast wbudowanego
pipeline'u — konfiguracja w `wojan-config.json` → `hermes.cli`.

## Niezależność

Projekt nie zależy od CrossFlow Command: zero zmian w tamtym repo, zero jego modułów/DB.
