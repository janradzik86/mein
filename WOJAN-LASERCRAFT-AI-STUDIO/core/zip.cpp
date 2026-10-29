#include "zip.h"
#include "common.h"
#include <algorithm>

namespace lc {

static void put16(std::vector<uint8_t>& v, uint16_t x) { v.push_back((uint8_t)x); v.push_back((uint8_t)(x >> 8)); }
static void put32(std::vector<uint8_t>& v, uint32_t x) {
  v.push_back((uint8_t)x); v.push_back((uint8_t)(x >> 8)); v.push_back((uint8_t)(x >> 16)); v.push_back((uint8_t)(x >> 24));
}
static uint16_t rd16(const uint8_t* p) { return (uint16_t)(p[0] | (p[1] << 8)); }
static uint32_t rd32(const uint8_t* p) { return (uint32_t)(p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24)); }

void ZipWriter::add(const std::string& name, const std::string& data, bool compress) {
  add(name, std::vector<uint8_t>(data.begin(), data.end()), compress);
}
void ZipWriter::add(const std::string& name, const std::vector<uint8_t>& data, bool compress) {
  Entry e;
  e.name = name;
  e.crc = crc32(data.data(), data.size());
  e.usize = (uint32_t)data.size();
  e.offset = (uint32_t)body_.size();
  std::vector<uint8_t> payload;
  if (compress && data.size() > 64) {
    payload = deflateRaw(data, 6);
    e.deflated = payload.size() < data.size();
    if (!e.deflated) payload = data;
  } else {
    payload = data;
    e.deflated = false;
  }
  put32(body_, 0x04034b50);
  put16(body_, 20);
  put16(body_, 0);
  put16(body_, e.deflated ? 8 : 0);
  put16(body_, 0);
  put16(body_, 0x21);
  put32(body_, e.crc);
  put32(body_, (uint32_t)payload.size());
  put32(body_, e.usize);
  put16(body_, (uint16_t)name.size());
  put16(body_, 0);
  body_.insert(body_.end(), name.begin(), name.end());
  body_.insert(body_.end(), payload.begin(), payload.end());
  files_.push_back(e);
}
std::vector<uint8_t> ZipWriter::finish() {
  std::vector<uint8_t> out = body_;
  uint32_t cdStart = (uint32_t)out.size();
  for (auto& e : files_) {
    put32(out, 0x02014b50);
    put16(out, 20);
    put16(out, 20);
    put16(out, 0);
    put16(out, e.deflated ? 8 : 0);
    put16(out, 0);
    put16(out, 0x21);
    put32(out, e.crc);
    put32(out, 0);
    put32(out, e.usize);
    put16(out, (uint16_t)e.name.size());
    put16(out, 0);
    put16(out, 0);
    put16(out, 0);
    put16(out, 0);
    put32(out, 0);
    put32(out, e.offset);
    out.insert(out.end(), e.name.begin(), e.name.end());
  }
  uint32_t cdSize = (uint32_t)out.size() - cdStart;
  put32(out, 0x06054b50);
  put16(out, 0);
  put16(out, 0);
  put16(out, (uint16_t)files_.size());
  put16(out, (uint16_t)files_.size());
  put32(out, cdSize);
  put32(out, cdStart);
  put16(out, 0);
  return out;
}

bool zipNameSafe(const std::string& name, std::string* why) {
  if (name.empty()) { if (why) *why = "pusta nazwa"; return false; }
  if (name[0] == '/' || name[0] == '\\') { if (why) *why = "ścieżka bezwzględna"; return false; }
  if (name.find(':') != std::string::npos) { if (why) *why = "dysk w nazwie"; return false; }
  auto parts = split(name, '/');
  for (auto& p : parts) {
    if (p == "..") { if (why) *why = "próba wyjścia z katalogu (..)"; return false; }
    if (p.find('\\') != std::string::npos) { if (why) *why = "backslash w ścieżce"; return false; }
  }
  return true;
}

