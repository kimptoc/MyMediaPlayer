package com.example.mymediaplayer.shared

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val durationMs: Long?,
    val year: Int?
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val uriString: String,
    val displayName: String
)

@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey val id: Int = 0,
    val treeUri: String,
    val scanLimit: Int,
    val scannedAt: Long
)

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM media_files")
    fun getAllFiles(): List<MediaFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFiles(files: List<MediaFileEntity>)

    @Query("DELETE FROM media_files")
    fun clearFiles()

    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Query("DELETE FROM playlists")
    fun clearPlaylists()

    @Query("SELECT * FROM scan_state WHERE id = 0")
    fun getScanState(): ScanStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertScanState(state: ScanStateEntity)

    @Query("DELETE FROM scan_state")
    fun clearScanState()

    @Transaction
    fun replaceCache(
        files: List<MediaFileEntity>,
        playlists: List<PlaylistEntity>,
        state: ScanStateEntity
    ) {
        clearFiles()
        clearPlaylists()
        clearScanState()
        if (files.isNotEmpty()) insertFiles(files)
        if (playlists.isNotEmpty()) insertPlaylists(playlists)
        upsertScanState(state)
    }
}

@Database(
    entities = [MediaFileEntity::class, PlaylistEntity::class, ScanStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MediaCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): MediaCacheDao

    companion object {
        @Volatile
        private var instance: MediaCacheDatabase? = null

        fun getInstance(context: Context): MediaCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediaCacheDatabase::class.java,
                    "media_cache.db"
                ).build().also { instance = it }
            }
        }
    }
}
