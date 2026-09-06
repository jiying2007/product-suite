package com.junchen.jingdu

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Thin JNI boundary for the shared C++ Core ABI. Android product logic stays in Kotlin. */
internal object NativeCore {
    init {
        System.loadLibrary("jingdu_native")
        if (nativeAbiVersion() != 2) throw ExceptionInInitializerError("unsupported Jingdu core ABI")
    }

    @JvmStatic private external fun nativeAbiVersion(): Int
    @JvmStatic private external fun nativeDetectEncoding(sample: ByteArray, truncated: Boolean): String
    @JvmStatic private external fun nativeFileSha256(path: ByteArray): String
    @JvmStatic private external fun nativeRepairRevision(normalizedSha256: ByteArray, packedRules: ByteArray): String
    @JvmStatic private external fun nativeOpen(path: ByteArray): Long
    @JvmStatic external fun nativeClose(handle: Long)
    @JvmStatic external fun nativeCharCount(handle: Long): Long
    @JvmStatic private external fun nativeRead(handle: Long, offset: Long, count: Long): ByteArray
    @JvmStatic private external fun nativeSearch(handle: Long, query: ByteArray, limit: Int): ByteArray
    @JvmStatic private external fun nativeChapters(handle: Long, limit: Int): ByteArray
    @JvmStatic private external fun nativeNoiseCandidates(handle: Long, limit: Int): ByteArray
    @JvmStatic private external fun nativeSpeechChunk(handle: Long, offset: Long, count: Long): ByteArray
    @JvmStatic private external fun nativeExportRules(handle: Long, packedRules: ByteArray, outputPath: ByteArray)

    @Throws(IOException::class)
    fun detectEncoding(sample: ByteArray, truncated: Boolean): String = nativeDetectEncoding(sample, truncated)

    @Throws(IOException::class)
    fun fileSha256(file: File): String = nativeFileSha256(file.absolutePath.toByteArray(StandardCharsets.UTF_8))

    @Throws(IOException::class)
    fun repairRevision(normalizedSha256: String, packedRules: String): String = nativeRepairRevision(
        normalizedSha256.toByteArray(StandardCharsets.UTF_8),
        packedRules.toByteArray(StandardCharsets.UTF_8),
    )

    @Throws(IOException::class)
    fun open(file: File): Long = nativeOpen(file.absolutePath.toByteArray(StandardCharsets.UTF_8))

    @Throws(IOException::class)
    fun read(handle: Long, offset: Long, count: Long): String =
        nativeRead(handle, offset, count).toString(StandardCharsets.UTF_8)

    @Throws(IOException::class)
    fun search(handle: Long, query: String, limit: Int): String =
        nativeSearch(handle, query.toByteArray(StandardCharsets.UTF_8), limit).toString(StandardCharsets.UTF_8)

    @Throws(IOException::class)
    fun chapters(handle: Long, limit: Int): String = nativeChapters(handle, limit).toString(StandardCharsets.UTF_8)

    @Throws(IOException::class)
    fun noiseCandidates(handle: Long, limit: Int): String =
        nativeNoiseCandidates(handle, limit).toString(StandardCharsets.UTF_8)

    @Throws(IOException::class)
    fun speechChunk(handle: Long, offset: Long, count: Long): String =
        nativeSpeechChunk(handle, offset, count).toString(StandardCharsets.UTF_8)

    @Throws(IOException::class)
    fun exportRules(handle: Long, packed: String, output: File) {
        nativeExportRules(
            handle,
            packed.toByteArray(StandardCharsets.UTF_8),
            output.absolutePath.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
