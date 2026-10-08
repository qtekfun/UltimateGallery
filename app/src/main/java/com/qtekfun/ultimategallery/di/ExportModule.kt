package com.qtekfun.ultimategallery.di

import com.qtekfun.ultimategallery.data.export.WorkManagerExportController
import com.qtekfun.ultimategallery.domain.export.ExportController
import com.qtekfun.ultimategallery.render.export.ExportSink
import com.qtekfun.ultimategallery.render.export.MediaStoreExportSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds
    @Singleton
    abstract fun exportController(impl: WorkManagerExportController): ExportController

    @Binds
    abstract fun exportSink(impl: MediaStoreExportSink): ExportSink
}
