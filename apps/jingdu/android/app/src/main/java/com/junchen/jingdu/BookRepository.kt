package com.junchen.jingdu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Private immutable TXT source/revision repository. Android product persistence is Kotlin-only. */
internal class BookRepository(context: Context) {
    data class Book(
        val id: String,
        val name: String,
        val encoding: String,
        val size: Long,
        val sourceSha256: String,
        val normalizedSha256: String,
        var progress: Long,
        var charCount: Long,
        var touchedAt: Long,
    )

    private val context = context.applicationContext
    private val root = File(context.filesDir, "books")
    private val errorLog = ProductErrorLog(context)

    init {
        if (!root.isDirectory && !root.mkdirs()) error("cannot create books directory")
        cleanupRootTemporaries()
    }

    @Synchronized
    fun list(): List<Book> {
        val books = mutableListOf<Book>()
        runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.getString("id")
                val sourceSha = item.getString("sourceSha256")
                val normalizedSha = item.getString("normalizedSha256")
                if (id.length != 64 || id != sourceSha || normalizedSha.length != 64) continue
                if (!rawFile(id).isFile || !normalizedFile(id, normalizedSha).isFile) continue
                books += Book(
                    id = id,
                    name = item.getString("name"),
                    encoding = item.getString("encoding"),
                    size = item.optLong("size"),
                    sourceSha256 = sourceSha,
                    normalizedSha256 = normalizedSha,
                    progress = item.optLong("progress"),
                    charCount = item.optLong("charCount"),
                    touchedAt = item.optLong("touchedAt"),
                )
            }
        }
        // Hard-cut v2 metadata: malformed private state is intentionally not migrated.
        return books.sortedByDescending(Book::touchedAt)
    }

    @Synchronized
    @Throws(Exception::class)
    fun importUri(uri: Uri, requestedEncoding: String?): Book {
        val sourceTemporary = File.createTempFile(".source-", ".tmp", root)
        var normalizedTemporary: File? = null
        try {
            val size = copyUri(uri, sourceTemporary)
            val sourceSha = NativeCore.fileSha256(sourceTemporary)
            val existing = findById(sourceSha)
            val directory = directory(sourceSha)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create book directory")

            var encoding = requestedEncoding ?: AUTO
            if (encoding == AUTO) encoding = detect(sourceTemporary)
            normalizedTemporary = File.createTempFile(".document-", ".tmp", directory)
            normalize(sourceTemporary, normalizedTemporary, encoding)
            val normalizedSha = NativeCore.fileSha256(normalizedTemporary)

            PrivateFilePublisher.publishImmutable(sourceTemporary, rawFile(sourceSha))
            PrivateFilePublisher.publishImmutable(normalizedTemporary, normalizedFile(sourceSha, normalizedSha))
            normalizedTemporary = null

            val restoredProgress = LibraryMetadataStore(context).consumeRestoredProgress(sourceSha, normalizedSha)
            val sameRevision = existing?.normalizedSha256 == normalizedSha
            val book = Book(
                id = sourceSha,
                name = displayName(uri),
                encoding = encoding,
                size = size,
                sourceSha256 = sourceSha,
                normalizedSha256 = normalizedSha,
                progress = existing?.progress ?: restoredProgress ?: 0L,
                charCount = if (sameRevision) existing.charCount else 0L,
                touchedAt = System.currentTimeMillis(),
            )
            upsert(book)
            prewarmChapterIndex(book)
            return book
        } catch (error: Throwable) {
            errorLog.record(ProductErrorClassifier.importFailure(error), "book.import")
            throw error
        } finally {
            deleteTemporary(sourceTemporary)
            deleteTemporary(normalizedTemporary)
        }
    }

    @Synchronized
    @Throws(Exception::class)
    fun redecode(book: Book?, requestedEncoding: String?): Book {
        book ?: throw IOException("no book selected")
        val raw = rawFile(book.id)
        if (!raw.isFile) throw IOException("private source copy is missing")
        val oldLength = documentLength(book)

        var encoding = requestedEncoding ?: AUTO
        if (encoding == AUTO) encoding = detect(raw)
        val temporary = File.createTempFile(".document-", ".tmp", directory(book.id))
        try {
            normalize(raw, temporary, encoding)
            val normalizedSha = NativeCore.fileSha256(temporary)
            PrivateFilePublisher.publishImmutable(temporary, normalizedFile(book.id, normalizedSha))
            val restoredProgress = LibraryMetadataStore(context).consumeRestoredProgress(book.id, normalizedSha)
            val sameRevision = book.normalizedSha256 == normalizedSha
            val updated = Book(
                id = book.id,
                name = book.name,
                encoding = encoding,
                size = book.size,
                sourceSha256 = book.sourceSha256,
                normalizedSha256 = normalizedSha,
                progress = restoredProgress ?: book.progress,
                charCount = if (sameRevision) book.charCount else 0L,
                touchedAt = System.currentTimeMillis(),
            )
            upsert(updated)
            prewarmChapterIndex(updated)
            val newLength = documentLength(updated)
            if (oldLength > 0 && newLength > 0) {
                ReaderAnnotationStore(context).remapBookForRedecode(book.id, oldLength, newLength)
            }
            return updated
        } catch (error: Throwable) {
            errorLog.record(ProductErrorClassifier.redecodeFailure(error), "book.redecode")
            throw error
        } finally {
            deleteTemporary(temporary)
        }
    }

    @Synchronized
    fun saveProgress(book: Book?, progress: Long) {
        book ?: return
        book.progress = progress.coerceAtLeast(0)
        book.touchedAt = System.currentTimeMillis()
        upsert(book)
    }

    @Synchronized
    fun updateCharCount(book: Book?, charCount: Long) {
        if (book == null || charCount <= 0 || book.charCount == charCount) return
        book.charCount = charCount
        if (book.progress >= charCount) book.progress = (charCount - 1).coerceAtLeast(0)
        upsert(book)
    }

    @Synchronized
    fun delete(book: Book?) {
        book ?: return
        deleteTree(directory(book.id))
        write(list().filterNot { it.id == book.id })
    }

    fun normalizedFile(book: Book): File = normalizedFile(book.id, book.normalizedSha256)

    fun cleanFile(book: Book, revision: String): File = File(directory(book.id), "clean-$revision.txt")

    @Throws(IOException::class)
    fun repairRevision(book: Book, packedRules: String?): String =
        NativeCore.repairRevision(book.normalizedSha256, packedRules.orEmpty())

    fun pruneDocumentRevisions(book: Book) {
        val keep = normalizedFile(book)
        directory(book.id).listFiles()?.forEach { file ->
            val name = file.name
            when {
                name.startsWith("document-") && name.endsWith(".txt") && file != keep -> deleteTemporary(file)
                name.startsWith(".document-") && name.endsWith(".tmp") -> deleteTemporary(file)
                name == "document.txt" -> deleteTemporary(file)
            }
        }
    }

    fun pruneCleanRevisions(book: Book, keep: File?) {
        directory(book.id).listFiles()?.forEach { file ->
            val name = file.name
            when {
                name.startsWith("clean-") && name.endsWith(".txt") && file != keep -> deleteTemporary(file)
                name.startsWith("clean-") && name.endsWith(".txt.tmp") -> deleteTemporary(file)
                name == "clean.txt" || name == "clean.revision" -> deleteTemporary(file)
            }
        }
    }

    private fun documentLength(book: Book): Long = runCatching {
        ReaderController(false).use { source ->
            source.open(normalizedFile(book), 0)
            source.length()
        }
    }.getOrDefault(book.charCount)

    private fun prewarmChapterIndex(book: Book): Long = runCatching {
        ReaderController(false).use { source ->
            source.open(normalizedFile(book), 0)
            val length = source.length()
            source.chapters()
            ReaderDerivedIndexPrewarmer.prewarm(context, book.id, book.normalizedSha256, length, source)
            length
        }
    }.getOrDefault(0L)

    private fun cleanupRootTemporaries() {
        root.listFiles()?.filter { it.name.startsWith(".source-") && it.name.endsWith(".tmp") }?.forEach(::deleteTemporary)
    }

    private fun findById(id: String): Book? = list().firstOrNull { it.id == id }

    @Throws(IOException::class)
    private fun copyUri(uri: Uri, target: File): Long {
        val source = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open selected file")
        var total = 0L
        BufferedInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
                output.fd.sync()
            }
        }
        return total
    }

    @Throws(IOException::class)
    private fun detect(raw: File): String {
        val sampleLength = minOf(raw.length(), SAMPLE_BYTES.toLong()).toInt()
        val sample = ByteArray(sampleLength)
        var total = 0
        FileInputStream(raw).use { input ->
            while (total < sample.size) {
                val count = input.read(sample, total, sample.size - total)
                if (count < 0) break
                total += count
            }
        }
        val actual = if (total == sample.size) sample else sample.copyOf(total)
        return NativeCore.detectEncoding(actual, raw.length() > actual.size)
    }

    @Throws(IOException::class)
    private fun normalize(raw: File, target: File, encodingName: String) {
        val charset = try {
            Charset.forName(encodingName)
        } catch (error: Exception) {
            throw IOException("unsupported encoding: $encodingName", error)
        }
        FileInputStream(raw).use { input ->
            val reader = BufferedReader(
                InputStreamReader(
                    input,
                    charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE),
                ),
                64 * 1024,
            )
            FileOutputStream(target).use { output ->
                val writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8), 64 * 1024)
                val buffer = CharArray(32 * 1024)
                var first = true
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    val start = if (first && count > 0 && buffer[0] == '\uFEFF') 1 else 0
                    first = false
                    writer.write(buffer, start, count - start)
                }
                writer.flush()
                output.fd.sync()
            }
        }
    }

    @Synchronized
    private fun upsert(book: Book) {
        write(list().filterNot { it.id == book.id } + book)
    }

    private fun write(books: List<Book>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject()
                    .put("id", book.id)
                    .put("name", book.name)
                    .put("encoding", book.encoding)
                    .put("size", book.size)
                    .put("sourceSha256", book.sourceSha256)
                    .put("normalizedSha256", book.normalizedSha256)
                    .put("progress", book.progress)
                    .put("charCount", book.charCount)
                    .put("touchedAt", book.touchedAt),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "TXT"
    }

    private fun directory(id: String): File = File(root, id)
    private fun rawFile(id: String): File = File(directory(id), "source.bin")
    private fun normalizedFile(id: String, normalizedSha: String): File = File(directory(id), "document-$normalizedSha.txt")

    private fun deleteTemporary(file: File?) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit()
    }

    private fun deleteTree(file: File?) {
        if (file == null || !file.exists()) return
        file.listFiles()?.forEach(::deleteTree)
        if (!file.delete()) file.deleteOnExit()
    }

    companion object {
        const val AUTO = "AUTO"
        val ENCODINGS = arrayOf(AUTO, "UTF-8", "GB18030", "GBK", "GB2312", "Big5", "UTF-16", "UTF-16LE", "UTF-16BE")
        private const val SAMPLE_BYTES = 64 * 1024
        private const val PREFS = "jingdu.library.v2"
        private const val KEY = "books"
    }
}
