#include "index_cache.h"

#include <algorithm>
#include <cstdio>
#include <fstream>
#include <limits>
#include <mutex>
#include <string>
#include <sys/stat.h>

namespace jingdu {
namespace {

constexpr const char* kMagicV1 = "JDX1";
constexpr const char* kMagicV2 = "JDX2";
constexpr size_t kMaxChapterTitleBytes = 512;
std::mutex g_cache_mutex;

struct FileIdentity {
  uint64_t bytes = 0;
  int64_t modified_seconds = 0;
};

struct CacheHeader {
  bool chapters_ready = false;
  uint64_t bytes = 0;
  int64_t modified_seconds = 0;
  uint64_t stride = 0;
  uint64_t chars = 0;
  uint64_t point_count = 0;
  uint64_t chapter_count = 0;
};

bool file_identity(const std::string& path, FileIdentity* identity) {
  if (identity == nullptr) return false;
  struct stat value {};
  if (stat(path.c_str(), &value) != 0 || value.st_size < 0) return false;
  identity->bytes = static_cast<uint64_t>(value.st_size);
  identity->modified_seconds = static_cast<int64_t>(value.st_mtime);
  return true;
}

bool sane_count(uint64_t file_bytes, uint64_t stride, uint64_t count) {
  if (stride == 0 || count == 0) return false;
  const uint64_t theoretical = file_bytes / stride + 2;
  return count <= std::max<uint64_t>(theoretical, 2);
}

bool read_header(std::ifstream* input, const FileIdentity& current, uint64_t stride,
                 CacheHeader* header) {
  if (input == nullptr || header == nullptr) return false;
  std::string magic;
  std::getline(*input, magic);
  if (magic != kMagicV1 && magic != kMagicV2) return false;

  CacheHeader value;
  value.chapters_ready = magic == kMagicV2;
  if (value.chapters_ready) {
    if (!(*input >> value.bytes >> value.modified_seconds >> value.stride >> value.chars >>
          value.point_count >> value.chapter_count)) {
      return false;
    }
    if (value.chapter_count > 20000) return false;
  } else if (!(*input >> value.bytes >> value.modified_seconds >> value.stride >> value.chars >>
                value.point_count)) {
    return false;
  }

  if (value.bytes != current.bytes || value.modified_seconds != current.modified_seconds ||
      value.stride != stride || !sane_count(value.bytes, value.stride, value.point_count)) {
    return false;
  }
  *header = value;
  return true;
}

bool read_points(std::ifstream* input, const CacheHeader& header,
                 std::vector<IndexPoint>* points) {
  if (input == nullptr || points == nullptr) return false;
  std::vector<IndexPoint> loaded;
  loaded.reserve(static_cast<size_t>(header.point_count));
  uint64_t previous_chars = 0;
  uint64_t previous_bytes = 0;
  for (uint64_t index = 0; index < header.point_count; ++index) {
    uint64_t char_offset = 0;
    uint64_t byte_offset = 0;
    if (!(*input >> char_offset >> byte_offset)) return false;
    if (index == 0) {
      if (char_offset != 0 || byte_offset != 0) return false;
    } else if (char_offset <= previous_chars || byte_offset <= previous_bytes) {
      return false;
    }
    if (char_offset > header.chars || byte_offset > header.bytes) return false;
    loaded.emplace_back(char_offset, byte_offset);
    previous_chars = char_offset;
    previous_bytes = byte_offset;
  }
  *points = std::move(loaded);
  return true;
}

std::string safe_title(std::string value) {
  for (char& character : value) {
    if (character == '\t' || character == '\r' || character == '\n') character = ' ';
  }
  if (value.size() > kMaxChapterTitleBytes) value.resize(kMaxChapterTitleBytes);
  return value;
}

void save_impl(const std::string& document_path, uint64_t stride, uint64_t bytes,
               uint64_t chars, const std::vector<IndexPoint>& points,
               const std::vector<ChapterPoint>* chapters) {
  if (document_path.empty() || stride == 0 || points.empty()) return;

  FileIdentity current;
  if (!file_identity(document_path, &current) || current.bytes != bytes) return;

  std::lock_guard<std::mutex> lock(g_cache_mutex);
  const std::string target = index_cache_path(document_path);
  const std::string temporary = target + ".tmp";
  {
    std::ofstream output(temporary, std::ios::trunc);
    if (!output) return;
    if (chapters == nullptr) {
      output << kMagicV1 << '\n';
      output << bytes << ' ' << current.modified_seconds << ' ' << stride << ' ' << chars << ' '
             << points.size() << '\n';
    } else {
      output << kMagicV2 << '\n';
      output << bytes << ' ' << current.modified_seconds << ' ' << stride << ' ' << chars << ' '
             << points.size() << ' ' << chapters->size() << '\n';
    }
    for (const auto& point : points) output << point.first << ' ' << point.second << '\n';
    if (chapters != nullptr) {
      for (const auto& chapter : *chapters) {
        output << chapter.offset << '\t' << safe_title(chapter.title) << '\n';
      }
    }
    output.flush();
    if (!output) {
      output.close();
      std::remove(temporary.c_str());
      return;
    }
  }

  if (std::rename(temporary.c_str(), target.c_str()) != 0) {
    std::remove(temporary.c_str());
  }
}

}  // namespace

std::string index_cache_path(const std::string& document_path) {
  return document_path + ".jdx";
}

bool load_index_cache(const std::string& document_path, uint64_t stride, uint64_t* bytes,
                      uint64_t* chars, std::vector<IndexPoint>* points) {
  if (bytes == nullptr || chars == nullptr || points == nullptr || stride == 0) return false;

  std::lock_guard<std::mutex> lock(g_cache_mutex);
  FileIdentity current;
  if (!file_identity(document_path, &current)) return false;
  std::ifstream input(index_cache_path(document_path));
  if (!input) return false;

  CacheHeader header;
  if (!read_header(&input, current, stride, &header)) return false;
  std::vector<IndexPoint> loaded;
  if (!read_points(&input, header, &loaded)) return false;

  *bytes = header.bytes;
  *chars = header.chars;
  *points = std::move(loaded);
  return true;
}

bool load_chapter_cache(const std::string& document_path, uint64_t stride,
                        std::vector<ChapterPoint>* chapters) {
  if (chapters == nullptr || stride == 0) return false;

  std::lock_guard<std::mutex> lock(g_cache_mutex);
  FileIdentity current;
  if (!file_identity(document_path, &current)) return false;
  std::ifstream input(index_cache_path(document_path));
  if (!input) return false;

  CacheHeader header;
  if (!read_header(&input, current, stride, &header) || !header.chapters_ready) return false;
  std::vector<IndexPoint> ignored;
  if (!read_points(&input, header, &ignored)) return false;

  std::vector<ChapterPoint> loaded;
  loaded.reserve(static_cast<size_t>(header.chapter_count));
  uint64_t previous_offset = 0;
  for (uint64_t index = 0; index < header.chapter_count; ++index) {
    uint64_t offset = 0;
    if (!(input >> offset)) return false;
    const int separator = input.get();
    if (separator != '\t') return false;
    std::string title;
    std::getline(input, title);
    if (!title.empty() && title.back() == '\r') title.pop_back();
    if (title.empty() || title.size() > kMaxChapterTitleBytes || offset > header.chars) return false;
    if (index != 0 && offset <= previous_offset) return false;
    loaded.push_back({offset, std::move(title)});
    previous_offset = offset;
  }
  *chapters = std::move(loaded);
  return true;
}

void save_index_cache(const std::string& document_path, uint64_t stride, uint64_t bytes,
                      uint64_t chars, const std::vector<IndexPoint>& points) {
  save_impl(document_path, stride, bytes, chars, points, nullptr);
}

void save_index_cache_with_chapters(const std::string& document_path, uint64_t stride,
                                    uint64_t bytes, uint64_t chars,
                                    const std::vector<IndexPoint>& points,
                                    const std::vector<ChapterPoint>& chapters) {
  save_impl(document_path, stride, bytes, chars, points, &chapters);
}

}  // namespace jingdu
