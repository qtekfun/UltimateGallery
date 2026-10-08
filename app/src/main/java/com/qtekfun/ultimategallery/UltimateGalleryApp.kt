package com.qtekfun.ultimategallery

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.qtekfun.ultimategallery.core.image.MediaThumbnailFetcher
import com.qtekfun.ultimategallery.core.image.MediaThumbnailKeyer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UltimateGalleryApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(MediaThumbnailKeyer())
            add(MediaThumbnailFetcher.Factory(context))
        }
        .build()
}
