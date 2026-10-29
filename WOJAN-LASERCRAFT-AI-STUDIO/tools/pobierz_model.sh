#!/usr/bin/env bash
# pobiera model whisper.cpp do katalogu „modele” (Linux/macOS/WSL)
set -eu
DIR="${1:-modele}"
mkdir -p "$DIR"
URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
echo "Pobieram $URL"
curl -L --fail -o "$DIR/ggml-base-q5_1.bin" "$URL"
SIZE=$(stat -c%s "$DIR/ggml-base-q5_1.bin" 2>/dev/null || stat -f%z "$DIR/ggml-base-q5_1.bin")
echo "Rozmiar: $SIZE B (oczekiwane 59707625)"
[ "$SIZE" -eq 59707625 ] || echo "UWAGA: inny rozmiar niż oczekiwany — sprawdź plik"
echo "Gotowe: $DIR/ggml-base-q5_1.bin"
