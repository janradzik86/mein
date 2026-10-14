# Stan buildu CI — blokada billingowa konta

## Dowód z API GitHuba (adnotacja check-run 115632539117, run 38496245640)

```
failure | .github:1 | The job was not started because your account is locked due to a billing issue.
notice  | .github:1 | The ubuntu-latest label will migrate to Ubuntu 26 beginning October 19, 2026.
```

* run: https://github.com/janradzik86/mein/actions/runs/38496245640
* job `Lint, testy jednostkowe, podpisane APK`: conclusion=failure, **0 wykonanych kroków**, runner_name pusty
  → job nie wystartował w ogóle, nie doszło do kompilacji
* job `Testy instrumentowane (emulator, best-effort)`: skipped (zależny od `verify`)
* repozytorium: PUBLICZNE od 2026-10-14 01:31 UTC (zmiana wykonana przez właściciela) — nie pomogła,
  bo blokada jest na poziomie konta, nie repozytorium

## Wniosek

Przyczyną wszystkich wcześniejszych `startup_failure` (także dla workflow hello-world) była blokada
billingowa konta. Nie jest to błąd kodu, konfiguracji Gradle ani pliku workflow.

## Co odblokowuje build

1. **Naprawa billingu**: https://github.com/settings/billing (metoda płatności / zaległa faktura /
   spending limit). Po odblokowaniu wystarczy ponownie uruchomić run — workflow zbuduje, podpisze,
   zweryfikuje i opublikuje APK w Release `terminownik-v1.0.0`.
2. **Build lokalny bez GitHuba** (nie zależy od billingu):
   `cd terminownik-android && bash scripts/build-release.sh` → `dist/terminownik-release.apk`.

## Stan artefaktów

| Artefakt | Stan |
| --- | --- |
| Kod aplikacji (99 plików) | wypchnięty, gałąź `arena/3c74200e-mein`, commit `3255265` |
| Workflow CI + skrypty podpisywania i diagnostyki | gotowe i zwalidowane statycznie |
| Podpisany APK | **nie zbudowany** — blokada billingowa |
| Testy jednostkowe w Gradle / lint / testy instrumentowane | **nie uruchomione** — j.w. |
| Logika biznesowa (67 scenariuszy) i spójność stringów (375/375) | **wykonane lokalnie, PASS** |
