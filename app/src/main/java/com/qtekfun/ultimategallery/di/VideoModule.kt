package com.qtekfun.ultimategallery.di

import com.qtekfun.ultimategallery.data.video.MediaStoreVideoStore
import com.qtekfun.ultimategallery.data.video.MediaVideoRotation
import com.qtekfun.ultimategallery.data.video.VideoStore
import com.qtekfun.ultimategallery.domain.video.VideoRotation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VideoModule {
    @Binds
    abstract fun videoRotation(impl: MediaVideoRotation): VideoRotation

    @Binds
    abstract fun videoStore(impl: MediaStoreVideoStore): VideoStore
}
