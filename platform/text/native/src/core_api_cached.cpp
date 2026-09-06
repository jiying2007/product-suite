#include "jingdu/core_api.h"
#include "index_cache.h"

#define jd_core_version jd_core_version_legacy_internal
#define jd_open_utf8 jd_open_utf8_uncached_internal
#define jd_chapters jd_chapters_uncached_internal
#define jd_export_rules jd_export_rules_legacy_internal
// Intentional translation-unit composition: this wrapper replaces selected public entry points
// while keeping the ABI v2 implementation private to one compiled translation unit.
// NOLINTNEXTLINE(bugprone-suspicious-include)
#include "core_api.cpp"
#undef jd_export_rules
#undef jd_chapters
#undef jd_open_utf8
#undef jd_core_version

namespace {
constexpr size_t kNoiseSketchWidth = 32768;
constexpr size_t kNoiseCandidateLimit = 2048;
constexpr size_t kNoiseMaxLineBytes = 512;
constexpr size_t kMaxPackedRuleFieldBytes = 2048;

struct NoiseCandidate {
  std::string text;
  uint32_t count = 0;
  uint32_t score = 0;
  std::string reason;
};

struct ExtendedRule {
  bool line_glob = false;
  std::string find;
  std::string replacement;
};

uint64_t noiseHash(const std::string& value) {
  uint64_t hash = 1469598103934665603ULL;
  for (unsigned char byte : value) {
    hash ^= byte;
    hash *= 1099511628211ULL;
  }
  return hash;
}

uint64_t mixNoiseHash(uint64_t value) {
  value ^= value >> 30U;
  value *= 0xbf58476d1ce4e5b9ULL;
  value ^= value >> 27U;
  value *= 0x94d049bb133111ebULL;
  return value ^ (value >> 31U);
}

bool hasUsefulNoiseText(const std::string& value) {
  if (value.size() < 6 || value.size() > kNoiseMaxLineBytes || looksLikeChapter(value)) return false;
  size_t useful = 0;
  for (unsigned char byte : value) {
    if (byte >= 0x80 || std::isalnum(byte) != 0) ++useful;
    if (useful >= 4) return true;
  }
  return false;
}

uint32_t promotionalStrength(const std::string& value, std::string* reason) {
  const std::string lower = lowerAscii(value);
  const bool url = lower.find("http://") != std::string::npos ||
                   lower.find("https://") != std::string::npos ||
                   lower.find("www.") != std::string::npos ||
                   lower.find(".com") != std::string::npos ||
                   lower.find(".net") != std::string::npos ||
                   lower.find(".cn") != std::string::npos ||
                   lower.find(".tw") != std::string::npos ||
                   lower.find(".hk") != std::string::npos;
  if (url) {
    if (reason != nullptr) *reason = "url";
    return 82;
  }

  // Content-language detection belongs in the document pipeline, not the UI locale. Keep
  // Simplified and Traditional markers together so the same Core behaves identically on all shells.
  constexpr const char* strong[] = {
      "最新网址", "备用网址", "请收藏本站", "请记住本站", "手机用户请访问",
      "关注公众号", "微信公众号", "下载app", "下载APP", "加入书签",
      "求收藏", "求推荐票", "感谢投票", "最快更新", "无弹窗",
      "本书来自", "更多精彩", "搜索书名", "请牢记域名",
      "最新網址", "備用網址", "請收藏本站", "請記住本站", "手機用戶請訪問",
      "關注公眾號", "微信公眾號", "下載app", "下載APP", "加入書籤",
      "求收藏", "求推薦票", "感謝投票", "最快更新", "無彈窗",
      "本書來自", "更多精彩", "搜尋書名", "請牢記域名", "請牢記網域"};
  for (const char* marker : strong) {
    if (value.find(marker) != std::string::npos) {
      if (reason != nullptr) *reason = "promo";
      return 88;
    }
  }

  constexpr const char* weak[] = {"subscribe", "follow us", "download app", "official site"};
  for (const char* marker : weak) {
    if (lower.find(marker) != std::string::npos) {
      if (reason != nullptr) *reason = "promo";
      return 72;
    }
  }
  return 0;
}

uint32_t repeatedStrength(uint32_t count) {
  if (count >= 200) return 86;
  if (count >= 50) return 78;
  if (count >= 10) return 68;
  if (count >= 5) return 60;
  if (count >= 3) return 52;
  return 0;
}

template <typename Callback>
bool forEachBoundedLine(const std::string& path, Callback callback) {
  std::ifstream input(path, std::ios::binary);
  if (!input) return false;
  std::vector<char> buffer(kScanBuffer);
  std::string line;
  line.reserve(160);
  bool tooLong = false;

  auto finishLine = [&]() {
    if (!tooLong) {
      if (!line.empty() && line.back() == '\r') line.pop_back();
      const std::string trimmed = trimAscii(line);
      if (!trimmed.empty()) callback(trimmed);
    }
    line.clear();
    tooLong = false;
  };

  while (input) {
    input.read(buffer.data(), static_cast<std::streamsize>(buffer.size()));
    const size_t read = static_cast<size_t>(input.gcount());
    for (size_t index = 0; index < read; ++index) {
      const char character = buffer[index];
      if (character == '\n') {
        finishLine();
      } else if (!tooLong) {
        if (line.size() < kNoiseMaxLineBytes) {
          line.push_back(character);
        } else {
          line.clear();
          tooLong = true;
        }
      }
    }
  }
  if (!line.empty() || tooLong) finishLine();
  return input.eof() || input.good();
}

std::string safeNoiseField(std::string value) {
  for (char& character : value) {
    if (character == '\t' || character == '\r' || character == '\n') character = ' ';
  }
  return value;
}

std::vector<ExtendedRule> parseExtendedRules(const char* packed) {
  std::vector<ExtendedRule> rules;
  if (packed == nullptr) return rules;
  const std::string all(packed);
  size_t start = 0;
  while (start <= all.size()) {
    size_t end = all.find('\x1e', start);
    if (end == std::string::npos) end = all.size();
    const std::string record = all.substr(start, end - start);
    if (!record.empty()) {
      const size_t first = record.find('\x1f');
      if (first != std::string::npos) {
        if (record.compare(0, first, "@g") == 0) {
          const size_t second = record.find('\x1f', first + 1);
          if (second != std::string::npos && second > first + 1) {
            const std::string pattern = record.substr(first + 1, second - first - 1);
            const std::string replacement = record.substr(second + 1);
            if (pattern.size() <= kMaxPackedRuleFieldBytes &&
                replacement.size() <= kMaxPackedRuleFieldBytes) {
              rules.push_back({true, pattern, replacement});
            }
          }
        } else if (first > 0) {
          const std::string find = record.substr(0, first);
          const std::string replacement = record.substr(first + 1);
          if (find.size() <= kMaxPackedRuleFieldBytes &&
              replacement.size() <= kMaxPackedRuleFieldBytes) {
            rules.push_back({false, find, replacement});
          }
        }
      }
    }
    if (end == all.size()) break;
    start = end + 1;
  }
  return rules;
}

bool lineGlobMatches(const std::string& text, const std::string& pattern) {
  size_t textIndex = 0;
  size_t patternIndex = 0;
  size_t starIndex = std::string::npos;
  size_t retryText = 0;
  while (textIndex < text.size()) {
    if (patternIndex < pattern.size() && pattern[patternIndex] == '*') {
      starIndex = patternIndex++;
      retryText = textIndex;
    } else if (patternIndex < pattern.size() && pattern[patternIndex] == text[textIndex]) {
      ++patternIndex;
      ++textIndex;
    } else if (starIndex != std::string::npos) {
      patternIndex = starIndex + 1;
      textIndex = ++retryText;
    } else {
      return false;
    }
  }
  while (patternIndex < pattern.size() && pattern[patternIndex] == '*') ++patternIndex;
  return patternIndex == pattern.size();
}

std::vector<jingdu::IndexPoint> indexPoints(const Document& document) {
  std::vector<jingdu::IndexPoint> points;
  points.reserve(document.index.size());
  for (const SparsePoint& point : document.index) points.emplace_back(point.chars, point.bytes);
  return points;
}

std::vector<jingdu::ChapterPoint> parseChapters(const std::string& packed) {
  std::vector<jingdu::ChapterPoint> chapters;
  std::istringstream input(packed);
  std::string line;
  uint64_t previous = 0;
  while (chapters.size() < 20000 && std::getline(input, line)) {
    const size_t tab = line.find('\t');
    if (tab == std::string::npos || tab == 0 || tab + 1 >= line.size()) continue;
    uint64_t offset = 0;
    char extra = '\0';
    std::istringstream offset_input(line.substr(0, tab));
    if (!(offset_input >> offset) || (offset_input >> extra)) continue;
    if (!chapters.empty() && offset <= previous) continue;
    std::string title = line.substr(tab + 1);
    if (!title.empty() && title.back() == '\r') title.pop_back();
    if (title.empty()) continue;
    chapters.push_back({offset, std::move(title)});
    previous = offset;
  }
  return chapters;
}

std::string packChapters(const std::vector<jingdu::ChapterPoint>& chapters, uint32_t limit) {
  if (limit == 0) limit = 20000;
  limit = std::min<uint32_t>(limit, 20000);
  std::ostringstream output;
  const size_t count = std::min<size_t>(chapters.size(), limit);
  for (size_t index = 0; index < count; ++index) {
    output << chapters[index].offset << '\t' << chapters[index].title << '\n';
  }
  return output.str();
}
}  // namespace

