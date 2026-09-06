#ifndef JINGDU_INDEX_CACHE_H
#define JINGDU_INDEX_CACHE_H

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

namespace jingdu {

using IndexPoint = std::pair<uint64_t, uint64_t>;

struct ChapterPoint {
  uint64_t offset = 0;
  std::string title;
};

bool load_index_cache(const std::string& document_path, uint64_t stride, uint64_t* bytes,
                      uint64_t* chars, std::vector<IndexPoint>* points);

// JDX2 augments the sparse character index with a persisted chapter table. JDX1 remains readable
// as an index-only cache and is upgraded lazily after the first chapter scan.
bool load_chapter_cache(const std::string& document_path, uint64_t stride,
                        std::vector<ChapterPoint>* chapters);

void save_index_cache(const std::string& document_path, uint64_t stride, uint64_t bytes,
                      uint64_t chars, const std::vector<IndexPoint>& points);

void save_index_cache_with_chapters(const std::string& document_path, uint64_t stride,
                                    uint64_t bytes, uint64_t chars,
                                    const std::vector<IndexPoint>& points,
                                    const std::vector<ChapterPoint>& chapters);

std::string index_cache_path(const std::string& document_path);

}  // namespace jingdu

#endif
