# JAK ROZPOZNAĆ, KTÓRA APLIKACJA DZIAŁA — trzy różne rzeczy o podobnej nazwie

Data: 2026-10-29. Ten dokument powstał, bo na telefon trafiły **trzy różne programy**
o nazwie „WOJAN LaserCraft / Wojan Studio”. Dwa z nich nie są naszym produktem i nie
mają prawa działać — i nie działają.

## Nasz produkt (jedyny, który wydajemy)

| cecha | wartość |
|---|---|
| plik | `WOJAN-LaserCraft-AI-Android-Release.apk` |
| rozmiar | **828 894 B (0,79 MB)** |
| pakiet | `pl.wojan.lasercraft` |
| nazwa na liście aplikacji | **WOJAN LaserCraft AI** |
| co widać po uruchomieniu | ciemny, grafitowy interfejs z 12 zakładkami (Generator AI, Mikrofon i prompt, Import zdjęcia, Kategorie, Edytor, Napisy AI, QR, Moje projekty, Kalkulator, Eksport, Diagnostyka AI, Ustawienia) |
| internet | **niepotrzebny** — program nigdy nie ładuje żadnego adresu |
| WebView / Capacitor / Cordova | **brak** (własny interfejs rysowany na płótnie Androida) |
| link | https://github.com/janradzik86/mein/raw/7e49932/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk |

## Program nr 2 — powłoka Capacitor (`wojan-lasercraft-lite-shell-v0.1.0-A.apk`)

* rozmiar **3,0 MB**, pakiet `pl.wojan.hobby.lasercraft`, nazwa „WOJAN LaserCraft” (bez AI);
* to strona WWW w opakowaniu (Capacitor/WebView), nie program natywny;
* wywala się na Androidzie 14+:
  `SecurityException: One of RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED should be specified`
  w `com.capacitorjs.plugins.share.SharePlugin.load` ← widać to na zrzutach z 09:51.

## Program nr 3 — „Wojan Studio”, strona z Render (`wojan-studio-ai-e4wd.onrender.com`)

* to **witrynna aplikacja w WebView** otwierająca adres `https://wojan-studio-ai-e4wd.onrender.com/`;
* zrzuty z 15:05 pokazują „Strona nie wyświetliła się”, stan `EMPTY:0`, oraz błędy:
  `Refused to execute script from '…/js/api.js' because its MIME type ('text/html') is not executable`
  (to samo dla `js/fx.js`, `js/app.js`, `css/styles.css`).

### Co dokładnie tam nie działa (diagnoza, bez zgadywania)

Serwer zwraca dla `/js/app.js` i `/css/styles.css` **stronę HTML** (`text/html`) zamiast
pliku JavaScript/CSS. Przeglądarka (i WebView) odmawia wykonania takiej odpowiedzi, więc
aplikacja zostaje pusta — stąd `EMPTY:0`. Typowe przyczyny:

1. **Pliki nie zostały wdrożone** — katalog publikowany na Render nie zawiera `js/` i `css/`,
   a przekierowanie „ wszystkiego na index.html” (SPA fallback) zwraca HTML z kodem 200.
2. **Zły katalog publikacji** (`public` vs `dist`/`build`) — serwer widzi tylko `index.html`.
3. **Przekierowanie catch-all** (`/* → /index.html`) włączone także dla istniejących plików.

Naprawa po stronie tamtego wdrożenia: ustawić poprawny katalog publikacji, usunąć catch-all
dla `/js/*` i `/css/*`, sprawdzić `Content-Type` (ma być `text/javascript` i `text/css`).

### Ale to i tak nie jest nasz produkt

Nawet po naprawie MIME byłaby to **strona internetowa w WebView** — czyli dokładnie to,
czego wymagania zabraniają („żadnego PWA udającego aplikację natywną”, „bez obowiązkowego
internetu”, „silnik AI lokalnie w telefonie”). Dlatego nie rozwijam tego wdrożenia i nie
wydaję go jako WOJAN LaserCraft AI.

## Jak odróżnić w 5 sekund

1. **Czy widać adres internetowy albo ekran „Strona nie wyświetliła się”?**
   Jeśli tak — to nie nasz program (nasz nigdy nie ładuje adresu).
2. **Rozmiar pliku:** 0,79 MB = nasz; 3,0 MB = powłoka Capacitor.
3. **Ustawienia → Aplikacje → [nazwa] → O aplikacji:** pakiet `pl.wojan.lasercraft` = nasz.
4. **Nazwa:** „WOJAN LaserCraft **AI**” = nasz; „WOJAN LaserCraft” / „Wojan Studio” = nie nasz.
5. **Tryb samolotowy:** nasz program działa normalnie; tamte dwie pokażą błąd.

## Wniosek

Na telefonie trzeba mieć **jeden** plik z linku w pierwszej tabeli. Pozostałe dwa programy
należy odinstalować, żeby nie myliły ikon. Jeśli nasz program kiedykolwiek pokaże błąd,
zrobi to własnym ekranem z nagłówkiem `pl.wojan.lasercraft · wersja … · Android …` —
wtedy wyślij ten zrzut, bo w nim jest konkretna linia kodu, a nie domysł.
