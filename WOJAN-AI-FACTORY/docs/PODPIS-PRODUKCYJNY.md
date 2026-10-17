# Podpis produkcyjny APK — procedura dla właściciela

APK testowy z repo jest podpisany **kluczem dev** (`keystore/wojan-dev.p12`) — podpis jest
kryptograficznie poprawny (v2+v3), ale służy **tylko do testów**. Do dystrybucji (sklep,
udostępnianie) potrzebny jest **Twój własny klucz produkcyjny**, który tworzysz i trzymasz
na SWOIM komputerze.

**Żelazne zasady (obowiązują cały czas):**
1. Prywatny klucz (.p12/.jks) i hasło **nigdy** nie trafiają do repozytorium, commitów,
   logów budowania, czatu ani chmury. `.gitignore` już wyklucza `keystore/*.p12`,
   `keystore/*.jks`, `keystore/*.env`.
2. Hasło istnieje wyłącznie jako zmienna środowiskowa `WOJAN_KEYSTORE_PASS` w sesji
   budowania albo w pliku `keystore/keystore.env` (lokalnie, wykluczony z gita).
3. Po utworzeniu klucza **zrób kopię zapasową** (zaszyfrowany nośnik offline). Utrata
   klucza produkcyjnego = brak możliwości aktualizacji aplikacji w sklepie (nowy klucz
   = nowa aplikacja).
4. Nie wklejaj hasła do komend w logach — skrypty czytają je z env, nie z argumentów.

## Krok 1 — utwórz klucz produkcyjny NA SWOIM KOMPUTERZE

**Wariant A — keytool z JDK (zalecany, standard Androida):**

```bash
keytool -genkeypair -v \
  -keystore wojan-release.p12 \
  -alias wojan \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storetype PKCS12
# keytool zapyta o hasło klucza i dane certyfikatu (CN/OU/O/L/C) — wpisz swoje
```

**Wariant B — skryptem projektu (Python + cryptography, np. na Twoim PC):**

```bash
export WOJAN_KEYSTORE_PASS="BARDZO-DLUGIE-LOSOWE-HASŁO"   # tylko w tej sesji shell
python tools/apksig/make_keystore.py /bezpieczna/sciezka/wojan-release.p12
# wynik: wojan-release.p12 (+ publiczny certyfikat -cert.pem — ten MOŻNA udostępniać)
```

Wymagania klucza produkcyjnego (polityka Google):
RSA ≥ 2048 bit (zalecane 4096 dla nowych kluczy), ważność ≥ 25 lat (keytool `-validity 10000`),
PKCS12 lub JKS.

## Krok 2 — przechowywanie

- Plik `wojan-release.p12` trzymaj poza repozytorium, np. na zaszyfrowanym dysku/hasle managerze.
- Kopię zapasową zrób OFFLINE (pendrive w sejfie / zaszyfrowane archiwum).
- Hasło: manager hasel lub `keystore/keystore.env` (lokalnie):

```
WOJAN_KEYSTORE_PASS=twoje-hasło
```

(plik jest na liście `.gitignore` — `git status` nigdy go nie pokaże).

## Krok 3 — budowanie i podpisywanie Release

Na komputerze z toolchainem (patrz `docs/BUILD.md`):

```bash
export WOJAN_KEYSTORE_PASS="..."                     # albo keystore/keystore.env
python android/build/build_apk.py \
    --keystore /bezpieczna/sciezka/wojan-release.p12 \
    --out dist/WOJAN-AI-FACTORY-Release.apk
```

Albo skopiuj swój klucz jako `keystore/wojan-release.p12` i uruchom bez `--keystore`.
(Dla testów: `--dev` używa klucza dev z repo — świadomie i tylko do testów.)

Skrypt automatycznie kończy oficjalną weryfikacją `ApkSignerTool verify --print-certs` —
musi wypisać `Verifies` i MD5/SHA certyfikatu. **Jeśli verify nie przejdzie — APK nie jest
gotowy do wydania.**

## Krok 4 — kontrola po podpisaniu (na Twoim komputerze)

```bash
# 1) oficjalny apksigner (z toolchainu) — musi pokazać Verifies + twój certyfikat:
java -cp tools/apksigner.jar com.android.apksigner.ApkSignerTool verify \
    --print-certs dist/WOJAN-AI-FACTORY-Release.apk

# 2) odcisk klucza do porównania z przyszłymi wydaniami:
java -cp tools/apksigner.jar com.android.apksigner.ApkSignerTool verify \
    --print-certs dist/WOJAN-AI-FACTORY-Release.apk | grep SHA
```

Zapisz ten odcisk (można publicznie) — to odcisk PUBLICZNEGO certyfikatu, nie sekret.

## Krok 5 — wydanie

- Aktualizacje sklepowe **muszą** być podpisane tym samym kluczem — bez niego użytkownicy
  nie zaktualizują aplikacji.
- `wojan-dev.p12` w repo może służyć wyłącznie do testów wewnętrznych; po przejściu na
  klucz produkcyjny APK-i testowe oznaczaj wyraźnie (np. inna nazwa pliku `-test.apk`).

## Czego NIE robić

- ❌ nie commituj `*.p12`, `*.jks`, `keystore.env`, `wojan-config.json` z kluczami API
- ❌ nie wpisuj hasła wprost w komendy, które trafiają do logów/czatu
- ❌ nie wysyłaj klucza mailem/„do przelotu" przez usługi chmurowe
- ❌ nie używaj klucza dev do wydania publicznego
- ❌ nie zgub klucza bez kopii zapasowej

## Tryb testowy (klucz dev) — co jest i czego nie wolno

`keystore/wojan-dev.p12` istnieje w środowisku budowania wyłącznie po to, by pipeline
dawało się testować automatycznie (podpis v2+v3 + verify PASS). Hasło nie jest zapisane
w repo (env / lokalny `keystore.env`). **Każdy APK podpisany tym kluczem = testowy.**
