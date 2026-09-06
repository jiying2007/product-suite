#include "jingdu/core_api.h"
#include "sha256.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {

constexpr uint32_t kAbiVersion = 2;
constexpr uint64_t kIndexStride = 4096;
constexpr size_t kScanBuffer = 64 * 1024;
constexpr uint64_t kMaxReadChars = 1024 * 1024;

struct SparsePoint {
  uint64_t chars;
  uint64_t bytes;
};

struct Document {
  std::string path;
  uint64_t bytes = 0;
  uint64_t chars = 0;
  std::vector<SparsePoint> index;
};

struct LegacyScore {
  bool valid = false;
  uint32_t units = 0;
  uint32_t common_hits = 0;
};

std::mutex g_mutex;
std::unordered_map<jd_handle, std::shared_ptr<Document>> g_documents;
jd_handle g_next_handle = 1;

constexpr uint16_t kCommonGb18030[] = {
    0x81ED, 0x8283, 0x87F8, 0x8C57, 0x8CA6, 0x9572, 0x95F8, 0x95FE,
    0x9EB3, 0x9EE9, 0x9F6F, 0xAC46, 0xB06C, 0xB1BE, 0xB2BB, 0xB3A4,
    0xB3C9, 0xB3F6, 0xB4CB, 0xB4CE, 0xB4F3, 0xB5BD, 0xB5C0, 0xB5C3,
    0xB5C4, 0xB5D8, 0xB5DA, 0xB6C1, 0xB6D4, 0xB6E0, 0xB6F8, 0xB6FE,
    0xB7A2, 0xB8DF, 0xB8F6, 0xB9FA, 0xB9FD, 0xBAC3, 0xBAF3, 0xBBB9,
    0xBBE1, 0xBCD2, 0xBDF8, 0xBE57, 0xBECD, 0xBFAA, 0xBFB4, 0xC0B4,
    0xC0EF, 0xC1CB, 0xC295, 0xC3C7, 0xC3E6, 0xC3F7, 0xC4DC, 0xC4E3,
    0xC4EA, 0xC55F, 0xC563, 0xC6F0, 0xC7B0, 0xC8A5, 0xC8BB, 0xC8CB,
    0xC8D5, 0xC8FD, 0xC9CF, 0xC9F9, 0xC9FA, 0xCAB1, 0xCAC2, 0xCAC7,
    0xCAD6, 0xCAE9, 0xCBB5, 0xCBFB, 0xCBFD, 0xCCA8, 0xCCE5, 0xCCEC,
    0xCDE5, 0xCDF8, 0xCEAA, 0xCEC4, 0xCED2, 0xCEDE, 0xCFC2, 0xCFD6,
    0xCFEB, 0xD0A1, 0xD0C4, 0xD0D0, 0xD165, 0xD1A7, 0xD2B2, 0xD2BB,
    0xD2D1, 0xD3C3, 0xD3D0, 0xD3DA, 0xD3EB, 0xD4C2, 0xD4DA, 0xD566,
    0xD5C2, 0xD5E2, 0xD6BB, 0xD6D0, 0xD6F8, 0xD778, 0xD7C5, 0xD7D3,
    0xD7EE, 0xDF40, 0xDF4D, 0xDF5E, 0xDF80, 0xE1E1, 0xE94C, 0xE95F,
    0xECB6, 0xF377};

