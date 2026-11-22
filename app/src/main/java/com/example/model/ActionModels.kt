package com.example.model

enum class ActionType(val label: String, val description: String) {
    CASE_CONVERSION("Case Conversion", "Title Case, Sentence case, UPPER, or lower"),
    REPLACE("Replace / RegEx", "Find and replace text or regular expressions"),
    FORMAT_VALUE("Format Value", "Compose a field using %tag% format strings"),
    TRIM("Trim Whitespace", "Strip leading/trailing and extra spaces"),
    REMOVE_FIELDS("Remove Fields", "Strip specific fields like Comment or BPM"),
    REMOVE_COVER("Remove Cover Art", "Strip embedded cover artwork from files")
}

enum class CaseMode(val label: String) {
    TITLE_CASE("Title Case (Mixed)"),
    SENTENCE_CASE("Sentence case"),
    UPPERCASE("UPPERCASE"),
    LOWERCASE("lowercase")
}

data class ActionStep(
    val id: String,
    val type: ActionType,
    val targetField: TagFieldKey = TagFieldKey.ALL,
    val caseMode: CaseMode = CaseMode.TITLE_CASE,
    val searchPattern: String = "",
    val replaceWith: String = "",
    val useRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val formatString: String = "",
    val enabled: Boolean = true
)

data class ActionGroup(
    val id: Long = 0L,
    val name: String,
    val description: String,
    val steps: List<ActionStep>,
    val isBuiltIn: Boolean = false
)

data class ActionPreviewItem(
    val trackId: String,
    val filename: String,
    val changes: List<String>
)
