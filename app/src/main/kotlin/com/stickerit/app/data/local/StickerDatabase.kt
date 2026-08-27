package com.stickerit.app.data.local

import androidx.room.*
import com.stickerit.app.data.model.Sticker
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// DAO
// ---------------------------------------------------------------------------

@Dao
interface StickerDao {

    @Query("SELECT * FROM stickers ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<Sticker>>

    @Query("SELECT * FROM stickers ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<Sticker>

    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun getById(id: Long): Sticker?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sticker: Sticker): Long

    @Update
    suspend fun update(sticker: Sticker)

    @Delete
    suspend fun delete(sticker: Sticker)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    suspend fun reorder(stickers: List<Sticker>) {
        stickers.forEachIndexed { index, sticker ->
            update(sticker.copy(sortOrder = index))
        }
    }

}

// ---------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------

@Database(
    entities = [Sticker::class],
    version = 1,
    exportSchema = true,
)
abstract class StickerDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao
}
