package com.qtekfun.ultimategallery.di

import com.qtekfun.ultimategallery.domain.watermark.FontLibrary
import com.qtekfun.ultimategallery.render.WatermarkBitmapStore
import com.qtekfun.ultimategallery.render.WatermarkRenderer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RenderModule {
    @Provides
    @Singleton
    fun watermarkRenderer(store: WatermarkBitmapStore, fontLibrary: FontLibrary): WatermarkRenderer = WatermarkRenderer(store::get, fontLibrary::typeface)
}