constexpr uint16_t kCommonBig5[] = {
    0xA440, 0xA446, 0xA447, 0xA448, 0xA454, 0xA455, 0xA457, 0xA45D,
    0xA45F, 0xA46A, 0xA46C, 0xA470, 0xA477, 0xA4A3, 0xA4A4, 0xA4D1,
    0xA4DF, 0xA4E2, 0xA4E5, 0xA4E9, 0xA4EB, 0xA54C, 0xA558, 0xA568,
    0xA575, 0xA578, 0xA5BB, 0xA5CD, 0xA5CE, 0xA65A, 0xA661, 0xA662,
    0xA668, 0xA66E, 0xA66F, 0xA67E, 0xA6A8, 0xA6B3, 0xA6B8, 0xA6B9,
    0xA6D3, 0xA6E6, 0xA741, 0xA7DA, 0xA8BD, 0xA8C6, 0xA8D3, 0xA8EC,
    0xA9F3, 0xA9FA, 0xAABA, 0xAAF8, 0xAB65, 0xABE1, 0xAC4F, 0xACB0,
    0xACDD, 0xADB1, 0xADCC, 0xAE61, 0xAEC9, 0xAED1, 0xAFE0, 0xB05F,
    0xB0AA, 0xB0EA, 0xB16F, 0xB27B, 0xB2C4, 0xB36F, 0xB3B9, 0xB3CC,
    0xB44E, 0xB54C, 0xB54D, 0xB56F, 0xB5DB, 0xB669, 0xB67D, 0xB751,
    0xB77C, 0xB8CC, 0xB944, 0xB94C, 0xB9EF, 0xBAF4, 0xBB4F, 0xBB50,
    0xBBA1, 0xBEC7, 0xC16E, 0xC1D9, 0xC5AA, 0xC5E9, 0xC657, 0xC94F,
    0xCA49, 0xCA5E};

bool isContinuation(uint8_t value) {
  return (value & 0xC0U) == 0x80U;
}

size_t expectedUtf8Width(uint8_t first) {
  if (first <= 0x7F) return 1;
  if (first >= 0xC2 && first <= 0xDF) return 2;
  if (first >= 0xE0 && first <= 0xEF) return 3;
  if (first >= 0xF0 && first <= 0xF4) return 4;
  return 0;
}

size_t utf8Width(const uint8_t* data, size_t remaining) {
  if (remaining == 0) return 0;
  const uint8_t first = data[0];
  if (first <= 0x7F) return 1;
  if (first >= 0xC2 && first <= 0xDF) {
    return remaining >= 2 && isContinuation(data[1]) ? 2 : 0;
  }
  if (first == 0xE0) {
    return remaining >= 3 && data[1] >= 0xA0 && data[1] <= 0xBF &&
                   isContinuation(data[2])
               ? 3
               : 0;
  }
  if ((first >= 0xE1 && first <= 0xEC) || (first >= 0xEE && first <= 0xEF)) {
    return remaining >= 3 && isContinuation(data[1]) && isContinuation(data[2]) ? 3 : 0;
  }
  if (first == 0xED) {
    return remaining >= 3 && data[1] >= 0x80 && data[1] <= 0x9F &&
                   isContinuation(data[2])
               ? 3
               : 0;
  }
  if (first == 0xF0) {
    return remaining >= 4 && data[1] >= 0x90 && data[1] <= 0xBF &&
                   isContinuation(data[2]) && isContinuation(data[3])
               ? 4
               : 0;
  }
  if (first >= 0xF1 && first <= 0xF3) {
    return remaining >= 4 && isContinuation(data[1]) && isContinuation(data[2]) &&
                   isContinuation(data[3])
               ? 4
               : 0;
  }
  if (first == 0xF4) {
    return remaining >= 4 && data[1] >= 0x80 && data[1] <= 0x8F &&
                   isContinuation(data[2]) && isContinuation(data[3])
               ? 4
               : 0;
  }
  return 0;
}

bool isValidUtf8Sample(const uint8_t* data, uint64_t size, bool truncated) {
  uint64_t offset = 0;
  while (offset < size) {
    const size_t available = static_cast<size_t>(std::min<uint64_t>(size - offset, 4));
    const size_t width = utf8Width(data + offset, available);
    if (width != 0) {
      offset += width;
      continue;
    }

    if (!truncated) return false;
    const size_t expected = expectedUtf8Width(data[offset]);
    const uint64_t tail = size - offset;
    if (expected <= tail || expected == 0 || expected > 4) return false;
    for (uint64_t i = 1; i < tail; ++i) {
      if (!isContinuation(data[offset + i])) return false;
    }
    return true;
  }
  return true;
}

