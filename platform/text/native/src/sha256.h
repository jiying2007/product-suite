#ifndef JINGDU_SHA256_H
#define JINGDU_SHA256_H

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>

namespace jingdu {

class Sha256 final {
 public:
  Sha256();
  void update(const uint8_t* data, size_t size);
  std::array<uint8_t, 32> finish();

 private:
  void transform(const uint8_t block[64]);
  std::array<uint32_t, 8> state_{};
  std::array<uint8_t, 64> buffer_{};
  uint64_t total_bytes_ = 0;
  size_t buffered_ = 0;
  bool finished_ = false;
};

std::string sha256_hex(const uint8_t* data, size_t size);
std::string sha256_file_hex(const std::string& path, bool* ok);

}  // namespace jingdu

#endif
