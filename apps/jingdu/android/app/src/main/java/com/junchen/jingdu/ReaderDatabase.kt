package com.junchen.jingdu

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "reader_annotations",
    indices = [
        Index(value = ["bookId", "sourceStart"]),
        Index(value = ["bookId", "kind"]),
        Index(value = ["bookId", "updatedAt"]),
    ],
)
internal data class ReaderAnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val sourceStart: Long,
    val sourceEnd: Long,
    val kind: String,
    val style: String,
    val note: String,
    val excerpt: String,
    val anchorBefore: String,
    val anchorSelected: String,
    val anchorAfter: String,
    val anchorHash: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "reader_sessions", indices = [Index(value = ["bookId", "dayEpoch"]), Index(value = ["dayEpoch"])])
internal data class ReaderSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val dayEpoch: Long,
    val startedAt: Long,
    val durationMs: Long,
    val startPosition: Long,
    val endPosition: Long,
    val charsRead: Long,
)

@Entity(tableName = "reader_pace")
internal data class ReaderPaceEntity(
    @PrimaryKey val id: Int = 1,
    val charsPerMinute: Double,
    val samples: Int,
)

internal data class ReaderDayAggregate(
    val dayEpoch: Long,
    val durationMs: Long,
    val charsRead: Long,
)

@Dao
internal interface ReaderAnnotationDao {
    @Query("SELECT * FROM reader_annotations WHERE bookId = :bookId ORDER BY sourceStart, createdAt")
    fun observe(bookId: String): Flow<List<ReaderAnnotationEntity>>

    @Query("SELECT * FROM reader_annotations WHERE bookId = :bookId ORDER BY sourceStart, createdAt")
    suspend fun list(bookId: String): List<ReaderAnnotationEntity>

    @Query("SELECT * FROM reader_annotations ORDER BY bookId, sourceStart, createdAt")
    suspend fun listAll(): List<ReaderAnnotationEntity>

    @Query("SELECT * FROM reader_annotations WHERE id = :id AND bookId = :bookId LIMIT 1")
    suspend fun find(bookId: String, id: String): ReaderAnnotationEntity?

    @Upsert suspend fun upsert(value: ReaderAnnotationEntity)
    @Upsert suspend fun upsertAll(values: List<ReaderAnnotationEntity>)

    @Query("DELETE FROM reader_annotations WHERE id = :id AND bookId = :bookId")
    suspend fun delete(bookId: String, id: String)

    @Query("DELETE FROM reader_annotations WHERE bookId = :bookId")
    suspend fun clearBook(bookId: String)

    @Query("DELETE FROM reader_annotations")
    suspend fun clearAll()
}

@Dao
internal interface ReaderStatsDao {
    @Upsert suspend fun upsertPace(value: ReaderPaceEntity)

    @Query("SELECT * FROM reader_pace WHERE id = 1 LIMIT 1")
    suspend fun pace(): ReaderPaceEntity?

    @Upsert suspend fun insertSession(value: ReaderSessionEntity)
    @Upsert suspend fun upsertSessions(values: List<ReaderSessionEntity>)

    @Query("SELECT * FROM reader_sessions ORDER BY startedAt, id")
    suspend fun listSessions(): List<ReaderSessionEntity>

    @Query("DELETE FROM reader_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM reader_pace")
    suspend fun clearPace()

    @Query("SELECT dayEpoch, SUM(durationMs) AS durationMs, SUM(charsRead) AS charsRead FROM reader_sessions GROUP BY dayEpoch ORDER BY dayEpoch DESC LIMIT :limit")
    fun observeDays(limit: Int): Flow<List<ReaderDayAggregate>>

    @Query("SELECT dayEpoch, SUM(durationMs) AS durationMs, SUM(charsRead) AS charsRead FROM reader_sessions GROUP BY dayEpoch ORDER BY dayEpoch DESC LIMIT :limit")
    suspend fun days(limit: Int): List<ReaderDayAggregate>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reader_sessions WHERE bookId = :bookId")
    suspend fun totalBookDuration(bookId: String): Long
}

@Database(
    entities = [ReaderAnnotationEntity::class, ReaderSessionEntity::class, ReaderPaceEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class ReaderDatabase : RoomDatabase() {
    abstract fun annotationDao(): ReaderAnnotationDao
    abstract fun statsDao(): ReaderStatsDao
}

internal object ReaderDatabaseProvider {
    @Volatile private var instance: ReaderDatabase? = null

    fun get(context: Context): ReaderDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, ReaderDatabase::class.java, "jingdu-reader.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
            .also { instance = it }
    }
}