uint64_t countUtf8CodePoints(const std::string& text) {
  uint64_t count = 0;
  size_t offset = 0;
  while (offset < text.size()) {
    const size_t width = utf8Width(
        reinterpret_cast<const uint8_t*>(text.data() + offset), text.size() - offset);
    if (width == 0) return std::numeric_limits<uint64_t>::max();
    offset += width;
    ++count;
  }
  return count;
}

const char* detectUtf16WithoutBom(const uint8_t* data, uint64_t size) {
  if (size < 8) return nullptr;
  const uint64_t pairs = size / 2;
  uint64_t evenZeros = 0;
  uint64_t oddZeros = 0;
  for (uint64_t i = 0; i + 1 < size; i += 2) {
    if (data[i] == 0) ++evenZeros;
    if (data[i + 1] == 0) ++oddZeros;
  }
  const double evenRatio = static_cast<double>(evenZeros) / static_cast<double>(pairs);
  const double oddRatio = static_cast<double>(oddZeros) / static_cast<double>(pairs);
  if (oddRatio > 0.20 && evenRatio < 0.05) return "UTF-16LE";
  if (evenRatio > 0.20 && oddRatio < 0.05) return "UTF-16BE";
  return nullptr;
}

template <size_t N>
bool containsCommonPair(uint16_t pair, const uint16_t (&table)[N]) {
  return std::find(std::begin(table), std::end(table), pair) != std::end(table);
}

LegacyScore scoreGb18030(const uint8_t* data, uint64_t size, bool truncated) {
  LegacyScore score;
  score.valid = true;
  uint64_t offset = 0;
  while (offset < size) {
    const uint8_t first = data[offset];
    if (first <= 0x7F) {
      ++offset;
      continue;
    }
    if (first < 0x81 || first > 0xFE) {
      score.valid = false;
      return score;
    }
    if (offset + 1 >= size) {
      score.valid = truncated;
      return score;
    }

    const uint8_t second = data[offset + 1];
    if (second >= 0x30 && second <= 0x39) {
      if (offset + 3 >= size) {
        score.valid = truncated;
        return score;
      }
      const uint8_t third = data[offset + 2];
      const uint8_t fourth = data[offset + 3];
      if (third < 0x81 || third > 0xFE || fourth < 0x30 || fourth > 0x39) {
        score.valid = false;
        return score;
      }
      offset += 4;
      ++score.units;
      continue;
    }

    const bool validSecond =
        (second >= 0x40 && second <= 0x7E) || (second >= 0x80 && second <= 0xFE);
    if (!validSecond || second == 0x7F) {
      score.valid = false;
      return score;
    }
    const uint16_t pair = static_cast<uint16_t>((static_cast<uint16_t>(first) << 8U) | second);
    if (containsCommonPair(pair, kCommonGb18030)) ++score.common_hits;
    offset += 2;
    ++score.units;
  }
  return score;
}

LegacyScore scoreBig5(const uint8_t* data, uint64_t size, bool truncated) {
  LegacyScore score;
  score.valid = true;
  uint64_t offset = 0;
  while (offset < size) {
    const uint8_t first = data[offset];
    if (first <= 0x7F) {
      ++offset;
      continue;
    }
    if (first < 0x81 || first > 0xFE) {
      score.valid = false;
      return score;
    }
    if (offset + 1 >= size) {
      score.valid = truncated;
      return score;
    }

    const uint8_t second = data[offset + 1];
    const bool validSecond =
        (second >= 0x40 && second <= 0x7E) || (second >= 0xA1 && second <= 0xFE);
    if (!validSecond) {
      score.valid = false;
      return score;
    }
    const uint16_t pair = static_cast<uint16_t>((static_cast<uint16_t>(first) << 8U) | second);
    if (containsCommonPair(pair, kCommonBig5)) ++score.common_hits;
    offset += 2;
    ++score.units;
  }
  return score;
}

