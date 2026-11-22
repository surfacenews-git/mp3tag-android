package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface Mp3tagDao {
    @Query("SELECT * FROM action_groups ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observeActionGroups(): Flow<List<ActionGroupEntity>>

    @Query("SELECT COUNT(*) FROM action_groups")
    suspend fun countActionGroups(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionGroup(entity: ActionGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionGroups(entities: List<ActionGroupEntity>)

    @Query("DELETE FROM action_groups WHERE id = :id")
    suspend fun deleteActionGroupById(id: Long)

    @Query("SELECT * FROM format_presets ORDER BY id ASC")
    fun observeFormatPresets(): Flow<List<FormatPresetEntity>>

    @Query("SELECT COUNT(*) FROM format_presets")
    suspend fun countFormatPresets(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormatPresets(presets: List<FormatPresetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormatPreset(preset: FormatPresetEntity): Long

    @Query("DELETE FROM format_presets WHERE id = :id")
    suspend fun deleteFormatPreset(id: Long)

    @Query("SELECT * FROM folder_bookmarks ORDER BY lastOpenedMs DESC LIMIT 10")
    fun observeFolderBookmarks(): Flow<List<FolderBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolderBookmark(bookmark: FolderBookmarkEntity)

    @Query("DELETE FROM folder_bookmarks WHERE uriString = :uriString")
    suspend fun deleteFolderBookmark(uriString: String)
}
