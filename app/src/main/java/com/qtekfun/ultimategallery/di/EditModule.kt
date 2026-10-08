package com.qtekfun.ultimategallery.di

import com.qtekfun.ultimategallery.data.edit.EditSaver
import com.qtekfun.ultimategallery.data.edit.EditStore
import com.qtekfun.ultimategallery.data.edit.MediaEditSaver
import com.qtekfun.ultimategallery.data.edit.MediaStoreEditStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EditModule {
    @Binds
    abstract fun editSaver(impl: MediaEditSaver): EditSaver

    @Binds
    abstract fun editStore(impl: MediaStoreEditStore): EditStore
}