const char* detectLegacyEncoding(const uint8_t* data, uint64_t size, bool truncated) {
  const LegacyScore gb = scoreGb18030(data, size, truncated);
  const LegacyScore big5 = scoreBig5(data, size, truncated);
  if (big5.valid && !gb.valid) return "Big5";
  if (gb.valid && big5.valid && big5.common_hits >= 2) {
    const uint32_t margin = std::max<uint32_t>(2, std::min(gb.units, big5.units) / 12);
    if (big5.common_hits >= gb.common_hits + margin) return "Big5";
  }
  return "GB18030";
}

const char* detectEncoding(const uint8_t* data, uint64_t size, bool truncated) {
  if (size >= 3 && data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF) {
    return "UTF-8";
  }
  if (size >= 2 && data[0] == 0xFF && data[1] == 0xFE) return "UTF-16LE";
  if (size >= 2 && data[0] == 0xFE && data[1] == 0xFF) return "UTF-16BE";
  if (const char* utf16 = detectUtf16WithoutBom(data, size); utf16 != nullptr) return utf16;
  if (isValidUtf8Sample(data, size, truncated)) return "UTF-8";
  return detectLegacyEncoding(data, size, truncated);
}

int copyHash(const std::string& value, char* output, uint64_t capacity) {
  if (output == nullptr || capacity < JD_SHA256_HEX_SIZE || value.size() != 64) {
    return JD_EINVAL;
  }
  std::memcpy(output, value.c_str(), JD_SHA256_HEX_SIZE);
  return JD_OK;
}

int setBuffer(const std::string& text, jd_buffer* output) {
  if (output == nullptr) return JD_EINVAL;
  output->data = nullptr;
  output->size = 0;
  if (text.empty()) return JD_OK;

  char* data = new (std::nothrow) char[text.size() + 1];
  if (data == nullptr) return JD_ENOMEM;
  std::memcpy(data, text.data(), text.size());
  data[text.size()] = '\0';
  output->data = data;
  output->size = static_cast<uint64_t>(text.size());
  return JD_OK;
}

std::shared_ptr<Document> getDocument(jd_handle handle) {
  std::lock_guard<std::mutex> lock(g_mutex);
  const auto iterator = g_documents.find(handle);
  return iterator == g_documents.end() ? nullptr : iterator->second;
}

int buildIndex(const std::string& path, Document* document) {
  if (document == nullptr) return JD_EINVAL;
  std::ifstream input(path, std::ios::binary);
  if (!input) return JD_ENOENT;

  document->index.clear();
  document->index.push_back({0, 0});
  uint64_t byteOffset = 0;
  uint64_t charOffset = 0;
  std::vector<uint8_t> carry;
  std::vector<uint8_t> buffer(kScanBuffer + 4);

  while (input) {
    if (!carry.empty()) std::copy(carry.begin(), carry.end(), buffer.begin());
    input.read(reinterpret_cast<char*>(buffer.data() + carry.size()),
               static_cast<std::streamsize>(kScanBuffer));
    const size_t read = static_cast<size_t>(input.gcount());
    const size_t total = carry.size() + read;
    size_t offset = 0;
    carry.clear();

    while (offset < total) {
      const size_t remaining = total - offset;
      const size_t width = utf8Width(buffer.data() + offset, std::min<size_t>(remaining, 4));
      if (width == 0) {
        const size_t expected = expectedUtf8Width(buffer[offset]);
        if (!input.eof() && expected > remaining && expected <= 4) {
          carry.assign(buffer.begin() + static_cast<std::ptrdiff_t>(offset),
                       buffer.begin() + static_cast<std::ptrdiff_t>(total));
          break;
        }
        return JD_EUTF8;
      }
      if (charOffset != 0 && charOffset % kIndexStride == 0) {
        document->index.push_back({charOffset, byteOffset});
      }
      offset += width;
      byteOffset += width;
      ++charOffset;
    }
  }

  if (!carry.empty()) return JD_EUTF8;
  document->bytes = byteOffset;
  document->chars = charOffset;
  return JD_OK;
}

