package com.qtekfun.ultimategallery.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class HomeViewMode { GRID, LIST }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SaveBehavior { ASK, OVERWRITE, COPY }

data class AppSettings(
    val homeViewMode: HomeViewMode = HomeViewMode.GRID,
    val folderGridColumns: Int = DEFAULT_FOLDER_COLUMNS,
    val photoGridColumns: Int = DEFAULT_PHOTO_COLUMNS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val saveBehavior: SaveBehavior = SaveBehavior.ASK,
    val lastProfileId: Long = NO_PROFILE,
    val appFoldersOffered: Boolean = false
) {
    companion object {
        const val DEFAULT_FOLDER_COLUMNS = 2
        const val DEFAULT_PHOTO_COLUMNS = 4
        const val NO_PROFILE = -1L
    }
}

/** Simple preferences, backed by DataStore. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            homeViewMode = p[HOME_VIEW]?.let { runCatching { HomeViewMode.valueOf(it) }.getOrNull() } ?: d.homeViewMode,
            folderGridColumns = p[FOLDER_COLUMNS] ?: d.folderGridColumns,
            photoGridColumns = p[PHOTO_COLUMNS] ?: d.photoGridColumns,
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[DYNAMIC] ?: d.dynamicColor,
            saveBehavior = p[SAVE]?.let { runCatching { SaveBehavior.valueOf(it) }.getOrNull() } ?: d.saveBehavior,
            lastProfileId = p[LAST_PROFILE] ?: d.lastProfileId,
            appFoldersOffered = p[APP_FOLDERS_OFFERED] ?: d.appFoldersOffered
        )
    }

    suspend fun setHomeViewMode(mode: HomeViewMode) = set(HOME_VIEW, mode.name)

    suspend fun setFolderGridColumns(columns: Int) = set(FOLDER_COLUMNS, columns)

    suspend fun setPhotoGridColumns(columns: Int) = set(PHOTO_COLUMNS, columns)

    suspend fun setThemeMode(mode: ThemeMode) = set(THEME, mode.name)

    suspend fun setDynamicColor(enabled: Boolean) = set(DYNAMIC, enabled)

    suspend fun setSaveBehavior(behavior: SaveBehavior) = set(SAVE, behavior.name)

    suspend fun setLastProfileId(id: Long) = set(LAST_PROFILE, id)

    suspend fun setAppFoldersOffered(offered: Boolean) = set(APP_FOLDERS_OFFERED, offered)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        val HOME_VIEW = stringPreferencesKey("home_view_mode")
        val FOLDER_COLUMNS = intPreferencesKey("folder_grid_columns")
        val PHOTO_COLUMNS = intPreferencesKey("photo_grid_columns")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SAVE = stringPreferencesKey("save_behavior")
        val LAST_PROFILE = longPreferencesKey("last_profile_id")
        val APP_FOLDERS_OFFERED = booleanPreferencesKey("app_folders_offered")
    }
}
