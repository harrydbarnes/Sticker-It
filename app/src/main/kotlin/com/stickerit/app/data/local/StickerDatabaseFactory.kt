package com.stickerit.app.data.local

import android.content.Context
import androidx.room.Room

/**
 * Creates the application's Room database with one canonical name and
 * migration chain. The app process and the exported WhatsApp provider each
 * need a builder, so keeping the configuration here prevents them drifting
 * apart as the schema evolves.
 */
object StickerDatabaseFactory {
    private const val DATABASE_NAME = "sticker_it.db"

    fun create(context: Context): StickerDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            StickerDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        ).build()
}