bool ZipReader::open(const std::vector<uint8_t>& zip, std::string* err) {
  entries_.clear();
  if (zip.size() < 22) { if (err) *err = "archiwum za małe"; return false; }
  // znajdź EOCD (od końca)
  size_t eocd = std::string::npos;
  for (size_t i = zip.size() - 22 + 1; i-- > 0;) {
    if (rd32(&zip[i]) == 0x06054b50) { eocd = i; break; }
    if (zip.size() > i + 40000) break;
  }
  if (eocd == std::string::npos) { if (err) *err = "brak nagłówka EOCD"; return false; }
  uint16_t count = rd16(&zip[eocd + 10]);
  uint32_t cdOff = rd32(&zip[eocd + 16]);
  size_t p = cdOff;
  for (uint16_t i = 0; i < count; i++) {
    if (p + 46 > zip.size() || rd32(&zip[p]) != 0x02014b50) { if (err) *err = "uszkodzony katalog centralny"; return false; }
    uint16_t method = rd16(&zip[p + 10]);
    uint32_t crc = rd32(&zip[p + 16]);
    uint32_t csize = rd32(&zip[p + 20]);
    uint32_t usize = rd32(&zip[p + 24]);
    uint16_t nlen = rd16(&zip[p + 28]);
    uint16_t elen = rd16(&zip[p + 30]);
    uint16_t clen = rd16(&zip[p + 32]);
    uint32_t localOff = rd32(&zip[p + 42]);
    std::string name((const char*)&zip[p + 46], nlen);
    p += 46 + nlen + elen + clen;
    std::string why;
    if (!zipNameSafe(name, &why)) { if (err) *err = "niebezpieczna nazwa wpisu: " + name + " (" + why + ")"; return false; }
    if (localOff + 30 > zip.size()) { if (err) *err = "zły offset wpisu"; return false; }
    uint16_t lnlen = rd16(&zip[localOff + 26]);
    uint16_t lelen = rd16(&zip[localOff + 28]);
    size_t dataOff = localOff + 30 + lnlen + lelen;
    if (dataOff + csize > zip.size()) { if (err) *err = "wpis poza archiwum"; return false; }
    ZipEntry e;
    e.name = name;
    if (method == 0) {
      e.data.assign(zip.begin() + dataOff, zip.begin() + dataOff + csize);
    } else if (method == 8) {
      if (!inflateRaw(&zip[dataOff], csize, e.data)) { if (err) *err = "błąd dekompresji: " + name; return false; }
    } else {
      if (err) *err = "nieobsługiwana metoda kompresji";
      return false;
    }
    if (e.data.size() != usize || crc32(e.data.data(), e.data.size()) != crc) {
      if (err) *err = "niezgodny CRC: " + name;
      return false;
    }
    entries_[name] = e;
  }
  return true;
}
bool ZipReader::openFile(const std::string& path, std::string* err) {
  std::vector<uint8_t> bytes;
  if (!readFileBytes(path, bytes)) { if (err) *err = "nie można otworzyć pliku"; return false; }
  return open(bytes, err);
}
std::vector<std::string> ZipReader::names() const {
  std::vector<std::string> n;
  for (auto& kv : entries_) n.push_back(kv.first);
  return n;
}
bool ZipReader::read(const std::string& name, std::vector<uint8_t>& out) const {
  auto it = entries_.find(name);
  if (it == entries_.end()) return false;
  out = it->second.data;
  return true;
}
bool ZipReader::readText(const std::string& name, std::string& out) const {
  std::vector<uint8_t> b;
  if (!read(name, b)) return false;
  out.assign(b.begin(), b.end());
  return true;
}
bool ZipReader::has(const std::string& name) const { return entries_.count(name) > 0; }
uint64_t ZipReader::totalUncompressed() const {
  uint64_t t = 0;
  for (auto& kv : entries_) t += kv.second.data.size();
  return t;
}

}  // namespace lc
