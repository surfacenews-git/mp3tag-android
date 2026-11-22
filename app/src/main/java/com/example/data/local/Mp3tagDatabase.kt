package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.ActionGroup
import com.example.model.ActionStep
import com.example.model.ActionType
import com.example.model.CaseMode
import com.example.model.ConverterMode
import com.example.model.TagFieldKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Database(
    entities = [ActionGroupEntity::class, FormatPresetEntity::class, FolderBookmarkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class Mp3tagDatabase : RoomDatabase() {
    abstract fun mp3tagDao(): Mp3tagDao

    companion object {
        @Volatile
        private var INSTANCE: Mp3tagDatabase? = null

        fun getInstance(context: Context): Mp3tagDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    Mp3tagDatabase::class.java,
                    "mp3tag_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class LocalConfigRepository(private val dao: Mp3tagDao) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val stepsListType = Types.newParameterizedType(List::class.java, ActionStep::class.java)
    private val stepsAdapter = moshi.adapter<List<ActionStep>>(stepsListType)

    val actionGroupsFlow = dao.observeActionGroups()
    val formatPresetsFlow = dao.observeFormatPresets()
    val folderBookmarksFlow = dao.observeFolderBookmarks()

    fun decodeSteps(json: String): List<ActionStep> {
        return runCatching { stepsAdapter.fromJson(json) ?: emptyList() }.getOrDefault(emptyList())
    }

    fun encodeSteps(steps: List<ActionStep>): String {
        return stepsAdapter.toJson(steps)
    }

    fun toDomain(entity: ActionGroupEntity): ActionGroup {
        return ActionGroup(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            steps = decodeSteps(entity.stepsJson),
            isBuiltIn = entity.isBuiltIn
        )
    }

    suspend fun ensureDefaultsSeeded() {
        if (dao.countActionGroups() == 0) {
            val defaults = listOf(
                ActionGroup(
                    name = "Standardize Case & Trim",
                    description = "Trim extra spaces and apply smart Title Case across all tag fields",
                    isBuiltIn = true,
                    steps = listOf(
                        ActionStep("s1", ActionType.TRIM, TagFieldKey.ALL),
                        ActionStep("s2", ActionType.CASE_CONVERSION, TagFieldKey.ALL, caseMode = CaseMode.TITLE_CASE)
                    )
                ),
                ActionGroup(
                    name = "Normalize Featuring Credits",
                    description = "Replace ft. / featuring variations with standard feat. in Title & Artist",
                    isBuiltIn = true,
                    steps = listOf(
                        ActionStep(
                            id = "s3",
                            type = ActionType.REPLACE,
                            targetField = TagFieldKey.TITLE,
                            searchPattern = "\\b(ft\\.?|featuring)\\b",
                            replaceWith = "feat.",
                            useRegex = true
                        ),
                        ActionStep(
                            id = "s4",
                            type = ActionType.REPLACE,
                            targetField = TagFieldKey.ARTIST,
                            searchPattern = "\\b(ft\\.?|featuring)\\b",
                            replaceWith = "feat.",
                            useRegex = true
                        )
                    )
                ),
                ActionGroup(
                    name = "Clean Track Comments & Encoder",
                    description = "Strip Comment, Encoded By, and extra whitespace from selected tracks",
                    isBuiltIn = true,
                    steps = listOf(
                        ActionStep("s5", ActionType.REMOVE_FIELDS, TagFieldKey.COMMENT),
                        ActionStep("s6", ActionType.TRIM, TagFieldKey.ALL)
                    )
                ),
                ActionGroup(
                    name = "Copy Artist to Album Artist",
                    description = "Populate Album Artist from Artist when blank or mismatched",
                    isBuiltIn = true,
                    steps = listOf(
                        ActionStep("s7", ActionType.FORMAT_VALUE, TagFieldKey.ALBUM_ARTIST, formatString = "%artist%")
                    )
                )
            )
            dao.insertActionGroups(
                defaults.map {
                    ActionGroupEntity(
                        name = it.name,
                        description = it.description,
                        stepsJson = encodeSteps(it.steps),
                        isBuiltIn = true
                    )
                }
            )
        }

        if (dao.countFormatPresets() == 0) {
            dao.insertFormatPresets(
                listOf(
                    FormatPresetEntity(modeName = ConverterMode.TAG_TO_FILENAME.name, label = "01 - Title", pattern = "\$num(%track%,2) - %title%"),
                    FormatPresetEntity(modeName = ConverterMode.TAG_TO_FILENAME.name, label = "01 - Artist - Title", pattern = "\$num(%track%,2) - %artist% - %title%"),
                    FormatPresetEntity(modeName = ConverterMode.TAG_TO_FILENAME.name, label = "Artist - Album - 01 Title", pattern = "%artist% - %album% - \$num(%track%,2) %title%"),
                    FormatPresetEntity(modeName = ConverterMode.FILENAME_TO_TAG.name, label = "Track - Artist - Title", pattern = "%track% - %artist% - %title%"),
                    FormatPresetEntity(modeName = ConverterMode.FILENAME_TO_TAG.name, label = "Track - Title", pattern = "%track% - %title%"),
                    FormatPresetEntity(modeName = ConverterMode.FILENAME_TO_TAG.name, label = "Artist - Title", pattern = "%artist% - %title%"),
                    FormatPresetEntity(modeName = ConverterMode.TAG_TO_TAG.name, label = "Title (Year)", pattern = "%title% (%year%)"),
                    FormatPresetEntity(modeName = ConverterMode.TAG_TO_TAG.name, label = "Copy Artist -> Album Artist", pattern = "%artist%")
                )
            )
        }
    }

    suspend fun saveActionGroup(name: String, description: String, steps: List<ActionStep>) {
        dao.insertActionGroup(
            ActionGroupEntity(
                name = name,
                description = description,
                stepsJson = encodeSteps(steps),
                isBuiltIn = false
            )
        )
    }

    suspend fun deleteActionGroup(id: Long) = dao.deleteActionGroupById(id)

    suspend fun saveFormatPreset(mode: ConverterMode, label: String, pattern: String) {
        dao.insertFormatPreset(FormatPresetEntity(modeName = mode.name, label = label, pattern = pattern))
    }

    suspend fun deleteFormatPreset(id: Long) = dao.deleteFormatPreset(id)

    suspend fun recordFolderBookmark(uriString: String, displayName: String) {
        dao.upsertFolderBookmark(FolderBookmarkEntity(uriString, displayName, System.currentTimeMillis()))
    }

    suspend fun removeFolderBookmark(uriString: String) = dao.deleteFolderBookmark(uriString)
}
