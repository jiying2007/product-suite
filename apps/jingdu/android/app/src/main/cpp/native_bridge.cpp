#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <string>

#include "jingdu/core_api.h"

namespace {
std::string bytes(JNIEnv* env, jbyteArray value) {
  if (value == nullptr) return {};
  const jsize size = env->GetArrayLength(value);
  std::string out(static_cast<size_t>(size), '\0');
  if (size > 0) env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte*>(out.data()));
  return out;
}

jbyteArray array(JNIEnv* env, const char* data, uint64_t size) {
  if (size > static_cast<uint64_t>(INT32_MAX)) return nullptr;
  jbyteArray out = env->NewByteArray(static_cast<jsize>(size));
  if (out != nullptr && size != 0) {
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
  }
  return out;
}

jbyteArray take(JNIEnv* env, jd_buffer* buffer) {
  jbyteArray out = array(env, buffer->data, buffer->size);
  jd_buffer_free(buffer);
  return out;
}

void fail(JNIEnv* env, int status, const char* operation) {
  if (status == JD_OK) return;
  jclass type = env->FindClass("java/io/IOException");
  const std::string message = std::string(operation) + " failed: " + std::to_string(status);
  env->ThrowNew(type, message.c_str());
}

jstring hashResult(JNIEnv* env, int status, const char* hash, const char* operation) {
  if (status != JD_OK) { fail(env, status, operation); return nullptr; }
  return env->NewStringUTF(hash);
}

jint abi(JNIEnv*, jclass) { return static_cast<jint>(jd_abi_version()); }

jstring detect(JNIEnv* env, jclass, jbyteArray sample, jboolean truncated) {
  const std::string input = bytes(env, sample);
  char name[32]{};
  const uint32_t flags = truncated == JNI_TRUE ? JD_DETECT_SAMPLE_TRUNCATED : JD_DETECT_NONE;
  const int status = jd_detect_encoding(reinterpret_cast<const uint8_t*>(input.data()), input.size(), flags, name, sizeof(name));
  if (status != JD_OK) { fail(env, status, "detectEncoding"); return nullptr; }
  return env->NewStringUTF(name);
}

jstring fileSha(JNIEnv* env, jclass, jbyteArray pathBytes) {
  const std::string path = bytes(env, pathBytes);
  char hash[JD_SHA256_HEX_SIZE]{};
  return hashResult(env, jd_file_sha256(path.c_str(), hash, sizeof(hash)), hash, "fileSha256");
}

jstring repairRevision(JNIEnv* env, jclass, jbyteArray normalizedShaBytes, jbyteArray rulesBytes) {
  const std::string normalizedSha = bytes(env, normalizedShaBytes);
  const std::string rules = bytes(env, rulesBytes);
  char hash[JD_SHA256_HEX_SIZE]{};
  return hashResult(env, jd_repair_revision(normalizedSha.c_str(), rules.c_str(), hash, sizeof(hash)), hash, "repairRevision");
}

jlong openDoc(JNIEnv* env, jclass, jbyteArray pathBytes) {
  const std::string path = bytes(env, pathBytes);
  jd_handle handle = 0;
  const int status = jd_open_utf8(path.c_str(), &handle);
  if (status != JD_OK) { fail(env, status, "open"); return 0; }
  return static_cast<jlong>(handle);
}

void closeDoc(JNIEnv*, jclass, jlong handle) { jd_close(static_cast<jd_handle>(handle)); }
jlong charCount(JNIEnv*, jclass, jlong handle) { return static_cast<jlong>(jd_char_count(static_cast<jd_handle>(handle))); }

jbyteArray readDoc(JNIEnv* env, jclass, jlong handle, jlong offset, jlong count) {
  jd_buffer out{};
  const int status = jd_read(static_cast<jd_handle>(handle), static_cast<uint64_t>(std::max<jlong>(0, offset)), static_cast<uint64_t>(std::max<jlong>(0, count)), &out);
  if (status != JD_OK) { fail(env, status, "read"); return nullptr; }
  return take(env, &out);
}

