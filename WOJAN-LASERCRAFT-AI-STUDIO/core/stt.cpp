// stt.cpp — implementacja lokalnego STT na whisper.cpp.
#include "stt.h"

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <algorithm>
#include <chrono>

#include "whisper.h"

namespace stt {
namespace {

struct WavInfo {
  int channels = 1;
  int rate = 16000;
  int bits = 16;
  size_t frames = 0;
  size_t data_offset = 0;
};

bool read_wav_header(const std::string& path, WavInfo& w, std::string& err) {
  FILE* f = fopen(path.c_str(), "rb");
  if (!f) {
    err = "nie można otworzyć pliku: " + path;
    return false;
  }
  char riff[12];
  if (fread(riff, 1, 12, f) != 12 || memcmp(riff, "RIFF", 4) != 0 || memcmp(riff + 8, "WAVE", 4) != 0) {
    fclose(f);
    err = "to nie jest plik WAV (brak nagłówka RIFF/WAVE)";
    return false;
  }
  bool have_fmt = false;
  while (true) {
    char id[4];
    uint32_t size = 0;
    if (fread(id, 1, 4, f) != 4) break;
    if (fread(&size, 4, 1, f) != 1) break;
    if (memcmp(id, "fmt ", 4) == 0) {
      uint16_t fmt = 0;
      uint16_t ch = 0, bits = 0;
      uint32_t rate = 0;
      if (fread(&fmt, 2, 1, f) != 1 || fread(&ch, 2, 1, f) != 1 || fread(&rate, 4, 1, f) != 1) {
        fclose(f);
        err = "uszkodzony nagłówek fmt";
        return false;
      }
      fseek(f, 6, SEEK_CUR);  // byte rate + block align
      if (fread(&bits, 2, 1, f) != 1) {
        fclose(f);
        err = "uszkodzony nagłówek fmt";
        return false;
      }
      if (size > 16) fseek(f, (long)(size - 16), SEEK_CUR);
      w.channels = ch ? ch : 1;
      w.rate = rate ? (int)rate : 16000;
      w.bits = bits;
      have_fmt = true;
      if (fmt != 1 || bits != 16) {
        fclose(f);
        err = "obsługiwany jest tylko WAV PCM 16-bit (ten plik ma format " +
              std::to_string(fmt) + ", " + std::to_string(bits) + " bit)";
        return false;
      }
    } else if (memcmp(id, "data", 4) == 0) {
      w.data_offset = (size_t)ftell(f);
      w.frames = size;
      fclose(f);
      return have_fmt;
    } else {
      fseek(f, (long)size, SEEK_CUR);
    }
  }
  fclose(f);
  err = "w pliku WAV nie znaleziono danych audio";
  return false;
}

}  // namespace

std::string model_dir() {
  const char* env = getenv("WOJAN_MODELS");
  if (env && *env) return env;
  // katalog programu: „modele” obok pliku wykonywalnego albo w katalogu roboczym
  const char* kandy = "modele";
  return kandy;
}

Status status() {
  Status s;
  s.katalog = model_dir();
  std::string best;
  long long best_size = -1;
  // przeglądamy katalog modeli; bierzemy największy plik ggml-*.bin (najlepsza jakość)
#ifdef _WIN32
  std::string cmd = "dir /b \"" + s.katalog + "\\ggml-*.bin\" 2>nul";
#else
  std::string cmd = "ls -1 \"" + s.katalog + "\"/ggml-*.bin 2>/dev/null";
#endif
  FILE* p = popen(cmd.c_str(), "r");
  if (p) {
    char line[1024];
    while (fgets(line, sizeof line, p)) {
      std::string f(line);
      while (!f.empty() && (f.back() == '\n' || f.back() == '\r')) f.pop_back();
      if (f.empty()) continue;
      std::string full = f;
      if (full.find('/') == std::string::npos && full.find('\\') == std::string::npos)
        full = s.katalog + "/" + f;
      long long sz = -1;
      FILE* g = fopen(full.c_str(), "rb");
      if (g) {
        fseek(g, 0, SEEK_END);
        sz = (long long)ftell(g);
        fclose(g);
      }
      if (sz > best_size) {
        best_size = sz;
        best = full;
      }
    }
    pclose(p);
  }
  if (!best.empty() && best_size > 1024) {
    s.model = true;
    s.model_path = best;
    s.model_bytes = best_size;
    size_t sl = best.find_last_of("/\\");
    s.model_name = sl == std::string::npos ? best : best.substr(sl + 1);
  }
  return s;
}

bool read_wav_16k(const std::string& path, std::vector<float>& pcm, std::string& err) {
  WavInfo w;
  if (!read_wav_header(path, w, err)) return false;
  FILE* f = fopen(path.c_str(), "rb");
  if (!f) {
    err = "nie można otworzyć pliku";
    return false;
  }
  fseek(f, (long)w.data_offset, SEEK_SET);
  std::vector<int16_t> raw(w.frames / 2);
  size_t got = fread(raw.data(), 2, raw.size(), f);
  fclose(f);
  raw.resize(got);
  if (raw.empty()) {
    err = "plik nie zawiera próbek audio";
    return false;
  }
  // mono
  std::vector<float> mono;
  if (w.channels <= 1) {
    mono.resize(raw.size());
    for (size_t i = 0; i < raw.size(); ++i) mono[i] = raw[i] / 32768.0f;
  } else {
    size_t frames = raw.size() / w.channels;
    mono.resize(frames);
    for (size_t i = 0; i < frames; ++i) {
      float acc = 0;
      for (int c = 0; c < w.channels; ++c) acc += raw[i * w.channels + c];
      mono[i] = acc / (32768.0f * w.channels);
    }
  }
  // resampling do 16 kHz (interpolacja liniowa)
  const int target = 16000;
  if (w.rate == target) {
    pcm = std::move(mono);
    return true;
  }
  double ratio = (double)w.rate / target;
  size_t out_n = (size_t)(mono.size() / ratio);
  pcm.resize(out_n);
  for (size_t i = 0; i < out_n; ++i) {
    double pos = i * ratio;
    size_t i0 = (size_t)pos;
    size_t i1 = std::min(i0 + 1, mono.size() - 1);
    double frac = pos - i0;
    pcm[i] = (float)(mono[i0] * (1 - frac) + mono[i1] * frac);
  }
  return true;
}

std::string engine_info() {
  const char* info = whisper_print_system_info();
  std::string v = "whisper.cpp 1.7.4";
  if (info && *info) v += std::string(" | ") + info;
  return v;
}

bool transcribe_wav(const std::string& wav_path, const std::string& language,
                    std::string& text, std::string& err, double* sekundy) {
  text.clear();
  Status st = status();
  if (!st.model) {
    err = "brak pliku modelu w katalogu „modele” (wymagany ggml-*.bin, np. ggml-base-q5_1.bin). "
          "Silnik whisper.cpp jest wkompilowany, ale wagi trzeba włożyć do katalogu „modele”. "
          "Sprawdzony katalog: " + st.katalog;
    return false;
  }
  std::vector<float> pcm;
  if (!read_wav_16k(wav_path, pcm, err)) return false;
  if (pcm.size() < 1600) {
    err = "nagranie jest za krótkie (minimum 0,1 s)";
    return false;
  }

  whisper_context_params cp = whisper_context_default_params();
  cp.use_gpu = false;
  cp.flash_attn = false;
  whisper_context* ctx = whisper_init_from_file_with_params(st.model_path.c_str(), cp);
  if (!ctx) {
    err = "nie udało się wczytać modelu: " + st.model_path +
          " (uszkodzony plik albo nieobsługiwany format ggml)";
    return false;
  }

  whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  wp.print_realtime = false;
  wp.print_progress = false;
  wp.print_timestamps = false;
  wp.translate = false;
  wp.single_segment = false;
  wp.no_context = true;
  std::string lang = language.empty() ? "pl" : language;
  if (lang != "auto") {
    wp.language = lang.c_str();
    wp.detect_language = false;
  } else {
    wp.language = nullptr;
    wp.detect_language = true;
  }
  wp.n_threads = 2;

  auto t0 = std::chrono::steady_clock::now();
  int rc = whisper_full(ctx, wp, pcm.data(), (int)pcm.size());
  auto t1 = std::chrono::steady_clock::now();
  if (sekundy) *sekundy = std::chrono::duration<double>(t1 - t0).count();
  if (rc != 0) {
    whisper_free(ctx);
    err = "transkrypcja nie powiodła się (kod " + std::to_string(rc) + ")";
    return false;
  }
  int n = whisper_full_n_segments(ctx);
  for (int i = 0; i < n; ++i) {
    const char* s = whisper_full_get_segment_text(ctx, i);
    if (s) text += s;
  }
  whisper_free(ctx);
  // porządkowanie: usuwamy znaczniki [?] i sklejamy spacje
  std::string clean;
  for (size_t i = 0; i < text.size(); ++i) {
    if (text[i] == '[') {
      size_t j = text.find(']', i);
      if (j != std::string::npos) { i = j; continue; }
    }
    clean.push_back(text[i]);
  }
  std::string out;
  bool space = false;
  for (char c : clean) {
    if (c == ' ' || c == '\n' || c == '\t' || c == '\r') { space = true; continue; }
    if (space && !out.empty()) out.push_back(' ');
    space = false;
    out.push_back(c);
  }
  text = out;
  return true;
}

}  // namespace stt
