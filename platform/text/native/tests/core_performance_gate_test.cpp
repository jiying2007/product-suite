#include "jingdu/core_api.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>

#if defined(__linux__) || defined(__APPLE__)
#include <sys/resource.h>
#endif

namespace {
using Clock = std::chrono::steady_clock;

int fail(const char* expression, int line) {
  std::cerr << "CHECK failed at line " << line << ": " << expression << std::endl;
  return 1;
}

#define CHECK(expr)                  \
  do {                               \
    if (!(expr)) {                   \
      return fail(#expr, __LINE__);  \
    }                                \
  } while (0)

std::string take(jd_buffer* buffer) {
  std::string value(buffer->data ? buffer->data : "", static_cast<size_t>(buffer->size));
  jd_buffer_free(buffer);
  return value;
}

size_t fixtureMiB() {
  constexpr size_t kDefaultMiB = 64;
  const char* raw = std::getenv("JINGDU_PERF_FIXTURE_MIB");
  if (raw == nullptr || *raw == '\0') return kDefaultMiB;
  const unsigned long parsed = std::strtoul(raw, nullptr, 10);
  if (parsed == 0) return kDefaultMiB;
  return std::clamp<size_t>(static_cast<size_t>(parsed), 20, 1024);
}

bool writeFixture(const char* path, size_t mib) {
  std::ofstream output(path, std::ios::binary | std::ios::trunc);
  if (!output) return false;
  const std::string block =
      "第1024章 性能门禁\n"
      "这是一段用于净读长篇TXT性能资格测试的稳定文本，包含中文、ASCII和数字0123456789。\n"
      "章节正文保持普通内容，不应被Smart Clean误判为广告。\n";
  const size_t target = mib * 1024U * 1024U;
  size_t written = 0;
  while (written + block.size() + 64 < target) {
    output.write(block.data(), static_cast<std::streamsize>(block.size()));
    if (!output) return false;
    written += block.size();
  }
  output << "JINGDU_PERFORMANCE_SENTINEL_终点\n";
  return static_cast<bool>(output);
}

long elapsedMs(Clock::time_point start) {
  return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - start).count();
}

long peakRssMiB() {
#if defined(__linux__) || defined(__APPLE__)
  struct rusage usage {};
  if (getrusage(RUSAGE_SELF, &usage) != 0) return -1;
#if defined(__APPLE__)
  return static_cast<long>(usage.ru_maxrss / (1024L * 1024L));
#else
  return static_cast<long>(usage.ru_maxrss / 1024L);
#endif
#else
  return -1;
#endif
}

void cleanup(const char* path) {
  std::remove(path);
  const std::string cache = std::string(path) + ".jdx";
  const std::string temporary = cache + ".tmp";
  std::remove(cache.c_str());
  std::remove(temporary.c_str());
}
}  // namespace

int main() {
  const char* path = "jingdu-performance-gate.txt";
  cleanup(path);
  const size_t mib = fixtureMiB();
  CHECK(writeFixture(path, mib));

  jd_handle first = 0;
  auto start = Clock::now();
  CHECK(jd_open_utf8(path, &first) == JD_OK);
  const long firstOpenMs = elapsedMs(start);
  CHECK(first != 0);
  const uint64_t chars = jd_char_count(first);
  CHECK(chars > 0);

  jd_buffer output{};
  start = Clock::now();
  CHECK(jd_search(first, "JINGDU_PERFORMANCE_SENTINEL_终点", 2, &output) == JD_OK);
  const long searchMs = elapsedMs(start);
  CHECK(take(&output).find("JINGDU_PERFORMANCE_SENTINEL") != std::string::npos);

  start = Clock::now();
  for (int iteration = 0; iteration < 1000; ++iteration) {
    const uint64_t offset = (static_cast<uint64_t>(iteration) * 104729ULL) % chars;
    CHECK(jd_read(first, offset, 2048, &output) == JD_OK);
    CHECK(output.size > 0);
    jd_buffer_free(&output);
  }
  const long randomReadMs = elapsedMs(start);
  jd_close(first);

  jd_handle second = 0;
  start = Clock::now();
  CHECK(jd_open_utf8(path, &second) == JD_OK);
  const long reopenMs = elapsedMs(start);
  CHECK(second != 0);
  jd_close(second);

  const long rssMiB = peakRssMiB();
  std::cerr << "Jingdu performance gate: fixture=" << mib << "MiB"
            << " firstOpen=" << firstOpenMs << "ms"
            << " reopen=" << reopenMs << "ms"
            << " search=" << searchMs << "ms"
            << " randomReads1000=" << randomReadMs << "ms"
            << " peakRss=" << rssMiB << "MiB" << std::endl;

  // Deliberately generous CI ceilings: these catch algorithmic regressions and accidental
  // whole-document materialization without pretending heterogeneous hosted runners are devices.
  const long scale = static_cast<long>(mib);
  CHECK(firstOpenMs < std::max(30000L, scale * 700L));
  CHECK(reopenMs < std::max(6000L, scale * 120L));
  CHECK(searchMs < std::max(12000L, scale * 250L));
  CHECK(randomReadMs < 10000L);
  // This ceiling is intentionally size-independent. The 960MiB CTest proves bounded-memory access.
  if (rssMiB >= 0) CHECK(rssMiB < 640L);

  cleanup(path);
  return 0;
}
