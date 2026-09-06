#ifndef JINGDU_CORE_API_H
#define JINGDU_CORE_API_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JD_SHA256_HEX_SIZE 65U

typedef uint64_t jd_handle;
typedef int32_t jd_status;
typedef uint32_t jd_detect_flags;

#define JD_OK ((jd_status)0)
#define JD_EINVAL ((jd_status)1)
#define JD_ENOENT ((jd_status)2)
#define JD_EIO ((jd_status)3)
#define JD_EUTF8 ((jd_status)4)
#define JD_ENOMEM ((jd_status)5)
#define JD_EHANDLE ((jd_status)6)

#define JD_DETECT_NONE ((jd_detect_flags)0U)
#define JD_DETECT_SAMPLE_TRUNCATED ((jd_detect_flags)(1U << 0))

typedef struct jd_buffer {
  char* data;
  uint64_t size;
} jd_buffer;

uint32_t jd_abi_version(void);
const char* jd_core_version(void);

/*
 * Detects the source encoding from a bounded sample.
 * Set JD_DETECT_SAMPLE_TRUNCATED when more source bytes exist after `data`.
 * That flag allows an incomplete final multibyte sequence to be ignored rather
 * than incorrectly classifying an otherwise valid UTF-8/legacy sample.
 */
jd_status jd_detect_encoding(const uint8_t* data, uint64_t size, uint32_t flags,
                             char* out_name, uint64_t out_capacity);

/* Lowercase SHA-256 hex, always 64 chars plus NUL. */
jd_status jd_sha256(const uint8_t* data, uint64_t size, char* out_hex,
                    uint64_t out_capacity);
jd_status jd_file_sha256(const char* path, char* out_hex, uint64_t out_capacity);

/* Deterministic derived-view revision: SHA256(normalizedSha + rule pack). */
jd_status jd_repair_revision(const char* normalized_sha256, const char* packed_rules,
                             char* out_hex, uint64_t out_capacity);

/* Opens a normalized UTF-8 document. Handles are process-local. */
jd_status jd_open_utf8(const char* path, jd_handle* out_handle);
void jd_close(jd_handle handle);
uint64_t jd_char_count(jd_handle handle);
uint64_t jd_byte_count(jd_handle handle);

/* All offsets/counts below are Unicode scalar/code-point counts, not bytes. */
jd_status jd_read(jd_handle handle, uint64_t char_offset, uint64_t max_chars,
                  jd_buffer* out);
jd_status jd_search(jd_handle handle, const char* utf8_query, uint32_t limit,
                    jd_buffer* out);
jd_status jd_chapters(jd_handle handle, uint32_t limit, jd_buffer* out);

/*
 * Local smart-clean analysis. Output is UTF-8 TSV, one candidate per line:
 *   score<TAB>count<TAB>reason<TAB>text<LF>
 * score is 0..100. Analysis is streaming/bounded and never modifies the source.
 */
jd_status jd_noise_candidates(jd_handle handle, uint32_t limit, jd_buffer* out);

jd_status jd_speech_chunk(jd_handle handle, uint64_t char_offset,
                          uint64_t max_chars, jd_buffer* out);
jd_status jd_export_rules(jd_handle handle, const char* packed_rules,
                          const char* output_path);

/* Frees only buffers returned by this ABI. Safe on NULL/empty buffers. */
void jd_buffer_free(jd_buffer* buffer);

#ifdef __cplusplus
}
#endif
#endif
