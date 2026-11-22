package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.TagSourceProvider
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mp3tag_settings")

data class AppSettings(
    val id3v2Version: Int = 3, // 3 for ID3v2.3 UTF-16, 4 for ID3v2.4 UTF-8
    val writeId3v1: Boolean = true,
    val scanSubfolders: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultTagSource: TagSourceProvider = TagSourceProvider.ITUNES,
    val autoEmbedFetchedCover: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ID3V2_VERSION = intPreferencesKey("id3v2_version")
        val WRITE_ID3V1 = booleanPreferencesKey("write_id3v1")
        val SCAN_SUBFOLDERS = booleanPreferencesKey("scan_subfolders")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_TAG_SOURCE = stringPreferencesKey("default_tag_source")
        val AUTO_EMBED_COVER = booleanPreferencesKey("auto_embed_cover")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            id3v2Version = prefs[Keys.ID3V2_VERSION] ?: 3,
            writeId3v1 = prefs[Keys.WRITE_ID3V1] ?: true,
            scanSubfolders = prefs[Keys.SCAN_SUBFOLDERS] ?: true,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            defaultTagSource = runCatching {
                TagSourceProvider.valueOf(prefs[Keys.DEFAULT_TAG_SOURCE] ?: TagSourceProvider.ITUNES.name)
            }.getOrDefault(TagSourceProvider.ITUNES),
            autoEmbedFetchedCover = prefs[Keys.AUTO_EMBED_COVER] ?: true
        )
    }

    suspend fun setId3v2Version(version: Int) {
        context.dataStore.edit { it[Keys.ID3V2_VERSION] = if (version == 4) 4 else 3 }
    }

    suspend fun setWriteId3v1(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WRITE_ID3V1] = enabled }
    }

    suspend fun setScanSubfolders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCAN_SUBFOLDERS] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultTagSource(provider: TagSourceProvider) {
        context.dataStore.edit { it[Keys.DEFAULT_TAG_SOURCE] = provider.name }
    }

    suspend fun setAutoEmbedCover(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_EMBED_COVER] = enabled }
    }
}
