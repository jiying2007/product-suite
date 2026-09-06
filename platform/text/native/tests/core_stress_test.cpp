#include "jingdu/core_api.h"

#include <atomic>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <string>
#include <thread>
#include <vector>

namespace {

int fail(const char* expression, int line) {
  std::cerr << "CHECK failed at line " << line << ": " << expression << std::endl;
  return 1;
}

#define CHECK(expr)                \
  do {                             \
    if (!(expr)) {                 \
      return fail(#expr, __LINE__); \
    }                              \
  } while (0)

std::string take(jd_buffer* buffer) {
  std::string value(buffer->data ? buffer->data : "", static_cast<size_t>(buffer->size));
  jd_buffer_free(buffer);
  return value;
}

bool writeLargeFixture(const char* path) {
  std::ofstream output(path, std::ios::binary | std::ios::trunc);
  if (!output) return false;
  const std::string block =
      "第十二章 压力测试\nabcdefghijklmnopqrstuvwxyz 世界 0123456789\n";
  const size_t target = 32U * 1024U * 1024U;
  size_t written = 0;
  while (written + block.size() < target) {
    output.write(block.data(), static_cast<std::streamsize>(block.size()));
    if (!output) return false;
    written += block.size();
  }
  output << "CROSS_BOUNDARY_SENTINEL_世界_END\n";
  return static_cast<bool>(output);
}

int malformedInputIsRejected() {
  const char* paths[] = {
      "jingdu-overlong.txt", "jingdu-truncated.txt", "jingdu-surrogate.txt"};
  const std::vector<std::vector<unsigned char>> cases = {
      {0xC0, 0xAF}, {'o', 'k', 0xE4, 0xB8}, {0xED, 0xA0, 0x80}};

  for (size_t i = 0; i < cases.size(); ++i) {
    {
      std::ofstream output(paths[i], std::ios::binary | std::ios::trunc);
      CHECK(static_cast<bool>(output));
      output.write(reinterpret_cast<const char*>(cases[i].data()),
                   static_cast<std::streamsize>(cases[i].size()));
      CHECK(static_cast<bool>(output));
    }
    jd_handle handle = 0;
    CHECK(jd_open_utf8(paths[i], &handle) == JD_EUTF8);
    CHECK(handle == 0);
    std::remove(paths[i]);
  }
  return 0;
}

int detectorContract() {
  char encoding[32]{};
  const uint8_t utf8Bom[] = {0xEF, 0xBB, 0xBF, 'a'};
  CHECK(jd_detect_encoding(utf8Bom, sizeof(utf8Bom), JD_DETECT_NONE, encoding,
                           sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "UTF-8");

  const uint8_t utf16LeBom[] = {0xFF, 0xFE, 0x61, 0x00};
  CHECK(jd_detect_encoding(utf16LeBom, sizeof(utf16LeBom), JD_DETECT_NONE, encoding,
                           sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "UTF-16LE");

  const uint8_t gb18030[] = {0xD6, 0xD0, 0xCE, 0xC4};
  CHECK(jd_detect_encoding(gb18030, sizeof(gb18030), JD_DETECT_NONE, encoding,
                           sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "GB18030");

  const uint8_t truncatedBoundary[] = {'x', 0xE4, 0xB8};
  CHECK(jd_detect_encoding(truncatedBoundary, sizeof(truncatedBoundary),
                           JD_DETECT_SAMPLE_TRUNCATED, encoding, sizeof(encoding)) == JD_OK);
  CHECK(std::string(encoding) == "UTF-8");
  return 0;
}

}  // namespace

int main() {
  CHECK(detectorContract() == 0);
  CHECK(malformedInputIsRejected() == 0);

  const char* path = "jingdu-stress.txt";
  CHECK(writeLargeFixture(path));

  jd_handle handle = 0;
  const int openStatus = jd_open_utf8(path, &handle);
  if (openStatus != JD_OK) {
    std::cerr << "large fixture open status=" << openStatus << std::endl;
    std::remove(path);
    return 1;
  }
  CHECK(handle != 0);

  const uint64_t bytes = jd_byte_count(handle);
  const uint64_t chars = jd_char_count(handle);
  std::cerr << "large fixture bytes=" << bytes << " chars=" << chars << std::endl;
  CHECK(bytes >= 31U * 1024U * 1024U);
  CHECK(chars > 10U * 1024U * 1024U);

  jd_buffer result{};
  CHECK(jd_search(handle, "CROSS_BOUNDARY_SENTINEL_世界_END", 2, &result) == JD_OK);
  CHECK(take(&result).find("CROSS_BOUNDARY_SENTINEL") != std::string::npos);

  const uint64_t middle = chars / 2;
  CHECK(jd_read(handle, middle, 4096, &result) == JD_OK);
  CHECK(!take(&result).empty());

  std::atomic<int> failures{0};
  std::vector<std::thread> workers;
  for (int workerIndex = 0; workerIndex < 8; ++workerIndex) {
    workers.emplace_back([&, workerIndex]() {
      for (int iteration = 0; iteration < 100; ++iteration) {
        const uint64_t offset =
            (middle + static_cast<uint64_t>(workerIndex * 997 + iteration * 113)) % chars;
        jd_buffer local{};
        const int status = jd_read(handle, offset, 512, &local);
        if (status != JD_OK || local.size == 0) ++failures;
        jd_buffer_free(&local);
      }
    });
  }
  for (auto& worker : workers) {
    worker.join();
  }
  CHECK(failures.load() == 0);

  for (int iteration = 0; iteration < 256; ++iteration) {
    jd_handle extra = 0;
    CHECK(jd_open_utf8(path, &extra) == JD_OK);
    CHECK(extra != 0);
    jd_close(extra);
  }

  jd_close(handle);
  std::remove(path);
  return 0;
}
