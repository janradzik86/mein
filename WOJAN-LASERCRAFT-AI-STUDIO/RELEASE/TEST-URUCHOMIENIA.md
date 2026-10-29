# TEST URUCHOMIENIA — 30 sekund, dwa pliki, jedna odpowiedź

Data: 2026-10-29. Cel: rozdzielić dwie zupełnie różne możliwe przyczyny „aplikacja się zamyka”:

* **A. nasz kod aplikacji** (wtedy potrzebny jest stos wywołań — daje go APK 1.1.0),
* **B. nasz łańcuch budowania DEX/podpisu** (wtedy padnie nawet 29-kilobajtowy test).

## Pliki

| plik | rozmiar | co robi |
|---|---|---|
| `WOJAN-PROBE-test.apk` | **29 465 B** | nic nie liczy: pokazuje jeden napis „DZIAŁA”. Pakiet `pl.wojan.probe`, ikona „WOJAN PROBE”. |
| `WOJAN-LaserCraft-AI-Android-Release.apk` | 832 990 B | właściwa aplikacja 1.1.0; po awarii pokazuje raport błędu |

Oba podpisane tym samym kluczem, oba sprawdzone: `apk_installcheck` → 0 problemów,
`dexlink` względem Android 17 (API 37) → 0 brakujących metod.

## Co zrobić i co mi napisać

1. Zainstaluj `WOJAN-PROBE-test.apk` (29 KB) i otwórz **WOJAN PROBE**.
   * **Widać napis „DZIAŁA”** → łańcuch budowania jest sprawny; problem leży w kodzie
     aplikacji i zajmie się nim raport z punktu 2.
   * **Zamyka się / biały ekran** → problem jest w naszym DEX-ie albo w podpisie;
     wtedy nie ma sensu nic poprawiać w aplikacji, tylko w sposobie budowania.
     Napisz mi to jednym zdaniem — to zmienia cały kierunek naprawy.
2. Odinstaluj starą wersję LaserCraft (nowy klucz podpisujący), zainstaluj
   `WOJAN-LaserCraft-AI-Android-Release.apk`, otwórz.
   * **Działa** (12 zakładek) → koniec, problemem były tamte dwa programy.
   * **Zamyka się** → otwórz **drugi raz**: pojawi się ekran „błąd uruchamiania”
     z pełnym stosem (ten sam tekst jest w schowku). Zrzut ekranu wystarczy.
3. Możesz odinstalować WOJAN PROBE po teście — nic nie zapisuje.

## Dlaczego nie zgaduję

Bez stosu wywołań „naprawa” byłaby loterią: mogę zmienić dowolną linię i powiedzieć
„teraz działa”, a Pan sprawdzi i znowu się zamknie. Raport z punktu 2 zawiera nazwę
klasy, metody i numer linii — po nim poprawka jest jedną, konkretną zmianą.
