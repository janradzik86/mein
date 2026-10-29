#!/usr/bin/env bash
# test_stt.sh — wykonawczy test rozpoznawania mowy (ten sam kod co w aplikacji).
#   bash tools/test_stt.sh            # test EN na samples/jfk.wav + PL na wygenerowanym nagraniu
# Wynik zapisywany w build/test_stt.txt (dowód do raportu).
set -uo pipefail
cd "$(dirname "$0")/.."
OUT=build/test_stt.txt; : > "$OUT"
SRC=third_party/whisper.cpp
MODEL_DIR=modele
[ -f "$MODEL_DIR/ggml-base-q5_1.bin" ] || { echo "brak modelu $MODEL_DIR/ggml-base-q5_1.bin" | tee -a "$OUT"; exit 1; }

echo "== budowa biblioteki whisper.cpp ==" | tee -a "$OUT"
bash tools/build_whisper.sh linux >>"$OUT" 2>&1 || { echo "BŁĄD kompilacji" | tee -a "$OUT"; exit 1; }

cat > /tmp/stt_cli_test.cpp <<'CPP'
#include "stt.h"
#include <cstdio>
int main(int argc, char** argv) {
  if (argc < 3) return 1;
  std::string text, err; double sek = 0;
  bool ok = stt::transcribe_wav(argv[1], argv[2], text, err, &sek);
  printf("  ok=%d czas=%.2fs\n  %s\n", (int)ok, sek, ok ? text.c_str() : err.c_str());
  return ok ? 0 : 2;
}
CPP
g++ -O3 -std=c++17 -mavx2 -mfma -mf16c -I core -I "$SRC/include" -I "$SRC/ggml/include" \
    /tmp/stt_cli_test.cpp core/stt.cpp build/whisper/libwhisper.a -o /tmp/stt_cli_test -lpthread -lm \
    >>"$OUT" 2>&1 || { echo "BŁĄD linkowania" | tee -a "$OUT"; exit 1; }

echo "== test EN (samples/jfk.wav, oczekiwane: 'And so my fellow Americans...') ==" | tee -a "$OUT"
/tmp/stt_cli_test "$SRC/samples/jfk.wav" en 2>/dev/null | tee -a "$OUT"

echo "== test PL (espeak-ng, głos pl) ==" | tee -a "$OUT"
python3 - "$OUT" <<'PY' 2>&1 | tee -a "$OUT"
import ctypes, wave, sys
try:
    from espeakng_loader import get_library_path, get_data_path
except ImportError:
    print("  pominięto: brak pakietu espeakng-loader (pip install espeakng-loader)")
    raise SystemExit(0)
lib = ctypes.CDLL(str(get_library_path()))
lib.espeak_Initialize.restype = ctypes.c_int
lib.espeak_Initialize.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_char_p, ctypes.c_int]
sr = lib.espeak_Initialize(2, 0, str(get_data_path()).encode(), 0)
lib.espeak_SetVoiceByName.argtypes = [ctypes.c_char_p]
lib.espeak_SetVoiceByName(b"pl")
CB = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.POINTER(ctypes.c_short), ctypes.c_int, ctypes.c_void_p)
buf = bytearray()
def cb(wav, n, ev):
    if n > 0:
        buf.extend(ctypes.string_at(wav, n * 2))
    return 0
cbp = CB(cb)
lib.espeak_SetSynthCallback.argtypes = [CB]
lib.espeak_SetSynthCallback(cbp)
lib.espeak_Synth.argtypes = [ctypes.c_char_p, ctypes.c_size_t, ctypes.c_uint, ctypes.c_int,
                             ctypes.c_uint, ctypes.c_uint, ctypes.c_void_p, ctypes.c_void_p]
tekst = ("Cześć, tu Wojan Laser Craft. Projektuję tabliczkę z grawerem dla żony. "
         "Piersiówka, stal nierdzewna, dwadzieścia centymetrów.").encode()
lib.espeak_Synth(tekst, len(tekst) + 1, 0, 1, 0, 0, None, None)
with wave.open("/tmp/polski_test.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(bytes(buf))
print(f"  nagranie: /tmp/polski_test.wav ({len(buf)/2/sr:.1f} s, {sr} Hz)")
PY
/tmp/stt_cli_test /tmp/polski_test.wav pl 2>/dev/null | tee -a "$OUT"
echo "== gotowe, wynik w $OUT ==" | tee -a "$OUT"
