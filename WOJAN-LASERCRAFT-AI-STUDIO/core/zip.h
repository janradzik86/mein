// ZIP — czytanie i zapisywanie archiwów (deflate/store), bez zależności
#pragma once
#include "codec.h"
#include <map>
#include <string>
#include <vector>

namespace lc {

class ZipWriter {
 public:
  void add(const std::string& name, const std::string& data, bool compress = true);
  void add(const std::string& name, const std::vector<uint8_t>& data, bool compress = true);
  std::vector<uint8_t> finish();
  size_t entries() const { return files_.size(); }

 private:
  struct Entry { std::string name; std::vector<uint8_t> data; bool deflated; uint32_t crc; uint32_t usize; uint32_t offset; };
  std::vector<Entry> files_;
  std::vector<uint8_t> body_;
  bool finished_ = false;
};

struct ZipEntry {
  std::string name;
  std::vector<uint8_t> data;
};

class ZipReader {
 public:
  bool open(const std::vector<uint8_t>& zip, std::string* err = nullptr);
  bool openFile(const std::string& path, std::string* err = nullptr);
  std::vector<std::string> names() const;
  bool read(const std::string& name, std::vector<uint8_t>& out) const;
  bool readText(const std::string& name, std::string& out) const;
  bool has(const std::string& name) const;
  size_t size() const { return entries_.size(); }
  uint64_t totalUncompressed() const;

 private:
  std::map<std::string, ZipEntry> entries_;
};

// Bezpieczna walidacja nazwy wpisu (ochrona przed path traversal)
bool zipNameSafe(const std::string& name, std::string* why = nullptr);

}  // namespace lc
