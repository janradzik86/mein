#!/usr/bin/env bash
# Automatyczne ponawianie buildu, az konto GitHub zostanie odblokowane billingowo.
#
# Co 10 minut ponawia run; gdy joby faktycznie wystartuja (kroki > 0), czeka na zakonczenie
# i zapisuje realne wyniki (kroki, Release, tresc wydania, diagnostyke bledow kompilacji)
# do /home/user/mein/CI_RESULT.md.
set -uo pipefail

REPO="janradzik86/mein"
BRANCH="arena/3c74200e-mein"
RUN_ID="${1:-38496245640}"
RESULT="/home/user/mein/CI_RESULT.md"
API="https://api.github.com"
TOKEN="$(gh auth token)"
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")

log() { echo "[$(date -u '+%H:%M:%S')] $*"; }

job_state() {  # -> "steps conclusion"
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/actions/runs/$RUN_ID/jobs" | python3 -c "
import json,sys
d=json.load(sys.stdin)
jobs=[j for j in d.get('jobs',[]) if j.get('name','').startswith('Lint')]
if not jobs: print('0 none'); sys.exit()
j=jobs[0]
print(len(j.get('steps',[])), j.get('conclusion') or j.get('status'))
" 2>/dev/null
}

billing_locked() {
  local job_id
  job_id="$(curl -sS "${AUTH[@]}" "$API/repos/$REPO/actions/runs/$RUN_ID/jobs" | python3 -c "
import json,sys
d=json.load(sys.stdin)
jobs=[j for j in d.get('jobs',[]) if j.get('name','').startswith('Lint')]
print(jobs[0]['id'] if jobs else '')
" 2>/dev/null)"
  [[ -z "$job_id" ]] && return 1
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/check-runs/$job_id/annotations" | grep -qi "billing issue"
}

for attempt in $(seq 1 36); do   # 36 * 10 min = 6 h
  read -r STEPS CONCLUSION < <(job_state)
  log "proba $attempt/36: kroki=$STEPS stan=$CONCLUSION"

  if (( STEPS > 0 )); then
    log "joby wystartowaly - czekam na zakonczenie buildu"
    for i in $(seq 1 80); do
      read -r STEPS CONCLUSION < <(job_state)
      if [[ "$CONCLUSION" == "success" || "$CONCLUSION" == "failure" ]]; then
        log "build zakonczony: $CONCLUSION (kroki: $STEPS)"
        break
      fi
      sleep 30
    done
    break
  fi

  if billing_locked; then
    log "konto nadal zablokowane billingowo - ponawiam za 10 min"
  else
    log "brak adnotacji billingowej, a kroki=0 - ponawiam za 10 min"
  fi
  curl -sS -o /dev/null -X POST "${AUTH[@]}" "$API/repos/$REPO/actions/runs/$RUN_ID/rerun-failed-jobs" || true
  sleep 600
done

read -r STEPS CONCLUSION < <(job_state)
{
  echo "# Wynik buildu CI (zapis automatyczny)"
  echo
  echo "* run: https://github.com/$REPO/actions/runs/$RUN_ID"
  echo "* galez: $BRANCH"
  echo "* zakonczenie joba buildu: **${CONCLUSION}** (wykonanych krokow: ${STEPS})"
  echo "* czas zapisu: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
  echo
  echo "## Kroki joba"
  echo '```'
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/actions/runs/$RUN_ID/jobs" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for j in d.get('jobs',[]):
    print(f\"JOB {j.get('name')} | {j.get('conclusion')}\")
    for s in j.get('steps',[]):
        print(f\"   - {s.get('number')}. {s.get('name')}: {s.get('conclusion')}\")
"
  echo '```'
  echo
  echo "## Release z APK"
  echo '```'
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/releases?per_page=5" | python3 -c "
import json,sys
d=json.load(sys.stdin)
if isinstance(d,dict): print(d.get('message')); sys.exit()
if not d: print('brak wydania')
for r in d:
    print('tag:', r.get('tag_name'), '| published:', r.get('published_at'))
    for a in r.get('assets',[]):
        print('   asset:', a.get('name'), '|', a.get('size'), 'bajtow')
        print('   url:', a.get('browser_download_url'))
"
  echo '```'
  echo
  echo "## Tresc wydania (apksigner, SHA-256, testy)"
  echo '```'
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/releases/tags/terminownik-v1.0.0" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(d.get('body') or d.get('message'))
" 2>/dev/null | head -160
  echo '```'
  echo
  echo "## Diagnostyka bledow kompilacji (jesli build padl)"
  echo '```'
  curl -sS "${AUTH[@]}" "$API/repos/$REPO/contents/.github/ci-diagnostics/terminownik-build.md?ref=$BRANCH" \
    | python3 -c "
import json,sys,base64
d=json.load(sys.stdin)
c=d.get('content')
print(base64.b64decode(c).decode('utf-8','replace')[:6000] if c else 'brak pliku diagnostyki: '+str(d.get('message')))
" 2>/dev/null
  echo '```'
} > "$RESULT"

log "wyniki zapisane: $RESULT"