SparsePoint pointForChar(const Document& document, uint64_t target) {
  auto iterator = std::upper_bound(
      document.index.begin(), document.index.end(), target,
      [](uint64_t value, const SparsePoint& point) { return value < point.chars; });
  if (iterator == document.index.begin()) return document.index.front();
  --iterator;
  return *iterator;
}

SparsePoint pointForByte(const Document& document, uint64_t target) {
  auto iterator = std::upper_bound(
      document.index.begin(), document.index.end(), target,
      [](uint64_t value, const SparsePoint& point) { return value < point.bytes; });
  if (iterator == document.index.begin()) return document.index.front();
  --iterator;
  return *iterator;
}

int readCodePoint(std::ifstream* input, uint64_t* widthOut, std::string* appendTo = nullptr) {
  if (input == nullptr || widthOut == nullptr) return JD_EINVAL;
  uint8_t sequence[4]{};
  input->read(reinterpret_cast<char*>(sequence), 1);
  if (!*input) return input->eof() ? JD_ENOENT : JD_EIO;

  const size_t width = expectedUtf8Width(sequence[0]);
  if (width == 0) return JD_EUTF8;
  if (width > 1) {
    input->read(reinterpret_cast<char*>(sequence + 1), static_cast<std::streamsize>(width - 1));
    if (!*input) return JD_EUTF8;
  }
  if (utf8Width(sequence, width) != width) return JD_EUTF8;
  if (appendTo != nullptr) appendTo->append(reinterpret_cast<const char*>(sequence), width);
  *widthOut = width;
  return JD_OK;
}

int byteOffsetForChar(const Document& document, uint64_t target, uint64_t* output) {
  if (output == nullptr) return JD_EINVAL;
  if (target >= document.chars) {
    *output = document.bytes;
    return JD_OK;
  }

  const SparsePoint point = pointForChar(document, target);
  std::ifstream input(document.path, std::ios::binary);
  if (!input) return JD_EIO;
  input.seekg(static_cast<std::streamoff>(point.bytes));

  uint64_t chars = point.chars;
  uint64_t bytes = point.bytes;
  while (chars < target) {
    uint64_t width = 0;
    const int status = readCodePoint(&input, &width);
    if (status != JD_OK) return status;
    bytes += width;
    ++chars;
  }
  *output = bytes;
  return JD_OK;
}

uint64_t charOffsetForByte(const Document& document, uint64_t target) {
  if (target >= document.bytes) return document.chars;
  const SparsePoint point = pointForByte(document, target);
  std::ifstream input(document.path, std::ios::binary);
  if (!input) return point.chars;
  input.seekg(static_cast<std::streamoff>(point.bytes));

  uint64_t chars = point.chars;
  uint64_t bytes = point.bytes;
  while (bytes < target) {
    uint64_t width = 0;
    const int status = readCodePoint(&input, &width);
    if (status != JD_OK || bytes + width > target) break;
    bytes += width;
    ++chars;
  }
  return chars;
}

int readChars(const Document& document, uint64_t offset, uint64_t maximum,
              std::string* output) {
  if (output == nullptr) return JD_EINVAL;
  output->clear();
  if (offset >= document.chars || maximum == 0) return JD_OK;

  maximum = std::min(maximum, kMaxReadChars);
  uint64_t byteOffset = 0;
  const int offsetStatus = byteOffsetForChar(document, offset, &byteOffset);
  if (offsetStatus != JD_OK) return offsetStatus;

  std::ifstream input(document.path, std::ios::binary);
  if (!input) return JD_EIO;
  input.seekg(static_cast<std::streamoff>(byteOffset));
  output->reserve(static_cast<size_t>(std::min<uint64_t>(maximum * 3, 4ULL * 1024ULL * 1024ULL)));

  for (uint64_t count = 0; count < maximum; ++count) {
    if (input.peek() == std::ifstream::traits_type::eof()) break;
    uint64_t width = 0;
    const int status = readCodePoint(&input, &width, output);
    if (status != JD_OK) return status;
  }
  return JD_OK;
}

