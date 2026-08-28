package com.stickerit.app.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = true,
)
abstract class StickerDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE stickers ADD COLUMN sourceFilePath TEXT")
        database.execSQL("ALTER TABLE stickers ADD COLUMN maskFilePath TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE stickers ADD COLUMN finishRecipeJson TEXT")
    }
}
