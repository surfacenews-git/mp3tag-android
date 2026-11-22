package com.example.audio.actions

import com.example.audio.converter.FormatStringEngine
import com.example.model.ActionPreviewItem
import com.example.model.ActionStep
import com.example.model.ActionType
import com.example.model.AudioTags
import com.example.model.AudioTrackItem
import com.example.model.CaseMode
import com.example.model.TagFieldKey
import java.util.Locale

object ActionRunner {

    private val TEXT_FIELDS = listOf(
        TagFieldKey.TITLE,
        TagFieldKey.ARTIST,
        TagFieldKey.ALBUM,
        TagFieldKey.ALBUM_ARTIST,
        TagFieldKey.GENRE,
        TagFieldKey.COMMENT,
        TagFieldKey.COMPOSER,
        TagFieldKey.PUBLISHER
    )

    fun applySteps(track: AudioTrackItem, steps: List<ActionStep>): AudioTags {
        var current = track.tags
        steps.filter { it.enabled }.forEach { step ->
            val workingTrack = track.copy(tags = current)
            current = applySingleStep(workingTrack, step)
        }
        return current
    }

    fun previewSteps(tracks: List<AudioTrackItem>, steps: List<ActionStep>): List<ActionPreviewItem> {
        return tracks.map { track ->
            val updated = applySteps(track, steps)
            val diffs = mutableListOf<String>()
            TEXT_FIELDS.forEach { field ->
                val oldVal = FormatStringEngine.getField(track.tags, field)
                val newVal = FormatStringEngine.getField(updated, field)
                if (oldVal != newVal) {
                    diffs.add("${field.label}: \"$oldVal\" → \"$newVal\"")
                }
            }
            if (track.tags.year != updated.year) diffs.add("Year: \"${track.tags.year}\" → \"${updated.year}\"")
            if (track.tags.track != updated.track) diffs.add("Track: \"${track.tags.track}\" → \"${updated.track}\"")
            if (track.tags.coverArt != null && updated.coverArt == null) diffs.add("Cover Art: Removed")
            ActionPreviewItem(track.id, track.fileName, diffs)
        }
    }

    private fun applySingleStep(track: AudioTrackItem, step: ActionStep): AudioTags {
        var tags = track.tags
        val targets = if (step.targetField == TagFieldKey.ALL) {
            TEXT_FIELDS
        } else {
            listOf(step.targetField)
        }

        return when (step.type) {
            ActionType.CASE_CONVERSION -> {
                targets.forEach { field ->
                    val oldVal = FormatStringEngine.getField(tags, field)
                    if (oldVal.isNotEmpty()) {
                        val transformed = when (step.caseMode) {
                            CaseMode.TITLE_CASE -> FormatStringEngine.toTitleCase(oldVal)
                            CaseMode.SENTENCE_CASE -> toSentenceCase(oldVal)
                            CaseMode.UPPERCASE -> oldVal.uppercase(Locale.getDefault())
                            CaseMode.LOWERCASE -> oldVal.lowercase(Locale.getDefault())
                        }
                        tags = FormatStringEngine.setField(tags, field, transformed)
                    }
                }
                tags
            }
            ActionType.TRIM -> {
                targets.forEach { field ->
                    val oldVal = FormatStringEngine.getField(tags, field)
                    if (oldVal.isNotEmpty()) {
                        val cleaned = oldVal.trim().replace(Regex(" {2,}"), " ")
                        tags = FormatStringEngine.setField(tags, field, cleaned)
                    }
                }
                tags
            }
            ActionType.REPLACE -> {
                if (step.searchPattern.isEmpty()) return tags
                targets.forEach { field ->
                    val oldVal = FormatStringEngine.getField(tags, field)
                    if (oldVal.isNotEmpty()) {
                        val replaced = if (step.useRegex) {
                            val opts = if (step.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                            runCatching {
                                oldVal.replace(Regex(step.searchPattern, opts), step.replaceWith)
                            }.getOrDefault(oldVal)
                        } else {
                            oldVal.replace(step.searchPattern, step.replaceWith, ignoreCase = !step.caseSensitive)
                        }
                        tags = FormatStringEngine.setField(tags, field, replaced)
                    }
                }
                tags
            }
            ActionType.FORMAT_VALUE -> {
                if (step.targetField == TagFieldKey.ALL) return tags
                val formatted = FormatStringEngine.evaluate(step.formatString, track)
                FormatStringEngine.setField(tags, step.targetField, formatted)
            }
            ActionType.REMOVE_FIELDS -> {
                if (step.targetField == TagFieldKey.ALL) {
                    tags.copy(comment = "", composer = "", publisher = "", copyright = "", encodedBy = "", bpm = "", customFields = emptyMap())
                } else {
                    FormatStringEngine.setField(tags, step.targetField, "")
                }
            }
            ActionType.REMOVE_COVER -> {
                tags.copy(coverArt = null)
            }
        }
    }

    private fun toSentenceCase(input: String): String {
        val lower = input.trim().lowercase(Locale.getDefault())
        if (lower.isEmpty()) return lower
        val chars = lower.toCharArray()
        for (i in chars.indices) {
            if (chars[i].isLetter()) {
                chars[i] = chars[i].uppercaseChar()
                break
            }
        }
        return String(chars)
    }
}