jbyteArray searchDoc(JNIEnv* env, jclass, jlong handle, jbyteArray queryBytes, jint limit) {
  const std::string query = bytes(env, queryBytes);
  jd_buffer out{};
  const int status = jd_search(static_cast<jd_handle>(handle), query.c_str(), static_cast<uint32_t>(std::max(1, limit)), &out);
  if (status != JD_OK) { fail(env, status, "search"); return nullptr; }
  return take(env, &out);
}

jbyteArray chapters(JNIEnv* env, jclass, jlong handle, jint limit) {
  jd_buffer out{};
  const int status = jd_chapters(static_cast<jd_handle>(handle), static_cast<uint32_t>(std::max(1, limit)), &out);
  if (status != JD_OK) { fail(env, status, "chapters"); return nullptr; }
  return take(env, &out);
}

jbyteArray noiseCandidates(JNIEnv* env, jclass, jlong handle, jint limit) {
  jd_buffer out{};
  const int status = jd_noise_candidates(static_cast<jd_handle>(handle),
                                         static_cast<uint32_t>(std::max(1, limit)), &out);
  if (status != JD_OK) { fail(env, status, "noiseCandidates"); return nullptr; }
  return take(env, &out);
}

jbyteArray speech(JNIEnv* env, jclass, jlong handle, jlong offset, jlong count) {
  jd_buffer out{};
  const int status = jd_speech_chunk(static_cast<jd_handle>(handle), static_cast<uint64_t>(std::max<jlong>(0, offset)), static_cast<uint64_t>(std::max<jlong>(1, count)), &out);
  if (status != JD_OK) { fail(env, status, "speechChunk"); return nullptr; }
  return take(env, &out);
}

void exportRules(JNIEnv* env, jclass, jlong handle, jbyteArray rulesBytes, jbyteArray pathBytes) {
  const std::string rules = bytes(env, rulesBytes);
  const std::string path = bytes(env, pathBytes);
  const int status = jd_export_rules(static_cast<jd_handle>(handle), rules.c_str(), path.c_str());
  if (status != JD_OK) fail(env, status, "exportRules");
}

JNINativeMethod methods[] = {
    {"nativeAbiVersion", "()I", reinterpret_cast<void*>(abi)},
    {"nativeDetectEncoding", "([BZ)Ljava/lang/String;", reinterpret_cast<void*>(detect)},
    {"nativeFileSha256", "([B)Ljava/lang/String;", reinterpret_cast<void*>(fileSha)},
    {"nativeRepairRevision", "([B[B)Ljava/lang/String;", reinterpret_cast<void*>(repairRevision)},
    {"nativeOpen", "([B)J", reinterpret_cast<void*>(openDoc)},
    {"nativeClose", "(J)V", reinterpret_cast<void*>(closeDoc)},
    {"nativeCharCount", "(J)J", reinterpret_cast<void*>(charCount)},
    {"nativeRead", "(JJJ)[B", reinterpret_cast<void*>(readDoc)},
    {"nativeSearch", "(J[BI)[B", reinterpret_cast<void*>(searchDoc)},
    {"nativeChapters", "(JI)[B", reinterpret_cast<void*>(chapters)},
    {"nativeNoiseCandidates", "(JI)[B", reinterpret_cast<void*>(noiseCandidates)},
    {"nativeSpeechChunk", "(JJJ)[B", reinterpret_cast<void*>(speech)},
    {"nativeExportRules", "(J[B[B)V", reinterpret_cast<void*>(exportRules)}
};
}  // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
  jclass cls = env->FindClass("com/junchen/jingdu/NativeCore");
  if (cls == nullptr) return JNI_ERR;
  if (env->RegisterNatives(cls, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) return JNI_ERR;
  return JNI_VERSION_1_6;
}
