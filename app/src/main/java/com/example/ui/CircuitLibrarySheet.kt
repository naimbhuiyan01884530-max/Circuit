package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SavedCircuitSummary
import com.example.ui.theme.LabAmber
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabCyan
import com.example.ui.theme.LabEmerald
import com.example.ui.theme.LabRose
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitLibrarySheet(
    currentName: String,
    circuits: List<SavedCircuitSummary>,
    exportedJson: String,
    onLoadCircuit: (String) -> Unit,
    onSaveCurrent: (name: String) -> Unit,
    onNewBlank: () -> Unit,
    onDeleteCircuit: (String) -> Unit,
    onImportJsonText: (String, (Boolean, String) -> Unit) -> Unit,
    onRequestExportFile: () -> Unit,
    onRequestImportFile: () -> Unit,
    onDismiss: () -> Unit
) {
    var saveName by remember(currentName) { mutableStateOf(currentName) }
    var showJsonModal by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }
    var jsonFeedback by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LabSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Circuit Library (SQLite / JSON)",
                    style = MaterialTheme.typography.titleLarge,
                    color = LabCyan
                )
                OutlinedButton(
                    onClick = {
                        onNewBlank()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("library_new_blank_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }

            // Save current schematic to SQLite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Schematic Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("library_save_name_input")
                )
                Button(
                    onClick = { onSaveCurrent(saveName) },
                    colors = ButtonDefaults.buttonColors(containerColor = LabCyan),
                    modifier = Modifier.testTag("library_save_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }

            // JSON Import / Export Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRequestExportFile,
                    modifier = Modifier.weight(1f).testTag("library_export_file_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Save .JSON", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onRequestImportFile,
                    modifier = Modifier.weight(1f).testTag("library_import_file_btn")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Load .JSON", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        jsonInput = exportedJson
                        jsonFeedback = null
                        showJsonModal = true
                    },
                    modifier = Modifier.weight(1f).testTag("library_raw_json_btn")
                ) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("JSON", maxLines = 1)
                }
            }

            // Saved & Preset Circuits List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(circuits, key = { it.id }) { item ->
                    Surface(
                        color = LabSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LabBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                onLoadCircuit(item.id)
                                onDismiss()
                            }
                            .testTag("library_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = if (item.isPreset) LabAmber.copy(alpha = 0.2f) else LabEmerald.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (item.isPreset) "PRESET" else "SAVED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (item.isPreset) LabAmber else LabEmerald,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (item.description.isNotBlank()) {
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${item.componentCount} components",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LabCyan
                                )
                            }
                            if (!item.isPreset) {
                                IconButton(onClick = { onDeleteCircuit(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Circuit", tint = LabRose)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJsonModal) {
        AlertDialog(
            onDismissRequest = { showJsonModal = false },
            title = { Text("Circuit JSON (Offline Export / Import)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        label = { Text("Schematic JSON") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                    jsonFeedback?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = LabAmber)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onImportJsonText(jsonInput) { ok, msg ->
                        jsonFeedback = msg
                        if (ok) {
                            showJsonModal = false
                            onDismiss()
                        }
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(exportedJson))
                        jsonFeedback = "Copied current circuit JSON to clipboard"
                    }) {
                        Text("Copy")
                    }
                    TextButton(onClick = { showJsonModal = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
