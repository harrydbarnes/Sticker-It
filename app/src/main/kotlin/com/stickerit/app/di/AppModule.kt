package com.stickerit.app.di

import android.content.Context
import com.stickerit.app.data.local.StickerDao
import com.stickerit.app.data.local.StickerPackDao
import com.stickerit.app.data.local.StickerDatabase
import com.stickerit.app.data.local.StickerDatabaseFactory
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
        StickerDatabaseFactory.create(context)

    @Provides
    fun provideStickerDao(db: StickerDatabase): StickerDao = db.stickerDao()

    @Provides
    fun provideStickerPackDao(db: StickerDatabase): StickerPackDao = db.stickerPackDao()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
