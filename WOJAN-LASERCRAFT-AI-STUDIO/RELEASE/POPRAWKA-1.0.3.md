# POPRAWKA 1.0.3 — PRAWDZIWA przyczyna: nieprawidłowy bajtkod DEX (invoke-direct na metody klasy nadrzędnej)

Data: 2026-10-29 · Dotyczy: `WOJAN-LaserCraft-AI-Android-Release.apk`

## Co było zepsute

Konwerter `.class → .smali` (`tools/j2smali/j2smali.py`) mapował **każdy** bajtkod JVM
`invokespecial` na DEX-owe `invoke-direct`. A `invokespecial` w JVM to nie tylko
konstruktory — to także wywołania `super.metoda()`.

W DEX `invoke-direct` wolno stosować **wyłącznie** do `<init>` albo do metody **prywatnej
tej samej klasy**. Wywołanie metody klasy nadrzędnej musi być `invoke-super`.

W DEX-ie 1.0.0–1.0.2 było 5 takich nieprawidłowych wywołań:

```
MainActivity.onCreate        → invoke-direct Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
MainActivity.onBackPressed   → invoke-direct Landroid/app/Activity;->onBackPressed()V
MainActivity.onActivityResult→ invoke-direct Landroid/app/Activity;->onActivityResult(IILandroid/content/Intent;)V
EditorView.onDraw            → invoke-direct Landroid/view/View;->onDraw(Landroid/graphics/Canvas;)V
EditorView.onSizeChanged     → invoke-direct Landroid/view/View;->onSizeChanged(IIII)V
```

ART (weryfikator DEX-a) odrzuca taką klasę:
`VerifyError: invoke-direct … is not private` / `Rejecting class pl.wojan.lasercraft.MainActivity`.
Ponieważ `MainActivity` jest aktywnością startową, **aplikacja nie ma jak wystartować** —
nie ma nawet ekranu błędu, bo klasa nie przechodzi weryfikacji przed `onCreate`.

Dlatego 1.0.0 i 1.0.1 nie działały, a 1.0.2 (poprawne zasoby, podpis, DEX 037) też nie —
DEX był generowany tym samym konwerterem.

## Poprawka

`tools/j2smali/j2smali.py`:

```python
if op == 0xB7 and nm != "<init>" and cls != self.cf["name"]:
    kind = "invoke-super"     # super.metoda() — NIE invoke-direct
```

Po poprawce w DEX-ie jest dokładnie 5 wywołań `invoke-super` (te z listy powyżej)
i **zero** nieprawidłowych `invoke-direct`.

## Nowe narzędzie kontrolne: `tools/dexverify.py`

Dotąd sprawdzałem tylko strukturę pliku (`dexcheck`) i czy odwołania się rozwiązują
(`dexlink`) — żadne z nich nie widziało błędu, który blokował aplikację. Nowy walidator
sprawdza reguły weryfikatora ART:

* `invoke-direct` → tylko `<init>` lub metoda prywatna tej samej klasy,
* `invoke-interface` → cel musi być interfejsem,
* `invoke-virtual` → cel nie może być interfejsem,
* `move-result*` → bezpośrednio po `invoke`, typ zgodny z typem zwracanym,
* `return*` → zgodny z deskryptorem metody,
* `new-instance` → nie może tworzyć interfejsu ani klasy abstrakcyjnej.

Wynik na DEX-ie 1.0.2 (przed poprawką):

```
dexverify: 150 klas, 6218 wywołań, 6 naruszeń weryfikacji ART
   ...onDraw: invoke-direct do Landroid/view/View;->onDraw (dozwolone tylko <init> lub metoda prywatna tej klasy)
   ...onSizeChanged: invoke-direct do Landroid/view/View;->onSizeChanged ...
   ...onActivityResult: invoke-direct do Landroid/app/Activity;->onActivityResult ...
   ...onBackPressed: invoke-direct do Landroid/app/Activity;->onBackPressed ...
   ...onCreate: invoke-direct do Landroid/app/Activity;->onCreate ...
```

Po poprawce (1.0.3), ten sam plik i te same 150 klas:

```
APK: 17 wpisów, classes.dex magic=b'dex\n037\x00' -> OK
dexcheck:  566 code_item, 0 błędów
dexlink:   150 klas, 6218 wywołań metod sprawdzonych, 0 problemów
dexverify: 150 klas, 6218 wywołań, 0 naruszeń weryfikacji ART
apksigner: v1=true, v2=true, v3=true
androguard: pl.wojan.lasercraft 1.0.3 (4), minSdk 24, targetSdk 36
```

Dodatkowo DEX przeszedł **rund-trip** przez nowoczesne narzędzia (baksmali → smali
z apktool 2.9.3): 150 klas, 566 metod z kodem, 0 błędów — plik da się w całości
rozłożyć i złożyć z powrotem.

`tools/dexverify.py` jest wpięty w `tools/build_android.sh` — build **przerywa się**,
jeśli pojawi się choć jedno naruszenie.

## Czego nadal nie da się potwierdzić w tym środowisku

Nie ma tu telefonu ani emulatora, więc APK nie został uruchomiony na urządzeniu.
Różnica wobec poprzednich wersji jest jednak zasadnicza: tym razem znalezione
naruszenie jest **twardym błędem weryfikatora ART**, który jednoznacznie uniemożliwiał
start aplikacji, i został usunięty (potwierdzone na poziomie bajtkodu: 0 naruszeń).

Jeśli po instalacji 1.0.3 aplikacja nadal się nie uruchomi, w grę wchodzi już tylko
błąd w kodzie UI — a od 1.0.1 każdy wyjątek z `onCreate` i z zakładek jest pokazywany
na ekranie i zapisywany w `files/crash.txt`. Wtedy wystarczy zdjęcie ekranu.
