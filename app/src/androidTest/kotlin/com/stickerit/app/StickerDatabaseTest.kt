package com.stickerit.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stickerit.app.data.local.StickerDatabase
import com.stickerit.app.data.model.Sticker
import com.stickerit.app.data.model.StickerPackEntity
import com.stickerit.app.data.model.StickerPackItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StickerDatabaseTest {

    @Test
    fun deletingStickerRemovesItsPackMembership() = runBlocking {
        val database = newDatabase()
        try {
            val stickerId = database.stickerDao().insert(
                Sticker(filePath = "/data/user/0/com.stickerit.app/files/sticker.webp", name = "Test"),
            )
            database.stickerPackDao().insertPack(
                StickerPackEntity(id = "test-pack", name = "Test pack"),
            )
            database.stickerPackDao().insertItems(
                listOf(StickerPackItemEntity(packId = "test-pack", stickerId = stickerId)),
            )

            assertEquals(1, database.stickerPackDao().getItems("test-pack").size)

            database.stickerDao().deleteById(stickerId)

            assertTrue(database.stickerPackDao().getItems("test-pack").isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingPackRemovesItsMembershipRows() = runBlocking {
        val database = newDatabase()
        try {
            val stickerId = database.stickerDao().insert(
                Sticker(filePath = "/data/user/0/com.stickerit.app/files/sticker.webp", name = "Test"),
            )
            database.stickerPackDao().insertPack(
                StickerPackEntity(id = "test-pack", name = "Test pack"),
            )
            database.stickerPackDao().insertItems(
                listOf(StickerPackItemEntity(packId = "test-pack", stickerId = stickerId)),
            )

            database.stickerPackDao().deletePack("test-pack")

            assertTrue(database.stickerPackDao().getItems("test-pack").isEmpty())
            assertEquals(1, database.stickerDao().getAll().size)
        } finally {
            database.close()
        }
    }

    private fun newDatabase(): StickerDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        StickerDatabase::class.java,
    ).allowMainThreadQueries().build()
}
