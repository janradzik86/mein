#!/usr/bin/env bash
# test_ai_model.sh — wykonawczy test neuronowego generatora wzorów.
# Generuje N projektów różnymi promptami, sprawdza że w każdym jest rysunek z modelu,
# waliduje geometrię, mierzy czas i zapisuje dowód do RELEASE/TEST-MODEL-AI.txt
set -uo pipefail
cd "$(dirname "$0")/.."
OUT=${1:-build/test_ai}
REPORT=RELEASE/TEST-MODEL-AI.txt
mkdir -p "$OUT"
: > "$REPORT"

echo "TEST MODELU AI — $(date -u '+%Y-%m-%d %H:%M UTC')" | tee -a "$REPORT"
echo "kod: core/aimodel.cpp (wnioskowanie), core/compose.cpp (wpięcie)" | tee -a "$REPORT"
echo "model: modele/ai-generator.bin" | tee -a "$REPORT"
ls -l modele/ai-generator.bin | awk '{print "rozmiar: "$5" B"}' | tee -a "$REPORT"
echo | tee -a "$REPORT"
printf "%-38s %-10s %s\n" "PROMPT" "CZAS[ms]" "RYSUNEK AI / WALIDACJA" | tee -a "$REPORT"

PROMPTS=(
  "wilk na tle gór, rycina, dla ojca"
  "mandala w stylu retro na deskę do krojenia"
  "statek żaglowy, kompas i kotwica, tabliczka"
  "sowa na gałęzi, minimal, prezent dla mamy"
  "motocykl retro, szyld do garażu"
  "piersiówka z motywem łowiectwa, jelen"
  "smok w stylu gotyckim, medal"
  "kwiaty i motyle, elegancki, na urodziny"
)
i=0
ok=0
for p in "${PROMPTS[@]}"; do
  i=$((i+1))
  t0=$(date +%s%N)
  ./build/wojan-cli generate --prompt "$p" --out "$OUT/p$i" >"$OUT/p$i.log" 2>&1
  t1=$(date +%s%N)
  ms=$(( (t1 - t0) / 1000000 ))
  proj=$(ls "$OUT/p$i"/*.wlcproj 2>/dev/null | head -1)
  if [ -z "$proj" ]; then
    printf "%-38s %-10s BŁĄD generowania\n" "$p" "$ms" | tee -a "$REPORT"
    continue
  fi
  ai=$(python3 -c "
import json,sys
d=json.load(open('$proj'))
objs=[o for o in d.get('objects',[]) if o.get('name')=='Rysunek AI']
seg=sum(o.get('d','').count('L') for o in objs)
print(len(objs), seg)
")
  n_obj=$(echo "$ai" | cut -d' ' -f1)
  n_seg=$(echo "$ai" | cut -d' ' -f2)
  val=$(grep -c "błąd" "$OUT/p$i.log" 2>/dev/null || true)
  if [ "$n_obj" -ge 1 ]; then ok=$((ok+1)); fi
  printf "%-38s %-10s obiektów:%s segmentów:%s walidacja:%s\n" "$p" "$ms" "$n_obj" "$n_seg" "$(grep -o 'błędów: [0-9]*, ostrzeżeń: [0-9]*' "$OUT/p$i.log" | head -1)" | tee -a "$REPORT"
done
echo | tee -a "$REPORT"
echo "projekty z rysunkiem z modelu: $ok / $i" | tee -a "$REPORT"
echo "pliki wyjściowe: $OUT" | tee -a "$REPORT"
