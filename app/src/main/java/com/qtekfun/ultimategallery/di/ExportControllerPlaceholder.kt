package com.qtekfun.ultimategallery.di

import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.domain.export.ExportStatus
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// TEMPORARY: replaced by the WorkManager implementation when the export backend is merged.
@Module
@InstallIn(SingletonComponent::class)
object ExportControllerPlaceholder {
    @Provides
    @Singleton
    fun controller(): ExportController = object : ExportController {
        override fun start(itemIds: List<Long>, profile: WatermarkProfile) = "placeholder"

        override fun observe(jobId: String): Flow<ExportStatus> = flowOf(ExportStatus.Failed("not available"))

        override fun cancel(jobId: String) = Unit
    }
}
