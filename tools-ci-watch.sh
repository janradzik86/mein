#!/usr/bin/env bash
# Automatyzacja: czeka, aż właściciel upubliczni repozytorium, wyzwala build CI,
# a po zakończeniu zapisuje realne wyniki do /home/user/mein/CI_RESULT.md.
#
# Dzięki temu potrzebne jest JEDNO kliknięcie po stronie właściciela – dalej wszystko
# dzieje się samo, a plik APK ląduje w GitHub Release.
set -uo pipefail

REPO="janradzik86/mein"
BRANCH="arena/3c74200e-mein"
ROOT="/home/user/mein"
RESULT="$ROOT/CI_RESULT.md"
API="https://api.github.com"

TOKEN="$(gh auth token)"
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")

log() { echo "[$(date -u '+%H:%M:%S')] $*"; }

visibility() {
  curl -sS "${AUTH[@]}" "$API/repos/$REPO" | python3 -c "import json,sys;print(json.load(sys.stdin).get('visibility'))" 2>/dev/null
}

# --- 1. czekaj na upublicznienie (max 3h) -------------------------------------------------
for i in $(seq 1 360); do
  VIS="$(visibility)"
  if [[ "$VIS" == "public" ]]; then
    log "repozytorium PUBLICZNE - wyzwalam build"
    break
  fi
  if (( i % 10 == 1 )); then log "widocznosc: ${VIS:-?} (oczekuje na publiczne, proba $i/360)"; fi
  sleep 30
done

VIS="$(visibility)"
if [[ "$VIS" != "public" ]]; then
  {
    echo "# Build nie uruchomiony"
    echo
    echo "Repozytorium nadal prywatne po 3h oczekiwania (widocznosc: ${VIS:-?})."
    echo "GitHub Actions nie startuje jobow dla tego repo (startup_failure)."
  } > "$RESULT"
  log "koniec: repo nadal prywatne"
  exit 0
fi

# --- 2. commit wyzwalajacy (plik w terminownik-android/** pasuje do filtra paths) ---------
cd "$ROOT" || exit 1
git fetch -q origin "$BRANCH" || true
git rebase -q "origin/$BRANCH" 2>/dev/null || git reset --soft "origin/$BRANCH"
date -u '+Wyzwolono build: %Y-%m-%d %H:%M:%S UTC' > terminownik-android/.ci-trigger
git add terminownik-android/.ci-trigger
git -c user.name="Arena Agent" -c user.email="agent@arena.ai" \
    commit -q -m "chore(ci): wyzwolenie buildu wydania po udostepnieniu repozytorium" || true
for attempt in 1 2 3; do
  if git push -q origin "$BRANCH"; then log "push wyzwalajacy OK (proba $attempt)"; break; fi
  log "push nieudany (proba $attempt) - ponawiam"
  sleep 10
  git pull -q --rebase origin "$BRANCH" || true
done
TRIGGER_SHA="$(git rev-parse HEAD)"
log "commit wyzwalajacy: $TRIGGER_SHA"

# --- 3. znajdz run i czekaj na zakonczenie (max 50 min) ----------------------------------
RUN_ID=""
for i in $(seq 1 20); do
  RUN_ID="$(curl -sS "${AUTH[@]}" "$API/repos/$REPO/actions/runs?head_sha=$TRIGGER_SHA&per_page=5" \
    | python3 -c "
import json,sys
d=json.load(sys.stdin)
runs=d.get('workflow_runs',[])
print(runs[0]['id'] if runs else '')
" 2>/dev/null)"
  [[ -n "$RUN_ID" ]] && break
  # fallback: najnowszy run
  RUN_ID="$(curl -sS "${AUTH[@]}" "$API/repos/$REPO/actions/runs?per_page=1" \
    | python3 -c "
import json,sys
d=json.load(sys.stdin)
runs=d.get('workflow_runs',[])
print(runs[0]['id'] if runs else '')
" 2>/dev/null)"
  [[ -n "$RUN_ID" ]] && break
  sleep 15
done
log "run id: ${RUN_ID:-brak}"

STATUS="unknown"; CONCLUSION=""
if [[ -n "$RUN_ID" ]]; then
  for i in $(seq 1 100); do
    read -r STATUS CONCLUSION < <(curl -sS "${AUTH[@]}" "$API/repos/$REPO/actions/runs/$RUN_ID" \
      | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(d.get('status'), d.get('conclusion') or '')
" 2>/dev/null)
    if [[ "$STATUS" == "completed" ]]; then log "run zakonczony: $CONCLUSION"; break; fi
    sleep 30
  done
fi

# --- 4. zbierz wyniki ---------------------------------------------------------------------
{
  echo "# Wynik buildu CI (automatyczny zapis)"
  echo
  echo "* repo: $REPO (widocznosc: $(visibility))"
  echo "* galez: $BRANCH"
  echo "* commit wyzwalajacy: \`$TRIGGER_SHA\`"
  echo "* run: ${RUN_ID:-brak} | status: $STATUS | konkluzja: ${CONCLUSION:-brak}"
  echo "* czas zapisu: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
  echo
  echo "## Joby"
  echo '```'
  if [[ -n "$RUN_ID" ]]; then
    curl -sS "${AUTH[@]}" "$API/repos/$REPO/actions/runs/$RUN_ID/jobs" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print('total jobs:', d.get('total_count'))
for j in d.get('jobs',[]):
    print(f\"- {j.get('name')} | {j.get('status')} | {j.get('conclusion')}\")
    for s in j.get('steps',[]):
        print(f\"    * {s.get('name')}: {s.get('conclusion')}\")
"
  fi
  echo '```'
  echo
  echo "## Release z APK"
  echo '```'
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/releases?per_page=5" | python3 -c "
import json,sys
d=json.load(sys.stdin)
if isinstance(d, dict): print(d); sys.exit()
for r in d:
    print('tag:', r.get('tag_name'), '| name:', r.get('name'), '| published:', r.get('published_at'))
    for a in r.get('assets',[]):
        print('   asset:', a.get('name'), '|', a.get('size'), 'bajtow |', a.get('browser_download_url'))
"
  echo '```'
  echo
  echo "## Tresc wydania (weryfikacja podpisu, SHA-256, testy)"
  echo '```'
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/releases/tags/terminownik-v1.0.0" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(d.get('body') or d)
" 2>/dev/null | head -200
  echo '```'
  echo
  echo "## Diagnostyka (jesli build sie nie udal)"
  echo '```'
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/contents/.github/ci-diagnostics/terminownik-build.md?ref=$BRANCH" \
    | python3 -c "
import json,sys,base64
d=json.load(sys.stdin)
c=d.get('content')
print(base64.b64decode(c).decode('utf-8','replace')[:8000] if c else d.get('message','brak pliku diagnostyki'))
" 2>/dev/null
  echo '```'
} > "$RESULT"

log "wyniki zapisane: $RESULT"
