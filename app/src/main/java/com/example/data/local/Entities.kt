package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_groups")
data class ActionGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val stepsJson: String,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "format_presets")
data class FormatPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val modeName: String,
    val label: String,
    val pattern: String
)

@Entity(tableName = "folder_bookmarks")
data class FolderBookmarkEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val lastOpenedMs: Long = System.currentTimeMillis()
)
