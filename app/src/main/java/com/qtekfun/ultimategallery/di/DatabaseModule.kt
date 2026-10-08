package com.qtekfun.ultimategallery.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimategallery.data.db.AppDatabase
import com.qtekfun.ultimategallery.data.db.HiddenFolderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "ultimategallery.db"

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): AppDatabase =
        Room.databaseBuilder<AppDatabase>(context, DATABASE_NAME)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(io)
            .build()

    @Provides
    fun hiddenFolderDao(db: AppDatabase): HiddenFolderDao = db.hiddenFolderDao()
}
