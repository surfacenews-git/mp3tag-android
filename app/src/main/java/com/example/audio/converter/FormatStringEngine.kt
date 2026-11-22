package com.example.audio.converter

import com.example.model.AudioTags
import com.example.model.AudioTrackItem
import com.example.model.TagFieldKey
import java.util.Locale

/**
 * Evaluates Mp3tag-style format strings (%artist%, %title%, $num(%track%,2), $caps(...), etc.)
 * and parses Filename -> Tag patterns (%track% - %artist% - %title%).
 */
object FormatStringEngine {

    private val PLACEHOLDER_REGEX = Regex("%([a-zA-Z0-9_]+)%")

    fun evaluate(pattern: String, track: AudioTrackItem): String {
        if (pattern.isBlank()) return ""
        val withPlaceholders = PLACEHOLDER_REGEX.replace(pattern) { match ->
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            resolvePlaceholder(key, track)
        }
        return evaluateFunctions(withPlaceholders)
    }

    fun evaluateFilenameRename(pattern: String, track: AudioTrackItem): String {
        val raw = evaluate(pattern, track).trim()
        if (raw.isEmpty()) return track.fileName
        // Keep only the filename leaf (or allow subfolder indicator cleanly sanitized for single-dir rename)
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val sanitized = sanitizeFilename(leaf).ifBlank { track.baseName }
        val ext = track.extension
        return if (ext.isNotEmpty() && !sanitized.endsWith(".$ext", ignoreCase = true)) {
            "$sanitized.$ext"
        } else {
            sanitized
        }
    }

    fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
    }

    fun resolvePlaceholder(key: String, track: AudioTrackItem): String {
        val t = track.tags
        return when (key) {
            "title" -> t.title
            "artist" -> t.artist
            "album" -> t.album
            "albumartist", "band" -> t.albumArtist
            "year", "date" -> t.year
            "track" -> t.track.substringBefore('/').trim()
            "track_full" -> t.track
            "discnumber", "disc" -> t.discNumber.substringBefore('/').trim()
            "genre" -> t.genre
            "comment" -> t.comment
            "composer" -> t.composer
            "publisher" -> t.publisher
            "copyright" -> t.copyright
            "bpm" -> t.bpm
            "unsyncedlyrics", "lyrics" -> t.lyrics
            "_filename" -> track.baseName
            "_filename_ext" -> track.fileName
            "_extension" -> track.extension
            "_directory" -> track.directoryName
            "_bitrate" -> track.techInfo.bitrateKbps.toString()
            "_samplerate" -> track.techInfo.sampleRateHz.toString()
            "_length" -> track.techInfo.formattedDuration
            else -> t.customFields.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: ""
        }
    }

    private fun evaluateFunctions(input: String): String {
        var current = input
        // Evaluate innermost $func(args) calls iteratively
        val funcPattern = Regex("\\$([a-zA-Z]+)\\(([^()]*)\\)")
        var safety = 0
        while (safety < 16) {
            val match = funcPattern.find(current) ?: break
            val name = match.groupValues[1].lowercase(Locale.ROOT)
            val args = splitArgs(match.groupValues[2])
            val replacement = evalSingleFunction(name, args)
            current = current.replaceRange(match.range, replacement)
            safety++
        }
        return current
    }

    private fun splitArgs(raw: String): List<String> {
        // Simple comma split respecting simple escaping
        return raw.split(',').map { it.trim() }
    }

    private fun evalSingleFunction(name: String, args: List<String>): String {
        val first = args.getOrNull(0) ?: ""
        return when (name) {
            "num" -> {
                val width = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 8) ?: 2
                val num = first.substringBefore('/').trim().toIntOrNull()
                if (num != null) num.toString().padStart(width, '0') else first
            }
            "upper" -> first.uppercase(Locale.getDefault())
            "lower" -> first.lowercase(Locale.getDefault())
            "caps", "caps2" -> toTitleCase(first)
            "trim" -> first.trim()
            "replace" -> {
                val from = args.getOrNull(1) ?: ""
                val to = args.getOrNull(2) ?: ""
                if (from.isNotEmpty()) first.replace(from, to) else first
            }
            "left" -> {
                val n = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: first.length
                first.take(n)
            }
            "right" -> {
                val n = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: first.length
                first.takeLast(n)
            }
            "if" -> {
                val thenVal = args.getOrNull(1) ?: ""
                val elseVal = args.getOrNull(2) ?: ""
                if (first.isNotBlank()) thenVal else elseVal
            }
            else -> first
        }
    }

    /**
     * Parses a filename (without extension) against a pattern like "%track% - %artist% - %title%"
     * Returns map of placeholder name -> extracted value, or null if filename didn't match pattern.
     */
    fun parseFilenameToTags(baseFilename: String, pattern: String): Map<String, String>? {
        if (pattern.isBlank()) return null
        val placeholders = mutableListOf<String>()
        val regexBuilder = StringBuilder("^")
        var lastIndex = 0
        val matches = PLACEHOLDER_REGEX.findAll(pattern).toList()
        if (matches.isEmpty()) return null

        matches.forEachIndexed { idx, m ->
            val literal = pattern.substring(lastIndex, m.range.first)
            if (literal.isNotEmpty()) {
                regexBuilder.append(Regex.escape(literal))
            }
            val key = m.groupValues[1].lowercase(Locale.ROOT)
            placeholders.add(key)

            val hasNextLiteral = if (idx < matches.lastIndex) {
                pattern.substring(m.range.last + 1, matches[idx + 1].range.first).isNotEmpty()
            } else {
                pattern.substring(m.range.last + 1).isNotEmpty()
            }
            // Use lazy capture when followed by delimiter, greedy at end
            if (idx == matches.lastIndex && !hasNextLiteral) {
                regexBuilder.append("(.+)")
            } else {
                regexBuilder.append("(.+?)")
            }
            lastIndex = m.range.last + 1
        }
        val tail = pattern.substring(lastIndex)
        if (tail.isNotEmpty()) regexBuilder.append(Regex.escape(tail))
        regexBuilder.append("$")

        val regex = Regex(regexBuilder.toString())
        val match = regex.find(baseFilename) ?: return null
        val result = linkedMapOf<String, String>()
        placeholders.forEachIndexed { idx, key ->
            if (key != "dummy") {
                val value = match.groupValues.getOrNull(idx + 1)?.trim() ?: ""
                if (value.isNotEmpty()) result[key] = value
            }
        }
        return result
    }

    fun applyExtractedFields(tags: AudioTags, extracted: Map<String, String>): AudioTags {
        var updated = tags
        extracted.forEach { (k, v) ->
            updated = when (k.lowercase(Locale.ROOT)) {
                "title" -> updated.copy(title = v)
                "artist" -> updated.copy(artist = v)
                "album" -> updated.copy(album = v)
                "albumartist", "band" -> updated.copy(albumArtist = v)
                "year", "date" -> updated.copy(year = v)
                "track" -> updated.copy(track = v)
                "discnumber", "disc" -> updated.copy(discNumber = v)
                "genre" -> updated.copy(genre = v)
                "comment" -> updated.copy(comment = v)
                "composer" -> updated.copy(composer = v)
                "publisher" -> updated.copy(publisher = v)
                "bpm" -> updated.copy(bpm = v)
                else -> updated
            }
        }
        return updated
    }

    fun getField(tags: AudioTags, key: TagFieldKey): String {
        return when (key) {
            TagFieldKey.TITLE -> tags.title
            TagFieldKey.ARTIST -> tags.artist
            TagFieldKey.ALBUM -> tags.album
            TagFieldKey.ALBUM_ARTIST -> tags.albumArtist
            TagFieldKey.YEAR -> tags.year
            TagFieldKey.TRACK -> tags.track
            TagFieldKey.DISC -> tags.discNumber
            TagFieldKey.GENRE -> tags.genre
            TagFieldKey.COMMENT -> tags.comment
            TagFieldKey.COMPOSER -> tags.composer
            TagFieldKey.PUBLISHER -> tags.publisher
            TagFieldKey.COPYRIGHT -> tags.copyright
            TagFieldKey.BPM -> tags.bpm
            TagFieldKey.LYRICS -> tags.lyrics
            TagFieldKey.ALL -> ""
        }
    }

    fun setField(tags: AudioTags, key: TagFieldKey, value: String): AudioTags {
        return when (key) {
            TagFieldKey.TITLE -> tags.copy(title = value)
            TagFieldKey.ARTIST -> tags.copy(artist = value)
            TagFieldKey.ALBUM -> tags.copy(album = value)
            TagFieldKey.ALBUM_ARTIST -> tags.copy(albumArtist = value)
            TagFieldKey.YEAR -> tags.copy(year = value)
            TagFieldKey.TRACK -> tags.copy(track = value)
            TagFieldKey.DISC -> tags.copy(discNumber = value)
            TagFieldKey.GENRE -> tags.copy(genre = value)
            TagFieldKey.COMMENT -> tags.copy(comment = value)
            TagFieldKey.COMPOSER -> tags.copy(composer = value)
            TagFieldKey.PUBLISHER -> tags.copy(publisher = value)
            TagFieldKey.COPYRIGHT -> tags.copy(copyright = value)
            TagFieldKey.BPM -> tags.copy(bpm = value)
            TagFieldKey.LYRICS -> tags.copy(lyrics = value)
            TagFieldKey.ALL -> tags
        }
    }

    private val SMALL_LOWER_WORDS = setOf("feat.", "ft.", "vs.", "a", "an", "and", "the", "of", "in", "on", "at", "to", "for", "by")
    private val ROMAN_NUMERALS = Regex("^(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII)$", RegexOption.IGNORE_CASE)

    fun toTitleCase(input: String): String {
        if (input.isBlank()) return input
        val words = input.trim().split(Regex("\\s+"))
        return words.mapIndexed { index, word ->
            val clean = word.trim('(', ')', '[', ']', '"', '\'')
            val lower = word.lowercase(Locale.getDefault())
            when {
                ROMAN_NUMERALS.matches(clean) -> word.uppercase(Locale.getDefault())
                index > 0 && index < words.lastIndex && SMALL_LOWER_WORDS.contains(clean.lowercase(Locale.getDefault())) -> lower
                else -> capitalizeWordPreservingDelimiters(lower)
            }
        }.joinToString(" ")
    }

    private fun capitalizeWordPreservingDelimiters(word: String): String {
        val chars = word.toCharArray()
        var capNext = true
        for (i in chars.indices) {
            val c = chars[i]
            if (c.isLetter()) {
                if (capNext) {
                    chars[i] = c.uppercaseChar()
                    capNext = false
                }
            } else if (c == '(' || c == '[' || c == '-' || c == '/' || c == '"') {
                capNext = true
            } else if (c == '\'') {
                capNext = false
            }
        }
        return String(chars)
    }
}
