#include "sha256.h"

#include <algorithm>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <sstream>

namespace jingdu {
namespace {
constexpr uint32_t k[64] = {
    0x428a2f98U, 0x71374491U, 0xb5c0fbcfU, 0xe9b5dba5U, 0x3956c25bU, 0x59f111f1U,
    0x923f82a4U, 0xab1c5ed5U, 0xd807aa98U, 0x12835b01U, 0x243185beU, 0x550c7dc3U,
    0x72be5d74U, 0x80deb1feU, 0x9bdc06a7U, 0xc19bf174U, 0xe49b69c1U, 0xefbe4786U,
    0x0fc19dc6U, 0x240ca1ccU, 0x2de92c6fU, 0x4a7484aaU, 0x5cb0a9dcU, 0x76f988daU,
    0x983e5152U, 0xa831c66dU, 0xb00327c8U, 0xbf597fc7U, 0xc6e00bf3U, 0xd5a79147U,
    0x06ca6351U, 0x14292967U, 0x27b70a85U, 0x2e1b2138U, 0x4d2c6dfcU, 0x53380d13U,
    0x650a7354U, 0x766a0abbU, 0x81c2c92eU, 0x92722c85U, 0xa2bfe8a1U, 0xa81a664bU,
    0xc24b8b70U, 0xc76c51a3U, 0xd192e819U, 0xd6990624U, 0xf40e3585U, 0x106aa070U,
    0x19a4c116U, 0x1e376c08U, 0x2748774cU, 0x34b0bcb5U, 0x391c0cb3U, 0x4ed8aa4aU,
    0x5b9cca4fU, 0x682e6ff3U, 0x748f82eeU, 0x78a5636fU, 0x84c87814U, 0x8cc70208U,
    0x90befffaU, 0xa4506cebU, 0xbef9a3f7U, 0xc67178f2U};

uint32_t rotr(uint32_t value, uint32_t amount) {
  return (value >> amount) | (value << (32U - amount));
}

std::string hex(const std::array<uint8_t, 32>& digest) {
  std::ostringstream out;
  out << std::hex << std::setfill('0');
  for (uint8_t byte : digest) out << std::setw(2) << static_cast<unsigned>(byte);
  return out.str();
}
}  // namespace

Sha256::Sha256()
    : state_{0x6a09e667U, 0xbb67ae85U, 0x3c6ef372U, 0xa54ff53aU,
             0x510e527fU, 0x9b05688cU, 0x1f83d9abU, 0x5be0cd19U} {}

void Sha256::update(const uint8_t* data, size_t size) {
  if (finished_ || (data == nullptr && size != 0)) return;
  total_bytes_ += size;
  while (size > 0) {
    const size_t copy = std::min(size, buffer_.size() - buffered_);
    std::memcpy(buffer_.data() + buffered_, data, copy);
    buffered_ += copy;
    data += copy;
    size -= copy;
    if (buffered_ == buffer_.size()) {
      transform(buffer_.data());
      buffered_ = 0;
    }
  }
}

std::array<uint8_t, 32> Sha256::finish() {
  if (!finished_) {
    const uint64_t bit_length = total_bytes_ * 8U;
    buffer_[buffered_++] = 0x80U;
    if (buffered_ > 56) {
      std::fill(buffer_.begin() + static_cast<std::ptrdiff_t>(buffered_), buffer_.end(), 0);
      transform(buffer_.data());
      buffered_ = 0;
    }
    std::fill(buffer_.begin() + static_cast<std::ptrdiff_t>(buffered_), buffer_.begin() + 56, 0);
    for (int i = 0; i < 8; ++i) {
      buffer_[63 - i] = static_cast<uint8_t>((bit_length >> (i * 8)) & 0xffU);
    }
    transform(buffer_.data());
    buffered_ = 0;
    finished_ = true;
  }

  std::array<uint8_t, 32> digest{};
  for (size_t i = 0; i < state_.size(); ++i) {
    digest[i * 4] = static_cast<uint8_t>((state_[i] >> 24) & 0xffU);
    digest[i * 4 + 1] = static_cast<uint8_t>((state_[i] >> 16) & 0xffU);
    digest[i * 4 + 2] = static_cast<uint8_t>((state_[i] >> 8) & 0xffU);
    digest[i * 4 + 3] = static_cast<uint8_t>(state_[i] & 0xffU);
  }
  return digest;
}

void Sha256::transform(const uint8_t block[64]) {
  uint32_t w[64]{};
  for (int i = 0; i < 16; ++i) {
    const int o = i * 4;
    w[i] = (static_cast<uint32_t>(block[o]) << 24) |
           (static_cast<uint32_t>(block[o + 1]) << 16) |
           (static_cast<uint32_t>(block[o + 2]) << 8) |
           static_cast<uint32_t>(block[o + 3]);
  }
  for (int i = 16; i < 64; ++i) {
    const uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
    const uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
    w[i] = w[i - 16] + s0 + w[i - 7] + s1;
  }

  uint32_t a = state_[0], b = state_[1], c = state_[2], d = state_[3];
  uint32_t e = state_[4], f = state_[5], g = state_[6], h = state_[7];
  for (int i = 0; i < 64; ++i) {
    const uint32_t s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
    const uint32_t ch = (e & f) ^ ((~e) & g);
    const uint32_t temp1 = h + s1 + ch + k[i] + w[i];
    const uint32_t s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
    const uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
    const uint32_t temp2 = s0 + maj;
    h = g; g = f; f = e; e = d + temp1;
    d = c; c = b; b = a; a = temp1 + temp2;
  }
  state_[0] += a; state_[1] += b; state_[2] += c; state_[3] += d;
  state_[4] += e; state_[5] += f; state_[6] += g; state_[7] += h;
}

std::string sha256_hex(const uint8_t* data, size_t size) {
  Sha256 sha;
  sha.update(data, size);
  return hex(sha.finish());
}

std::string sha256_file_hex(const std::string& path, bool* ok) {
  if (ok) *ok = false;
  std::ifstream input(path, std::ios::binary);
  if (!input) return {};
  Sha256 sha;
  std::array<uint8_t, 64 * 1024> buffer{};
  while (input) {
    input.read(reinterpret_cast<char*>(buffer.data()), static_cast<std::streamsize>(buffer.size()));
    const size_t count = static_cast<size_t>(input.gcount());
    if (count != 0) sha.update(buffer.data(), count);
  }
  if (!input.eof()) return {};
  if (ok) *ok = true;
  return hex(sha.finish());
}

}  // namespace jingdu