extern "C" const char* jd_core_version(void) {
  return "2.2.0";
}

extern "C" jd_status jd_open_utf8(const char* path, jd_handle* out_handle) {
  if (path == nullptr || *path == '\0' || out_handle == nullptr) return JD_EINVAL;

  uint64_t bytes = 0;
  uint64_t chars = 0;
  std::vector<jingdu::IndexPoint> points;
  if (jingdu::load_index_cache(path, kIndexStride, &bytes, &chars, &points)) {
    auto document = std::make_shared<Document>();
    document->path = path;
    document->bytes = bytes;
    document->chars = chars;
    document->index.reserve(points.size());
    for (const auto& point : points) {
      document->index.push_back({point.first, point.second});
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    jd_handle handle = g_next_handle++;
    if (handle == 0) handle = g_next_handle++;
    g_documents.emplace(handle, std::move(document));
    *out_handle = handle;
    return JD_OK;
  }

  const jd_status status = jd_open_utf8_uncached_internal(path, out_handle);
  if (status != JD_OK) return status;

  const auto document = getDocument(*out_handle);
  if (document != nullptr) {
    jingdu::save_index_cache(path, kIndexStride, document->bytes, document->chars,
                             indexPoints(*document));
  }
  return JD_OK;
}

extern "C" jd_status jd_chapters(jd_handle handle, uint32_t limit, jd_buffer* out) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  if (out == nullptr) return JD_EINVAL;

  std::vector<jingdu::ChapterPoint> chapters;
  if (jingdu::load_chapter_cache(document->path, kIndexStride, &chapters)) {
    return setBuffer(packChapters(chapters, limit), out);
  }

  // JDX1 intentionally remains index-only. The first real chapter request runs the authoritative
  // Core scan once, then atomically upgrades that same cache to JDX2. Subsequent opens/processes
  // enumerate the persisted table and never rescan the TXT for chapter headings.
  jd_buffer scanned{};
  const jd_status status = jd_chapters_uncached_internal(handle, 20000, &scanned);
  if (status != JD_OK) {
    jd_buffer_free(&scanned);
    return status;
  }
  const std::string packed(scanned.data == nullptr ? "" : scanned.data,
                           static_cast<size_t>(scanned.size));
  jd_buffer_free(&scanned);
  chapters = parseChapters(packed);
  jingdu::save_index_cache_with_chapters(document->path, kIndexStride, document->bytes,
                                         document->chars, indexPoints(*document), chapters);
  return setBuffer(packChapters(chapters, limit), out);
}