std::string trimAscii(std::string value) {
  const auto whitespace = [](unsigned char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  };
  while (!value.empty() && whitespace(static_cast<unsigned char>(value.front()))) {
    value.erase(value.begin());
  }
  while (!value.empty() && whitespace(static_cast<unsigned char>(value.back()))) {
    value.pop_back();
  }
  return value;
}

std::string lowerAscii(std::string value) {
  for (char& character : value) {
    character = static_cast<char>(std::tolower(static_cast<unsigned char>(character)));
  }
  return value;
}

bool looksLikeChapter(const std::string& raw) {
  const std::string value = trimAscii(raw);
  if (value.empty() || value.size() > 240) return false;
  const std::string lower = lowerAscii(value);
  if (lower.rfind("chapter ", 0) == 0 || lower.rfind("chapter\t", 0) == 0) return true;
  if (value.rfind("第", 0) != 0) return false;
  return value.find("章") != std::string::npos || value.find("回") != std::string::npos ||
         value.find("节") != std::string::npos || value.find("卷") != std::string::npos;
}

std::string contextFor(const Document& document, uint64_t hit) {
  std::string value;
  if (readChars(document, hit > 30 ? hit - 30 : 0, 100, &value) != JD_OK) return {};
  for (char& character : value) {
    if (character == '\n' || character == '\r' || character == '\t') character = ' ';
  }
  return value;
}

std::vector<std::pair<std::string, std::string>> parseRules(const char* packed) {
  std::vector<std::pair<std::string, std::string>> rules;
  if (packed == nullptr) return rules;
  const std::string all(packed);
  size_t start = 0;
  while (start <= all.size()) {
    size_t end = all.find('\x1e', start);
    if (end == std::string::npos) end = all.size();
    const std::string record = all.substr(start, end - start);
    const size_t separator = record.find('\x1f');
    if (separator != std::string::npos && separator > 0) {
      rules.emplace_back(record.substr(0, separator), record.substr(separator + 1));
    }
    if (end == all.size()) break;
    start = end + 1;
  }
  return rules;
}

void replaceAll(std::string* text, const std::string& from, const std::string& to) {
  if (text == nullptr || from.empty()) return;
  size_t position = 0;
  while ((position = text->find(from, position)) != std::string::npos) {
    text->replace(position, from.size(), to);
    position += to.size();
  }
}

size_t sentenceBoundaryWidth(const std::string& text, size_t offset) {
  const unsigned char character = static_cast<unsigned char>(text[offset]);
  if (character == '.' || character == '!' || character == '?' || character == '\n') return 1;
  constexpr const char* marks[] = {"。", "！", "？", "；"};
  for (const char* mark : marks) {
    const size_t size = std::strlen(mark);
    if (offset + size <= text.size() && text.compare(offset, size, mark) == 0) return size;
  }
  return 0;
}

}  // namespace

uint32_t jd_abi_version(void) {
  return kAbiVersion;
}

const char* jd_core_version(void) {
  return "2.0.0";
}

int jd_detect_encoding(const uint8_t* data, uint64_t size, uint32_t flags, char* out_name,
                       uint64_t out_capacity) {
  if ((data == nullptr && size != 0) || out_name == nullptr || out_capacity == 0) {
    return JD_EINVAL;
  }
  const char* encoding =
      detectEncoding(data, size, (flags & JD_DETECT_SAMPLE_TRUNCATED) != 0);
  const size_t required = std::strlen(encoding) + 1;
  if (required > out_capacity) return JD_EINVAL;
  std::memcpy(out_name, encoding, required);
  return JD_OK;
}

