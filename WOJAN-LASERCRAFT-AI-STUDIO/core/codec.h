// Kompresja: zlib inflate/deflate (dla PNG i ZIP), CRC32, Adler32
#pragma once
#include <cstdint>
#include <cstddef>
#include <vector>

namespace lc {

uint32_t crc32(const uint8_t* data, size_t len, uint32_t crc = 0);
std::vector<uint8_t> zlibCompress(const std::vector<uint8_t>& raw, int level = 6);
bool zlibDecompress(const uint8_t* data, size_t len, std::vector<uint8_t>& out);
std::vector<uint8_t> deflateRaw(const std::vector<uint8_t>& raw, int level = 6);
bool inflateRaw(const uint8_t* data, size_t len, std::vector<uint8_t>& out);

}  // namespace lc
