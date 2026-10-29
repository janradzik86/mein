@echo off
REM pobiera model whisper.cpp (ggml-base-q5_1.bin) do tego katalogu
setlocal
cd /d "%~dp0"
where curl >nul 2>&1
if errorlevel 1 (
  echo Brak programu curl. Pobierz plik recznie:
  echo https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin
  pause
  exit /b 1
)
echo Pobieram model (~59,7 MB)...
curl -L --fail -o ggml-base-q5_1.bin "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
if errorlevel 1 (
  echo Blad pobierania. Pobierz plik recznie z adresu w README-MODEL.txt
  pause
  exit /b 1
)
for %%A in (ggml-base-q5_1.bin) do echo Pobrano %%A (%%~zA bajtow)
echo Gotowe. Uruchom program i zakladke "Mikrofon i prompt".
pause
