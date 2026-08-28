package com.stickerit.app.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stickerit.app.data.model.PackStickerRow
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.model.StickerPackEntity
import com.stickerit.app.data.model.StickerPackItemEntity
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

@Dao
interface StickerPackDao {

    @Query("SELECT * FROM sticker_packs ORDER BY sortOrder ASC, createdAt ASC")
    fun observePacks(): Flow<List<StickerPackEntity>>

    /** Synchronous reads are used by ContentProvider binder threads. */
    @Query("SELECT * FROM sticker_packs ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllPacks(): List<StickerPackEntity>

    @Query("SELECT * FROM sticker_packs WHERE id = :packId")
    fun getPack(packId: String): StickerPackEntity?

    @Query("SELECT COUNT(*) FROM sticker_packs")
    fun countPacks(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPack(pack: StickerPackEntity)

    @Update
    fun updatePack(pack: StickerPackEntity)

    @Query("DELETE FROM sticker_packs WHERE id = :packId")
    fun deletePack(packId: String)

    @Query("SELECT * FROM sticker_pack_items WHERE packId = :packId ORDER BY sortOrder ASC, stickerId ASC")
    fun observeItems(packId: String): Flow<List<StickerPackItemEntity>>

    /** Synchronous reads are used by ContentProvider binder threads. */
    @Query("SELECT * FROM sticker_pack_items WHERE packId = :packId ORDER BY sortOrder ASC, stickerId ASC")
    fun getItems(packId: String): List<StickerPackItemEntity>

    @Query("""
        SELECT s.filePath AS filePath,
               spi.emojis AS emojis,
               spi.accessibilityText AS accessibilityText
        FROM sticker_pack_items AS spi
        INNER JOIN stickers AS s ON s.id = spi.stickerId
        WHERE spi.packId = :packId
        ORDER BY spi.sortOrder ASC, spi.stickerId ASC
    """)
    fun getPackStickers(packId: String): List<PackStickerRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItems(items: List<StickerPackItemEntity>)

    @Update
    fun updateItem(item: StickerPackItemEntity)

    @Query("DELETE FROM sticker_pack_items WHERE packId = :packId")
    fun deleteItems(packId: String)

    @Transaction
    fun replaceItems(packId: String, items: List<StickerPackItemEntity>) {
        deleteItems(packId)
        if (items.isNotEmpty()) insertItems(items)
    }
}

// ---------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------

@Database(
    entities = [Sticker::class, StickerPackEntity::class, StickerPackItemEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class StickerDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao
    abstract fun stickerPackDao(): StickerPackDao
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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sticker_packs (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                publisher TEXT NOT NULL,
                trayImageFileName TEXT NOT NULL,
                trayImageIsCustom INTEGER NOT NULL,
                imageDataVersion TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sticker_pack_items (
                packId TEXT NOT NULL,
                stickerId INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                emojis TEXT NOT NULL,
                accessibilityText TEXT NOT NULL,
                PRIMARY KEY(packId, stickerId),
                FOREIGN KEY(packId) REFERENCES sticker_packs(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(stickerId) REFERENCES stickers(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sticker_pack_items_stickerId ON sticker_pack_items(stickerId)",
        )

        // Preserve the old fixed-pack identity as the first named pack. The
        // repository imports the legacy JSON manifest's exact membership once
        // the app has a Context and can read the old private file.
        database.execSQL(
            """
            INSERT OR IGNORE INTO sticker_packs
                (id, name, publisher, trayImageFileName, trayImageIsCustom, imageDataVersion, createdAt, sortOrder)
            VALUES
                ('stickerit_library', 'Sticker It library', 'Sticker It', 'whatsapp_tray.png', 0, '1',
                 CAST(strftime('%s', 'now') AS INTEGER) * 1000, 0)
            """.trimIndent(),
        )
    }
}