int jd_sha256(const uint8_t* data, uint64_t size, char* out_hex, uint64_t out_capacity) {
  if (data == nullptr && size != 0) return JD_EINVAL;
  return copyHash(jingdu::sha256_hex(data, static_cast<size_t>(size)), out_hex, out_capacity);
}

int jd_file_sha256(const char* path, char* out_hex, uint64_t out_capacity) {
  if (path == nullptr || *path == '\0') return JD_EINVAL;
  bool success = false;
  const std::string hash = jingdu::sha256_file_hex(path, &success);
  return success ? copyHash(hash, out_hex, out_capacity) : JD_EIO;
}

int jd_repair_revision(const char* normalized_sha256, const char* packed_rules, char* out_hex,
                       uint64_t out_capacity) {
  if (normalized_sha256 == nullptr || std::strlen(normalized_sha256) != 64) return JD_EINVAL;
  const std::string material = std::string("jingdu-repair-v1\n") + normalized_sha256 + "\n" +
                               (packed_rules == nullptr ? "" : packed_rules);
  return copyHash(jingdu::sha256_hex(reinterpret_cast<const uint8_t*>(material.data()),
                                     material.size()),
                  out_hex, out_capacity);
}

int jd_open_utf8(const char* path, jd_handle* out_handle) {
  if (path == nullptr || *path == '\0' || out_handle == nullptr) return JD_EINVAL;
  auto document = std::make_shared<Document>();
  document->path = path;
  const int status = buildIndex(path, document.get());
  if (status != JD_OK) return status;

  std::lock_guard<std::mutex> lock(g_mutex);
  jd_handle handle = g_next_handle++;
  if (handle == 0) handle = g_next_handle++;
  g_documents.emplace(handle, std::move(document));
  *out_handle = handle;
  return JD_OK;
}

void jd_close(jd_handle handle) {
  std::lock_guard<std::mutex> lock(g_mutex);
  g_documents.erase(handle);
}

uint64_t jd_char_count(jd_handle handle) {
  const auto document = getDocument(handle);
  return document ? document->chars : 0;
}

uint64_t jd_byte_count(jd_handle handle) {
  const auto document = getDocument(handle);
  return document ? document->bytes : 0;
}

int jd_read(jd_handle handle, uint64_t char_offset, uint64_t max_chars, jd_buffer* out) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  std::string text;
  const int status = readChars(*document, char_offset, max_chars, &text);
  return status == JD_OK ? setBuffer(text, out) : status;
}

int jd_search(jd_handle handle, const char* utf8_query, uint32_t limit, jd_buffer* out) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  if (utf8_query == nullptr || *utf8_query == '\0' || out == nullptr) return JD_EINVAL;
  const std::string query(utf8_query);
  if (countUtf8CodePoints(query) == std::numeric_limits<uint64_t>::max()) return JD_EUTF8;
  if (limit == 0) limit = 100;
  limit = std::min<uint32_t>(limit, 10000);

  std::ifstream input(document->path, std::ios::binary);
  if (!input) return JD_EIO;
  const size_t overlapSize = query.size() > 1 ? query.size() - 1 : 0;
  std::string carry;
  std::vector<char> buffer(kScanBuffer);
  uint64_t base = 0;
  uint64_t last = std::numeric_limits<uint64_t>::max();
  uint32_t hits = 0;
  std::ostringstream result;

  while (input && hits < limit) {
    input.read(buffer.data(), static_cast<std::streamsize>(buffer.size()));
    const size_t read = static_cast<size_t>(input.gcount());
    if (read == 0) break;
    std::string chunk = carry;
    chunk.append(buffer.data(), read);
    const uint64_t chunkBase = base - static_cast<uint64_t>(carry.size());

    size_t position = 0;
    while (hits < limit && (position = chunk.find(query, position)) != std::string::npos) {
      const uint64_t absoluteByte = chunkBase + position;
      if (absoluteByte != last) {
        const uint64_t charOffset = charOffsetForByte(*document, absoluteByte);
        result << charOffset << '\t' << contextFor(*document, charOffset) << '\n';
        last = absoluteByte;
        ++hits;
      }
      position += std::max<size_t>(1, query.size());
    }

    carry = overlapSize == 0
                ? std::string()
                : chunk.substr(chunk.size() > overlapSize ? chunk.size() - overlapSize : 0);
    base += read;
  }
  return setBuffer(result.str(), out);
}

