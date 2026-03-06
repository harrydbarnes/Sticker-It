package com.stickerit.app.di

import android.content.Context
import androidx.room.Room
import com.stickerit.app.data.local.StickerDao
import com.stickerit.app.data.local.StickerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StickerDatabase =
        Room.databaseBuilder(
            context,
            StickerDatabase::class.java,
            "sticker_it.db",
        ).build()

    @Provides
    fun provideStickerDao(db: StickerDatabase): StickerDao = db.stickerDao()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
