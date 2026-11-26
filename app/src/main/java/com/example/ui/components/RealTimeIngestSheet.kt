package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.sync.ImportedWorkbookBundle
import com.example.data.sync.IngestTarget
import com.example.ui.RealTimeIngestUiState
import com.example.ui.theme.OpsAmber
import com.example.ui.theme.OpsAmberDeep
import com.example.ui.theme.OpsBorder
import com.example.ui.theme.OpsConsole
import com.example.ui.theme.OpsCrimson
import com.example.ui.theme.OpsCrimsonSurface
import com.example.ui.theme.OpsCrimsonText
import com.example.ui.theme.OpsCyan
import com.example.ui.theme.OpsCyanDeep
import com.example.ui.theme.OpsEmerald
import com.example.ui.theme.OpsEmeraldDeep
import com.example.ui.theme.OpsObsidian
import com.example.ui.theme.OpsSlate
import com.example.ui.theme.OpsSteel
import com.example.ui.theme.OpsWhite
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealTimeIngestSheet(
    initialTarget: IngestTarget,
    ingestState: RealTimeIngestUiState,
    onPreviewFile: (Uri, IngestTarget) -> Unit,
    onSavePickedPhoto: suspend (Uri) -> String?,
    onExtractFromPhoto: (String, IngestTarget, String) -> Unit,
    onCreateOfflineDraft: (String, IngestTarget, String?) -> Unit,
    onCommitBundle: (ImportedWorkbookBundle) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedTarget by remember(initialTarget) { mutableStateOf(initialTarget) }
    var inputMode by remember { mutableIntStateOf(0) } // 0 = XLSX / CSV File, 1 = Photo / Note
    var photoPath by remember { mutableStateOf<String?>(null) }
    var noteHint by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onPreviewFile(it, selectedTarget) }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = onSavePickedPhoto(uri)
                if (saved != null) {
                    photoPath = saved
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onReset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = OpsSlate
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Live Add / Update Hub",
                style = MaterialTheme.typography.headlineSmall,
                color = OpsWhite
            )
            Text(
                text = "Add new rows or update existing records in real time from .xlsx, .csv, .json, or a photo.",
                style = MaterialTheme.typography.bodySmall,
                color = OpsSteel
            )

            // Target Workbook Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IngestTarget.entries.forEach { target ->
                    FilterChip(
                        selected = selectedTarget == target,
                        onClick = {
                            selectedTarget = target
                            onReset()
                        },
                        label = { Text(target.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = OpsConsole,
                            labelColor = OpsSteel,
                            selectedContainerColor = OpsCyanDeep,
                            selectedLabelColor = OpsCyan
                        ),
                        modifier = Modifier.testTag("ingest_target_${target.name.lowercase()}")
                    )
                }
            }

            // Source Mode Toggle (XLSX/CSV vs Photo)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = inputMode == 0,
                    onClick = { inputMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = OpsAmberDeep,
                        activeContentColor = OpsAmber,
                        inactiveContainerColor = OpsConsole,
                        inactiveContentColor = OpsSteel
                    ),
                    modifier = Modifier.testTag("ingest_mode_file")
                ) {
                    Text("XLSX / CSV File")
                }
                SegmentedButton(
                    selected = inputMode == 1,
                    onClick = { inputMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = OpsAmberDeep,
                        activeContentColor = OpsAmber,
                        inactiveContainerColor = OpsConsole,
                        inactiveContentColor = OpsSteel
                    ),
                    modifier = Modifier.testTag("ingest_mode_photo")
                ) {
                    Text("Photo / Note")
                }
            }

            if (inputMode == 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, tint = OpsCyan)
                            Text(
                                text = "Select Excel (.xlsx), CSV (.csv), or JSON",
                                style = MaterialTheme.typography.titleSmall,
                                color = OpsWhite
                            )
                        }
                        Text(
                            text = "Existing rows with matching SI (Keys), Date (Visitors), or SN (Sports/Maintenance) are updated in place; new rows are added immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )
                        Button(
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/json",
                                        "*/*"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_xlsx_csv_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose .xlsx / .csv File", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_universal_photo_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = OpsAmber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (photoPath == null) "Pick Register / Log Photo" else "Change Photo", color = OpsAmber)
                        }

                        photoPath?.let { path ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OpsObsidian)
                            ) {
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "Selected operations photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        OutlinedTextField(
                            value = noteHint,
                            onValueChange = { noteHint = it },
                            label = { Text("Note / WhatsApp snippet (optional or offline)") },
                            placeholder = { Text("e.g. SI 1644 M-C13-16 returned 26/11 17:10 or Padel M-A04-16 19:00") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ingest_note_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (photoPath != null) {
                                Button(
                                    onClick = { onExtractFromPhoto(photoPath!!, selectedTarget, noteHint) },
                                    enabled = ingestState !is RealTimeIngestUiState.Processing,
                                    colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("extract_universal_photo_button")
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Extract Photo", fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { onCreateOfflineDraft(noteHint, selectedTarget, photoPath) },
                                enabled = noteHint.isNotBlank() && ingestState !is RealTimeIngestUiState.Processing,
                                border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("draft_offline_note_button")
                            ) {
                                Text("Parse Note", color = OpsCyan)
                            }
                        }
                    }
                }
            }

            when (ingestState) {
                RealTimeIngestUiState.Idle -> Unit
                RealTimeIngestUiState.Processing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OpsCyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Parsing records for live update...", color = OpsWhite)
                    }
                }
                is RealTimeIngestUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpsCrimsonSurface),
                        border = BorderStroke(1.dp, OpsCrimson)
                    ) {
                        Text(
                            text = ingestState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsCrimsonText,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is RealTimeIngestUiState.PreviewReady -> {
                    val p = ingestState.preview
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpsEmeraldDeep.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, OpsEmerald.copy(alpha = 0.6f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OpsEmerald)
                                Text(
                                    text = "Ready: ${p.sourceLabel}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OpsWhite
                                )
                            }
                            if (p.bundle.keys.isNotEmpty()) {
                                Text(
                                    text = "• Key Register: ${p.newKeysCount} new, ${p.updatedKeysCount} update(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsWhite
                                )
                            }
                            if (p.visitorsUpsertCount > 0) {
                                Text("• PM Visitors: ${p.visitorsUpsertCount} day(s)", style = MaterialTheme.typography.bodySmall, color = OpsWhite)
                            }
                            if (p.maintenanceUpsertCount > 0) {
                                Text("• Maintenance Log: ${p.maintenanceUpsertCount} work order(s)", style = MaterialTheme.typography.bodySmall, color = OpsWhite)
                            }
                            if (p.sportsUpsertCount > 0) {
                                Text("• Padel & Football: ${p.sportsUpsertCount} booking(s)", style = MaterialTheme.typography.bodySmall, color = OpsWhite)
                            }
                            if (p.bundle.skippedTestRecords > 0) {
                                Text("• Filtered ${p.bundle.skippedTestRecords} deleted test entry(ies)", style = MaterialTheme.typography.bodySmall, color = OpsAmber)
                            }

                            Button(
                                onClick = {
                                    onCommitBundle(p.bundle)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OpsEmerald, contentColor = OpsObsidian),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("commit_realtime_update_button")
                            ) {
                                Text("Apply Real-Time Update (${p.totalRows} row${if (p.totalRows == 1) "" else "s"})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
