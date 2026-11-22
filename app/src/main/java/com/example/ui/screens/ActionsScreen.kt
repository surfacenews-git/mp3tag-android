package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ActionGroup
import com.example.model.ActionPreviewItem
import com.example.model.ActionStep
import com.example.model.ActionType
import com.example.model.AudioTrackItem
import com.example.model.CaseMode
import com.example.model.TagFieldKey
import java.util.UUID

@Composable
fun ActionsScreen(
    targetTracks: List<AudioTrackItem>,
    actionGroups: List<ActionGroup>,
    isSaving: Boolean,
    previewSteps: (List<ActionStep>) -> List<ActionPreviewItem>,
    onExecuteSteps: (List<ActionStep>, String) -> Unit,
    onSaveActionGroup: (String, String, List<ActionStep>) -> Unit,
    onDeleteActionGroup: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var quickActionType by remember { mutableStateOf(ActionType.CASE_CONVERSION) }
    var targetField by remember { mutableStateOf(TagFieldKey.ALL) }
    var caseMode by remember { mutableStateOf(CaseMode.TITLE_CASE) }
    var searchPattern by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var formatValuePattern by remember { mutableStateOf("%artist%") }

    var activePreview by remember { mutableStateOf<Pair<String, List<ActionStep>>?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val currentQuickStep = remember(
        quickActionType, targetField, caseMode, searchPattern, replaceWith, useRegex, formatValuePattern
    ) {
        ActionStep(
            id = "quick",
            type = quickActionType,
            targetField = targetField,
            caseMode = caseMode,
            searchPattern = searchPattern,
            replaceWith = replaceWith,
            useRegex = useRegex,
            formatString = formatValuePattern
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Actions & Automations", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Batch cleanup across ${targetTracks.size} track(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledTonalButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.testTag("new_action_group_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Group")
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Quick Action Card
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Quick Action", style = MaterialTheme.typography.titleMedium)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ActionType.entries.forEach { type ->
                                    FilterChip(
                                        selected = quickActionType == type,
                                        onClick = { quickActionType = type },
                                        label = { Text(type.label, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }

                            if (quickActionType != ActionType.REMOVE_COVER) {
                                var showFieldMenu by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Apply to Field", style = MaterialTheme.typography.bodyMedium)
                                    Box {
                                        OutlinedButton(onClick = { showFieldMenu = true }) {
                                            Text(targetField.label)
                                        }
                                        DropdownMenu(expanded = showFieldMenu, onDismissRequest = { showFieldMenu = false }) {
                                            TagFieldKey.entries.forEach { f ->
                                                if (quickActionType == ActionType.FORMAT_VALUE && f == TagFieldKey.ALL) return@forEach
                                                DropdownMenuItem(
                                                    text = { Text(f.label) },
                                                    onClick = {
                                                        targetField = f
                                                        showFieldMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            when (quickActionType) {
                                ActionType.CASE_CONVERSION -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CaseMode.entries.forEach { cm ->
                                            FilterChip(
                                                selected = caseMode == cm,
                                                onClick = { caseMode = cm },
                                                label = { Text(cm.label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                                ActionType.REPLACE -> {
                                    OutlinedTextField(
                                        value = searchPattern,
                                        onValueChange = { searchPattern = it },
                                        label = { Text(if (useRegex) "RegEx Pattern" else "Find Text") },
                                        leadingIcon = { Icon(Icons.Default.FindReplace, null) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("quick_replace_search")
                                    )
                                    OutlinedTextField(
                                        value = replaceWith,
                                        onValueChange = { replaceWith = it },
                                        label = { Text("Replace With") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("quick_replace_with")
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Use Regular Expression", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = useRegex, onCheckedChange = { useRegex = it })
                                    }
                                }
                                ActionType.FORMAT_VALUE -> {
                                    OutlinedTextField(
                                        value = formatValuePattern,
                                        onValueChange = { formatValuePattern = it },
                                        label = { Text("Format String (e.g. %artist% - %title%)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                else -> {
                                    Text(
                                        text = quickActionType.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { activePreview = quickActionType.label to listOf(currentQuickStep) },
                                    enabled = targetTracks.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_action_preview")
                                ) {
                                    Icon(Icons.Default.Preview, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Preview")
                                }
                                Button(
                                    onClick = { onExecuteSteps(listOf(currentQuickStep), quickActionType.label) },
                                    enabled = targetTracks.isNotEmpty() && !isSaving,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_action_apply")
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Run Action")
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Action Groups", style = MaterialTheme.typography.titleMedium)
                }

                items(actionGroups, key = { it.id }) { group ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = group.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!group.isBuiltIn) {
                                    IconButton(
                                        onClick = { onDeleteActionGroup(group.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete group", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Text(
                                text = "${group.steps.size} step(s): ${group.steps.joinToString(" → ") { it.type.label }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { activePreview = group.name to group.steps },
                                    enabled = targetTracks.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Preview")
                                }
                                FilledTonalButton(
                                    onClick = { onExecuteSteps(group.steps, group.name) },
                                    enabled = targetTracks.isNotEmpty() && !isSaving,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("run_action_group_${group.id}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Execute")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    // Preview Diff Dialog
    activePreview?.let { (label, steps) ->
        val items = remember(label, steps, targetTracks) { previewSteps(steps) }
        AlertDialog(
            onDismissRequest = { activePreview = null },
            title = { Text("Preview: $label") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(items) { item ->
                        Column {
                            Text(item.filename, style = MaterialTheme.typography.labelMedium)
                            if (item.changes.isEmpty()) {
                                Text("No changes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                item.changes.forEach { diff ->
                                    Text(diff, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activePreview = null
                        onExecuteSteps(steps, label)
                    },
                    enabled = !isSaving
                ) {
                    Text("Apply All")
                }
            },
            dismissButton = {
                TextButton(onClick = { activePreview = null }) { Text("Close") }
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateActionGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onSave = { name, desc, steps ->
                onSaveActionGroup(name, desc, steps)
                showCreateGroupDialog = false
            }
        )
    }
}

@Composable
private fun CreateActionGroupDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, List<ActionStep>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val steps = remember {
        mutableStateListOf(
            ActionStep(UUID.randomUUID().toString(), ActionType.TRIM, TagFieldKey.ALL),
            ActionStep(UUID.randomUUID().toString(), ActionType.CASE_CONVERSION, TagFieldKey.ALL, caseMode = CaseMode.TITLE_CASE)
        )
    }
    var addType by remember { mutableStateOf(ActionType.REPLACE) }
    var search by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Action Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Steps (${steps.size}):", style = MaterialTheme.typography.labelLarge)
                steps.forEachIndexed { idx, s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${idx + 1}. ${s.type.label} (${s.targetField.label})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (steps.size > 1) steps.removeAt(idx) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove step", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ActionType.TRIM, ActionType.CASE_CONVERSION, ActionType.REPLACE, ActionType.REMOVE_FIELDS).forEach { t ->
                        FilterChip(selected = addType == t, onClick = { addType = t }, label = { Text(t.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                if (addType == ActionType.REPLACE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Find") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = replace, onValueChange = { replace = it }, label = { Text("Replace") }, modifier = Modifier.weight(1f))
                    }
                }
                OutlinedButton(
                    onClick = {
                        steps.add(
                            ActionStep(
                                id = UUID.randomUUID().toString(),
                                type = addType,
                                targetField = if (addType == ActionType.REMOVE_FIELDS) TagFieldKey.COMMENT else TagFieldKey.ALL,
                                searchPattern = search,
                                replaceWith = replace
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add ${addType.label} Step")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && steps.isNotEmpty()) {
                        onSave(name.trim(), description.ifBlank { "Custom workflow" }, steps.toList())
                    }
                }
            ) { Text("Save Group") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
