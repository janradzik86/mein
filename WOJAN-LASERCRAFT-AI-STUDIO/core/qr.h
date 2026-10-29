// Generator kodów QR (tryb bajtowy, wersje 1–10, poziomy korekcji L/M/Q/H)
#pragma once
#include "path.h"

namespace lc {

struct QrCode {
  int size = 0;                 // liczba modułów
  std::vector<uint8_t> modules;  // 1 = ciemny
  bool ok = false;
  std::string error;
  int version = 0;
  char ecc = 'M';
  int mask = 0;
  bool at(int x, int y) const { return x >= 0 && y >= 0 && x < size && y < size && modules[(size_t)y * size + x]; }
};

QrCode qrEncode(const std::string& data, char ecc = 'M', int minVersion = 1, int forceMask = -1);
// Ścieżka SVG z kodem (jeden prostokąt na moduł, scalone poziomo) — origin (0,0), bok = sizeMm
Path qrPath(const std::string& data, double sizeMm, char ecc = 'M');
// Test strukturalny: wzorce, informacja formatu (BCH), poziom korekcji
std::string qrSelfCheck(const QrCode& qr);
// Odczyt zwrotny macierzy → dane (test round-trip; nie zastępuje dekodera zewnętrznego)
bool qrDecode(const QrCode& qr, std::string& out);

}  // namespace lc