int jd_chapters(jd_handle handle, uint32_t limit, jd_buffer* out) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  if (out == nullptr) return JD_EINVAL;
  if (limit == 0) limit = 20000;
  limit = std::min<uint32_t>(limit, 20000);

  std::ifstream input(document->path, std::ios::binary);
  if (!input) return JD_EIO;
  std::string line;
  uint64_t charOffset = 0;
  uint32_t count = 0;
  std::ostringstream result;
  while (count < limit && std::getline(input, line)) {
    if (looksLikeChapter(line)) {
      std::string title = trimAscii(line);
      for (char& character : title) {
        if (character == '\t') character = ' ';
      }
      result << charOffset << '\t' << title << '\n';
      ++count;
    }
    const uint64_t lineChars = countUtf8CodePoints(line);
    if (lineChars == std::numeric_limits<uint64_t>::max()) return JD_EUTF8;
    charOffset += lineChars;
    if (!input.eof()) ++charOffset;
  }
  return setBuffer(result.str(), out);
}

int jd_speech_chunk(jd_handle handle, uint64_t char_offset, uint64_t max_chars,
                    jd_buffer* out) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  if (out == nullptr || max_chars == 0) return JD_EINVAL;
  max_chars = std::min<uint64_t>(max_chars, 4000);

  std::string text;
  const int status = readChars(*document, char_offset, max_chars, &text);
  if (status != JD_OK) return status;
  if (text.empty()) return setBuffer("", out);

  const size_t minimum = text.size() / 3;
  size_t cut = text.size();
  for (size_t offset = minimum; offset < text.size(); ++offset) {
    const size_t boundary = sentenceBoundaryWidth(text, offset);
    if (boundary != 0) {
      cut = offset + boundary;
      break;
    }
  }
  text.resize(cut);
  const uint64_t chars = countUtf8CodePoints(text);
  if (chars == std::numeric_limits<uint64_t>::max()) return JD_EUTF8;

  std::ostringstream result;
  result << (char_offset + chars) << '\t' << text;
  return setBuffer(result.str(), out);
}

int jd_export_rules(jd_handle handle, const char* packed_rules, const char* output_path) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  if (output_path == nullptr || *output_path == '\0') return JD_EINVAL;

  const auto rules = parseRules(packed_rules);
  std::ifstream input(document->path, std::ios::binary);
  if (!input) return JD_EIO;
  const std::string temporary = std::string(output_path) + ".tmp";
  std::ofstream output(temporary, std::ios::binary | std::ios::trunc);
  if (!output) return JD_EIO;

  std::string line;
  while (std::getline(input, line)) {
    for (const auto& rule : rules) replaceAll(&line, rule.first, rule.second);
    output.write(line.data(), static_cast<std::streamsize>(line.size()));
    if (!input.eof()) output.put('\n');
    if (!output) {
      output.close();
      std::remove(temporary.c_str());
      return JD_EIO;
    }
  }
  output.flush();
  if (!output) {
    output.close();
    std::remove(temporary.c_str());
    return JD_EIO;
  }
  output.close();
  if (std::rename(temporary.c_str(), output_path) != 0) {
    std::remove(temporary.c_str());
    return JD_EIO;
  }
  return JD_OK;
}

void jd_buffer_free(jd_buffer* buffer) {
  if (buffer == nullptr) return;
  delete[] buffer->data;
  buffer->data = nullptr;
  buffer->size = 0;
}
