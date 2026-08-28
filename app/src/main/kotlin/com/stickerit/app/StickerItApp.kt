package com.stickerit.app

import android.app.Application
import com.stickerit.app.domain.SegmentationModelManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StickerItApp : Application() {
    @Inject
    lateinit var segmentationModelManager: SegmentationModelManager

    override fun onCreate() {
        super.onCreate()
        segmentationModelManager.prefetchIfNeeded()
    }
}
