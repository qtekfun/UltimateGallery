package com.qtekfun.ultimategallery.di

import com.qtekfun.ultimategallery.data.media.MediaRepository
import com.qtekfun.ultimategallery.data.media.MediaStoreRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    @Singleton
    abstract fun mediaRepository(impl: MediaStoreRepository): MediaRepository
}