extern "C" jd_status jd_noise_candidates(jd_handle handle, uint32_t limit, jd_buffer* out) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  if (out == nullptr) return JD_EINVAL;
  if (limit == 0) limit = 50;
  limit = std::min<uint32_t>(limit, 200);

  std::array<std::vector<uint16_t>, 4> sketch;
  for (auto& row : sketch) row.assign(kNoiseSketchWidth, 0);
  constexpr uint64_t seeds[] = {
      0x9e3779b97f4a7c15ULL, 0x243f6a8885a308d3ULL,
      0xb7e151628aed2a6bULL, 0x94d049bb133111ebULL};
  std::unordered_map<uint64_t, NoiseCandidate> candidates;

  const bool firstPass = forEachBoundedLine(document->path, [&](const std::string& text) {
    if (!hasUsefulNoiseText(text)) return;
    const uint64_t hash = noiseHash(text);
    uint16_t estimate = std::numeric_limits<uint16_t>::max();
    for (size_t row = 0; row < sketch.size(); ++row) {
      const size_t slot = static_cast<size_t>(mixNoiseHash(hash ^ seeds[row]) & (kNoiseSketchWidth - 1));
      uint16_t& counter = sketch[row][slot];
      if (counter != std::numeric_limits<uint16_t>::max()) ++counter;
      estimate = std::min(estimate, counter);
    }

    std::string reason;
    const uint32_t strong = promotionalStrength(text, &reason);
    if ((strong != 0 || estimate >= 3) && candidates.size() < kNoiseCandidateLimit) {
      auto [iterator, inserted] = candidates.emplace(hash, NoiseCandidate{text, 0, strong, reason});
      if (!inserted && iterator->second.text == text && strong > iterator->second.score) {
        iterator->second.score = strong;
        iterator->second.reason = reason;
      }
    }
  });
  if (!firstPass) return JD_EIO;
  if (candidates.empty()) return setBuffer("", out);

  for (auto& item : candidates) item.second.count = 0;
  const bool secondPass = forEachBoundedLine(document->path, [&](const std::string& text) {
    const uint64_t hash = noiseHash(text);
    auto iterator = candidates.find(hash);
    if (iterator != candidates.end() && iterator->second.text == text &&
        iterator->second.count != std::numeric_limits<uint32_t>::max()) {
      ++iterator->second.count;
    }
  });
  if (!secondPass) return JD_EIO;

  std::vector<NoiseCandidate> ranked;
  ranked.reserve(candidates.size());
  for (auto& item : candidates) {
    NoiseCandidate candidate = std::move(item.second);
    const uint32_t repeated = repeatedStrength(candidate.count);
    if (candidate.score == 0 && repeated == 0) continue;
    if (candidate.score != 0 && repeated != 0) {
      candidate.score = std::min<uint32_t>(100, std::max(candidate.score, repeated) + 6);
      candidate.reason = "promo_repeated";
    } else if (repeated != 0) {
      candidate.score = repeated;
      candidate.reason = "repeated";
    }
    ranked.push_back(std::move(candidate));
  }

  std::sort(ranked.begin(), ranked.end(), [](const NoiseCandidate& left, const NoiseCandidate& right) {
    if (left.score != right.score) return left.score > right.score;
    if (left.count != right.count) return left.count > right.count;
    return left.text < right.text;
  });
  if (ranked.size() > limit) ranked.resize(limit);

  std::ostringstream result;
  for (const auto& candidate : ranked) {
    result << candidate.score << '\t' << candidate.count << '\t'
           << safeNoiseField(candidate.reason) << '\t'
           << safeNoiseField(candidate.text) << '\n';
  }
  return setBuffer(result.str(), out);
}

extern "C" jd_status jd_export_rules(jd_handle handle, const char* packed_rules,
                                      const char* output_path) {
  const auto document = getDocument(handle);
  if (!document) return JD_EHANDLE;
  if (output_path == nullptr || *output_path == '\0') return JD_EINVAL;

  const auto rules = parseExtendedRules(packed_rules);
  std::ifstream input(document->path, std::ios::binary);
  if (!input) return JD_EIO;
  const std::string temporary = std::string(output_path) + ".tmp";
  std::ofstream output(temporary, std::ios::binary | std::ios::trunc);
  if (!output) return JD_EIO;

  std::string line;
  while (std::getline(input, line)) {
    for (const auto& rule : rules) {
      if (rule.line_glob) {
        if (lineGlobMatches(trimAscii(line), rule.find)) line = rule.replacement;
      } else {
        replaceAll(&line, rule.find, rule.replacement);
      }
    }
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
