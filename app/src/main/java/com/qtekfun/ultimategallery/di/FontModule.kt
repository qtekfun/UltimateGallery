package com.qtekfun.ultimategallery.di

import com.qtekfun.ultimategallery.data.fonts.AppFontLibrary
import com.qtekfun.ultimategallery.domain.watermark.FontLibrary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FontModule {
    @Binds
    @Singleton
    abstract fun fontLibrary(impl: AppFontLibrary): FontLibrary
}
