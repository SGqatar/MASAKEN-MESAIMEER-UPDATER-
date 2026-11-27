package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ai.InspectionPhotoDraft
import com.example.data.db.InspectionReportEntity
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.ReportDispatchEntity
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.example.data.sync.LocalArchiveEntry
import com.example.ui.PendingEmailDraft
import com.example.ui.components.OpsStatusPill
import com.example.ui.components.SpringButton
import com.example.ui.components.SpringOutlinedButton
import com.example.ui.components.StaggeredMotionReveal
import com.example.ui.theme.OpsAmber
import com.example.ui.theme.OpsAmberDeep
import com.example.ui.theme.OpsBorder
import com.example.ui.theme.OpsConsole
import com.example.ui.theme.OpsConsoleElevated
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
import com.example.ui.theme.OpsTabularFontFamily
import com.example.ui.theme.OpsWhite
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    keys: List<KeyRegisterEntity>,
    visitors: List<VisitorDayEntity>,
    maintenance: List<MaintenanceRequestEntity>,
    sports: List<SportsBookingEntity>,
    inspectionDrafts: List<InspectionPhotoDraft>,
    inspectionReports: List<InspectionReportEntity>,
    reportDispatches: List<ReportDispatchEntity>,
    localArchives: List<LocalArchiveEntry>,
    isAnalyzingInspection: Boolean,
    isGeneratingReport: Boolean,
    pendingEmailDraft: PendingEmailDraft?,
    onAddInspectionPhotos: (List<Uri>, String) -> Unit,
    onAddManualInspectionRow: (String, String, String, String) -> Unit,
    onUpdateInspectionDraft: (InspectionPhotoDraft) -> Unit,
    onRemoveInspectionDraft: (String) -> Unit,
    onClearInspectionDrafts: () -> Unit,
    onAnalyzeInspectionPhotos: (String, String) -> Unit,
    onGenerateInspectionXlsx: (String, String, String, Boolean) -> Unit,
    onPrepareWeeklyReport: (Int, String, String, String, String) -> Unit,
    onPrepareMonthlyReport: (String, String) -> Unit,
    onOpenBacklogXlsx: (String) -> Unit,
    onOpenArchiveEmailComposer: (LocalArchiveEntry) -> Unit,
    onSendDraftViaGmailApi: (Activity, PendingEmailDraft, String, String, String, String, Boolean) -> Unit,
    onDispatchViaMailApp: (PendingEmailDraft, String, String, String, String) -> Unit,
    onUploadDraftToDriveOnly: (Activity, PendingEmailDraft) -> Unit,
    onDismissEmailDraft: () -> Unit,
    onBackupPhoneAndDrive: (Activity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Interactive 3-file backlog preview target ("SPORTS" | "KEYS" | "VISITORS_MAINT" | null)
    var activeBacklogPreview by remember { mutableStateOf<String?>(null) }

    // Building inspection inputs
    var buildingName by remember { mutableStateOf("Building A04 (Masaken Mesaimeer)") }
    var inspectionDate by remember { mutableStateOf("27/11/2026") }
    var inspectorName by remember { mutableStateOf("Emmanuel Ssali") }
    var defaultArea by remember { mutableStateOf("Corridor") }
    var aiHint by remember { mutableStateOf("") }
    var showAddManualDialog by remember { mutableStateOf(false) }

    // Weekly period state (defaults to Nov 20 - Nov 26, Week 48)
    var weekNumberText by remember { mutableStateOf("48") }
    var weekStartLabel by remember { mutableStateOf("20 Nov") }
    var weekEndLabel by remember { mutableStateOf("26 Nov") }
    var weekStartIso by remember { mutableStateOf("2026-11-20") }
    var weekEndIso by remember { mutableStateOf("2026-11-26") }

    // Monthly period state
    var selectedMonthKey by remember { mutableStateOf("2026-11") }
    var selectedMonthLabel by remember { mutableStateOf("November 2026") }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddInspectionPhotos(uris, defaultArea)
        }
    }

    val weekVisitors = remember(visitors, weekStartIso, weekEndIso) {
        visitors.filter { it.dateIso in weekStartIso..weekEndIso }
    }
    val weekVisCount = remember(weekVisitors) { weekVisitors.sumOf { it.visitorCount } }
    val weekViewCount = remember(weekVisitors) { weekVisitors.sumOf { it.viewingCount } }

    val monthVisitors = remember(visitors, selectedMonthKey) {
        visitors.filter { it.monthKey == selectedMonthKey }
    }
    val monthVisCount = remember(monthVisitors) { monthVisitors.sumOf { it.visitorCount } }
    val monthViewCount = remember(monthVisitors) { monthVisitors.sumOf { it.viewingCount } }
    val fbCount = remember(sports) { sports.count { it.sport.equals("FOOTBALL", ignoreCase = true) } }
    val pdCount = remember(sports) { sports.count { it.sport.equals("PADEL", ignoreCase = true) } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpsObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            StaggeredMotionReveal(delayMillis = 0) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reports & Inspections",
                                style = MaterialTheme.typography.headlineMedium,
                                color = OpsWhite
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Photo-embedded Building XLSX, Saturday Weekly & 1st-of-Month Excel dispatches",
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsSteel
                            )
                        }
                        OpsStatusPill(
                            label = "XLSX + DRIVE",
                            containerColor = OpsCyanDeep,
                            contentColor = OpsCyan
                        )
                    }
                }
            }
        }

        // Interactive 3-File Workbook Backlogs Explorer
        item {
            StaggeredMotionReveal(delayMillis = 20) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag("backlog_files_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.TableView, contentDescription = null, tint = OpsCyan)
                                Text(
                                    text = "Workbook Files & Backlogs",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OpsWhite
                                )
                            }
                            OpsStatusPill(
                                label = "3 FILES",
                                containerColor = OpsCyanDeep,
                                contentColor = OpsCyan
                            )
                        }

                        Text(
                            text = "Preview full historical backlogs in-app or open standalone .xlsx files anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )

                        BacklogFileItemRow(
                            icon = Icons.Filled.SportsSoccer,
                            accentColor = OpsEmerald,
                            title = "1. Padel & Football",
                            fileName = "MM-PADEL-AND-FOOTBALL-COURT-NOVEMBER-2026.xlsx",
                            summary = "${sports.size} bookings ($fbCount Football / $pdCount Padel)",
                            previewTag = "preview_backlog_sports",
                            openTag = "open_xlsx_sports",
                            isBusy = isGeneratingReport,
                            onPreview = { activeBacklogPreview = "SPORTS" },
                            onOpenXlsx = { onOpenBacklogXlsx("SPORTS") }
                        )

                        BacklogFileItemRow(
                            icon = Icons.Filled.Key,
                            accentColor = OpsAmber,
                            title = "2. MM Keys Register",
                            fileName = "WSF-PMD-F-07-MM-KEYS-REGISTER-BACKLOG.xlsx",
                            summary = "${keys.size} rows • ${keys.count { it.isUnreturnedAlert }} red, ${keys.count { it.isMoveInPendingTCode }} move-in",
                            previewTag = "preview_backlog_keys",
                            openTag = "open_xlsx_keys",
                            isBusy = isGeneratingReport,
                            onPreview = { activeBacklogPreview = "KEYS" },
                            onOpenXlsx = { onOpenBacklogXlsx("KEYS") }
                        )

                        BacklogFileItemRow(
                            icon = Icons.Filled.Groups,
                            accentColor = OpsCyan,
                            title = "3. Visitors & Maintenance",
                            fileName = "PM-VISITORS-2026-AND-MAINTENANCE-BACKLOG.xlsx",
                            summary = "${visitors.size} visitor days • ${maintenance.size} maintenance requests",
                            previewTag = "preview_backlog_visitors_maint",
                            openTag = "open_xlsx_visitors_maint",
                            isBusy = isGeneratingReport,
                            onPreview = { activeBacklogPreview = "VISITORS_MAINT" },
                            onOpenXlsx = { onOpenBacklogXlsx("VISITORS_MAINT") }
                        )
                    }
                }
            }
        }

        // 1. Building Inspection XLSX Card (with Embedded Photos Inside Rows by Area)
        item {
            StaggeredMotionReveal(delayMillis = 40) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.42f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.HomeWork, contentDescription = null, tint = OpsCyan)
                                Text(
                                    text = "Building Inspection (.xlsx)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OpsWhite
                                )
                            }
                            OpsStatusPill(
                                label = "PHOTOS IN ROWS",
                                containerColor = OpsEmeraldDeep,
                                contentColor = OpsEmerald
                            )
                        }

                        Text(
                            text = "Attach building photos, organize them by area with AI or manual edits, and embed thumbnails directly inside Excel rows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )

                        OutlinedTextField(
                            value = buildingName,
                            onValueChange = { buildingName = it },
                            label = { Text("Building / Block Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inspection_building_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = inspectionDate,
                                onValueChange = { inspectionDate = it },
                                label = { Text("Date") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inspectorName,
                                onValueChange = { inspectorName = it },
                                label = { Text("Inspector") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = defaultArea,
                                onValueChange = { defaultArea = it },
                                label = { Text("Default Area") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = aiHint,
                                onValueChange = { aiHint = it },
                                label = { Text("AI Note (optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpringOutlinedButton(
                                onClick = {
                                    multiPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("pick_inspection_photos_button")
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = OpsCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Photos", color = OpsCyan)
                            }
                            SpringOutlinedButton(
                                onClick = { showAddManualDialog = true },
                                border = BorderStroke(1.dp, OpsBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("add_manual_inspection_row_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = OpsSteel, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Manual Row", color = OpsWhite)
                            }
                        }

                        if (inspectionDrafts.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${inspectionDrafts.size} item(s) • Grouped by Area",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OpsAmber
                                )
                                TextButton(onClick = onClearInspectionDrafts) {
                                    Text("Clear All", color = OpsSteel)
                                }
                            }

                            SpringButton(
                                onClick = { onAnalyzeInspectionPhotos(buildingName, aiHint) },
                                enabled = !isAnalyzingInspection && inspectionDrafts.any { it.photoPath.isNotBlank() },
                                colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_organize_inspection_button")
                            ) {
                                if (isAnalyzingInspection) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = OpsObsidian, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Organizing with Gemini Vision...")
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI Organize Photos by Area (Gemini)", fontWeight = FontWeight.Bold)
                                }
                            }

                            inspectionDrafts.forEachIndexed { idx, draft ->
                                InspectionDraftEditorCard(
                                    index = idx + 1,
                                    draft = draft,
                                    onUpdate = onUpdateInspectionDraft,
                                    onRemove = { onRemoveInspectionDraft(draft.id) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SpringOutlinedButton(
                                    onClick = { onGenerateInspectionXlsx(buildingName, inspectionDate, inspectorName, false) },
                                    enabled = !isGeneratingReport,
                                    border = BorderStroke(1.dp, OpsEmerald.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("export_inspection_xlsx_only_button")
                                ) {
                                    Icon(Icons.Filled.TableView, contentDescription = null, tint = OpsEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save .xlsx", color = OpsEmerald)
                                }
                                SpringButton(
                                    onClick = { onGenerateInspectionXlsx(buildingName, inspectionDate, inspectorName, true) },
                                    enabled = !isGeneratingReport,
                                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("export_and_email_inspection_button")
                                ) {
                                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("XLSX & Email", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Weekly Key Log & Visitors Report Card
        item {
            StaggeredMotionReveal(delayMillis = 80) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.42f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Assessment, contentDescription = null, tint = OpsAmber)
                                Text("Weekly Keys Log Report", style = MaterialTheme.typography.titleMedium, color = OpsWhite)
                            }
                            OpsStatusPill(label = "SAT 16:00", containerColor = OpsAmberDeep, contentColor = OpsAmber)
                        }

                        Text(
                            text = "Every Saturday: generates WSF-PMD-F-09 Weekly Excel + visitor & vacant flat viewing summary. Always prompts To & CC before sending.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = weekNumberText,
                                onValueChange = { weekNumberText = it },
                                label = { Text("Week #") },
                                singleLine = true,
                                modifier = Modifier.weight(0.65f)
                            )
                            OutlinedTextField(
                                value = weekStartLabel,
                                onValueChange = { weekStartLabel = it },
                                label = { Text("Start") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = weekEndLabel,
                                onValueChange = { weekEndLabel = it },
                                label = { Text("End") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = weekStartIso == "2026-11-20",
                                onClick = {
                                    weekNumberText = "48"
                                    weekStartLabel = "20 Nov"
                                    weekEndLabel = "26 Nov"
                                    weekStartIso = "2026-11-20"
                                    weekEndIso = "2026-11-26"
                                },
                                label = { Text("Week 48 (20–26 Nov)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = OpsConsoleElevated,
                                    labelColor = OpsSteel,
                                    selectedContainerColor = OpsAmberDeep,
                                    selectedLabelColor = OpsAmber
                                )
                            )
                            FilterChip(
                                selected = weekStartIso == "2026-11-13",
                                onClick = {
                                    weekNumberText = "47"
                                    weekStartLabel = "13 Nov"
                                    weekEndLabel = "19 Nov"
                                    weekStartIso = "2026-11-13"
                                    weekEndIso = "2026-11-19"
                                },
                                label = { Text("Week 47 (13–19 Nov)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = OpsConsoleElevated,
                                    labelColor = OpsSteel,
                                    selectedContainerColor = OpsAmberDeep,
                                    selectedLabelColor = OpsAmber
                                )
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = OpsConsoleElevated),
                            border = BorderStroke(1.dp, OpsBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("VISITORS IN PERIOD", style = MaterialTheme.typography.labelSmall, color = OpsSteel)
                                    Text(
                                        text = "$weekVisCount visitors",
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = OpsTabularFontFamily),
                                        color = OpsCyan
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("VACANT FLAT VIEWINGS", style = MaterialTheme.typography.labelSmall, color = OpsSteel)
                                    Text(
                                        text = "$weekViewCount viewings",
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = OpsTabularFontFamily),
                                        color = OpsEmerald
                                    )
                                }
                            }
                        }

                        SpringButton(
                            onClick = {
                                onPrepareWeeklyReport(
                                    weekNumberText.toIntOrNull() ?: 48,
                                    weekStartLabel.trim(),
                                    weekEndLabel.trim(),
                                    weekStartIso,
                                    weekEndIso
                                )
                            },
                            enabled = !isGeneratingReport,
                            colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("generate_weekly_report_button")
                        ) {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Weekly .xlsx & Prompt To/CC", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. End-of-Month Combined Report Card (Keys + PM Visitors + Padel & Football)
        item {
            StaggeredMotionReveal(delayMillis = 120) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsEmerald.copy(alpha = 0.42f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = OpsEmerald)
                                Text("Monthly Report Bundle (1st of Month)", style = MaterialTheme.typography.titleMedium, color = OpsWhite)
                            }
                            OpsStatusPill(label = "1ST • 3 XLSX", containerColor = OpsEmeraldDeep, contentColor = OpsEmerald)
                        }

                        Text(
                            text = "Prepared on the 1st day of each month: bundles WSF-PMD-F-09 Keys, PM Visitors Log Jan–Dec, and Monthly Football & Padel Court Excel reports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("2026-11" to "November 2026", "2026-10" to "October 2026", "2026-09" to "September 2026").forEach { (mk, ml) ->
                                FilterChip(
                                    selected = selectedMonthKey == mk,
                                    onClick = {
                                        selectedMonthKey = mk
                                        selectedMonthLabel = ml
                                    },
                                    label = { Text(ml.substringBefore(" ")) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = OpsConsoleElevated,
                                        labelColor = OpsSteel,
                                        selectedContainerColor = OpsEmeraldDeep,
                                        selectedLabelColor = OpsEmerald
                                    )
                                )
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = OpsConsoleElevated),
                            border = BorderStroke(1.dp, OpsBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "$selectedMonthLabel Summary",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OpsWhite
                                )
                                Text(
                                    text = "Visitors: $monthVisCount  •  Viewings: $monthViewCount  •  Keys: ${keys.size} rows",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
                                    color = OpsSteel
                                )
                                Text(
                                    text = "Sports: ${sports.size} total ($fbCount Football / $pdCount Padel)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
                                    color = OpsCyan
                                )
                            }
                        }

                        SpringButton(
                            onClick = { onPrepareMonthlyReport(selectedMonthKey, selectedMonthLabel) },
                            enabled = !isGeneratingReport,
                            colors = ButtonDefaults.buttonColors(containerColor = OpsEmerald, contentColor = OpsObsidian),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("generate_monthly_report_button")
                        ) {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Monthly Bundle & Prompt To/CC", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Local Folder & Google Drive Archive + Dispatch History
        item {
            StaggeredMotionReveal(delayMillis = 160) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.FolderCopy, contentDescription = null, tint = OpsCyan)
                                Text("Device & Google Drive Archive", style = MaterialTheme.typography.titleMedium, color = OpsWhite)
                            }
                            OpsStatusPill(
                                label = "${localArchives.size} FILES",
                                containerColor = OpsConsoleElevated,
                                contentColor = OpsSteel
                            )
                        }

                        Text(
                            text = "Local path: Documents/Masaken Mesaimeer/\nGoogle Drive: Masaken Mesaimeer Ops/",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )

                        SpringOutlinedButton(
                            onClick = { activity?.let(onBackupPhoneAndDrive) },
                            enabled = activity != null && !isGeneratingReport,
                            border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("backup_phone_and_drive_button")
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, tint = OpsCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save Full JSON Backup to Phone & Drive", color = OpsCyan)
                        }

                        if (localArchives.isNotEmpty()) {
                            Text("Recent Generated Workbooks", style = MaterialTheme.typography.labelLarge, color = OpsAmber)
                            localArchives.take(6).forEach { entry ->
                                val timeText = SimpleDateFormat("dd-MMM HH:mm", Locale.US).format(Date(entry.modifiedAtMillis))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = OpsConsoleElevated),
                                    border = BorderStroke(1.dp, OpsBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.fileName, style = MaterialTheme.typography.bodySmall, color = OpsWhite)
                                            Text(
                                                text = "${entry.subfolderName} • ${(entry.sizeBytes / 1024).coerceAtLeast(1)} KB • $timeText",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OpsSteel
                                            )
                                        }
                                        TextButton(
                                            onClick = { onOpenArchiveEmailComposer(entry) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Send", color = OpsCyan)
                                        }
                                    }
                                }
                            }
                        }

                        if (inspectionReports.isNotEmpty()) {
                            Text("Recent Inspection History (${inspectionReports.size})", style = MaterialTheme.typography.labelLarge, color = OpsEmerald)
                            inspectionReports.take(4).forEach { insp ->
                                Text(
                                    text = "• ${insp.buildingName} (${insp.inspectionDate}) — ${insp.itemCount} items${if (insp.driveFileId.isNotBlank()) " • Drive synced" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsSteel
                                )
                            }
                        }

                        if (reportDispatches.isNotEmpty()) {
                            Text("Recent Dispatches (${reportDispatches.size})", style = MaterialTheme.typography.labelLarge, color = OpsCyan)
                            reportDispatches.take(5).forEach { disp ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = OpsSlate),
                                    border = BorderStroke(1.dp, OpsBorder)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(disp.subject, style = MaterialTheme.typography.labelMedium, color = OpsWhite)
                                        Text(
                                            text = "To: ${disp.recipientsTo} • ${disp.dispatchChannel} • ${disp.driveStatus}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OpsSteel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }

    if (showAddManualDialog) {
        var area by remember { mutableStateOf(defaultArea) }
        var finding by remember { mutableStateOf("") }
        var severity by remember { mutableStateOf("Minor") }
        var action by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddManualDialog = false },
            containerColor = OpsSlate,
            titleContentColor = OpsWhite,
            textContentColor = OpsSteel,
            title = { Text("Add Manual Inspection Row") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Area / Zone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = finding, onValueChange = { finding = it }, label = { Text("Finding") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Pass", "Minor", "Urgent").forEach { sev ->
                            FilterChip(
                                selected = severity == sev,
                                onClick = { severity = sev },
                                label = { Text(sev) }
                            )
                        }
                    }
                    OutlinedTextField(value = action, onValueChange = { action = it }, label = { Text("Action Required") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                SpringButton(
                    onClick = {
                        onAddManualInspectionRow(area, finding, severity, action)
                        showAddManualDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian)
                ) {
                    Text("Add Row")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualDialog = false }) { Text("Cancel", color = OpsSteel) }
            }
        )
    }

    pendingEmailDraft?.let { draft ->
        PreSendEmailComposerSheet(
            draft = draft,
            isSending = isGeneratingReport,
            onDismiss = onDismissEmailDraft,
            onSendGmailApi = { to, cc, subject, body, uploadDrive ->
                activity?.let { act ->
                    onSendDraftViaGmailApi(act, draft, to, cc, subject, body, uploadDrive)
                }
            },
            onSendMailApp = { to, cc, subject, body ->
                onDispatchViaMailApp(draft, to, cc, subject, body)
            },
            onUploadDriveOnly = {
                activity?.let { act ->
                    onUploadDraftToDriveOnly(act, draft)
                }
            }
        )
    }

    activeBacklogPreview?.let { type ->
        BacklogPreviewBottomSheet(
            backlogType = type,
            keys = keys,
            visitors = visitors,
            maintenance = maintenance,
            sports = sports,
            onOpenXlsx = { onOpenBacklogXlsx(type) },
            onDismiss = { activeBacklogPreview = null }
        )
    }
}

@Composable
private fun InspectionDraftEditorCard(
    index: Int,
    draft: InspectionPhotoDraft,
    onUpdate: (InspectionPhotoDraft) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsConsoleElevated),
        border = BorderStroke(1.dp, OpsBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Item #$index", style = MaterialTheme.typography.labelLarge, color = OpsCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Pass", "Minor", "Urgent").forEach { sev ->
                        val selected = draft.severity.equals(sev, true)
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdate(draft.copy(severity = sev)) },
                            label = { Text(sev) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = OpsConsole,
                                labelColor = OpsSteel,
                                selectedContainerColor = when (sev) {
                                    "Urgent" -> OpsCrimsonSurface
                                    "Minor" -> OpsAmberDeep
                                    else -> OpsEmeraldDeep
                                },
                                selectedLabelColor = when (sev) {
                                    "Urgent" -> OpsCrimsonText
                                    "Minor" -> OpsAmber
                                    else -> OpsEmerald
                                }
                            )
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = OpsCrimson)
                    }
                }
            }

            if (draft.photoPath.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OpsObsidian)
                ) {
                    AsyncImage(
                        model = File(draft.photoPath),
                        contentDescription = "Inspection item $index photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            OutlinedTextField(
                value = draft.area,
                onValueChange = { onUpdate(draft.copy(area = it)) },
                label = { Text("Area / Zone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.finding,
                onValueChange = { onUpdate(draft.copy(finding = it)) },
                label = { Text("Inspection Finding") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.actionRequired,
                onValueChange = { onUpdate(draft.copy(actionRequired = it)) },
                label = { Text("Action Required") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreSendEmailComposerSheet(
    draft: PendingEmailDraft,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSendGmailApi: (String, String, String, String, Boolean) -> Unit,
    onSendMailApp: (String, String, String, String) -> Unit,
    onUploadDriveOnly: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // CRITICAL: Never pre-fill To or CC — always prompt the user every time
    var toInput by remember(draft) { mutableStateOf("") }
    var ccInput by remember(draft) { mutableStateOf("") }
    var subject by remember(draft) { mutableStateOf(draft.defaultSubject) }
    var bodyText by remember(draft) { mutableStateOf(draft.defaultBody) }
    var alsoUploadDrive by remember(draft) { mutableStateOf(true) }

    val hasValidTo = remember(toInput) {
        toInput.split(',', ';', '\n').any { it.trim().contains('@') && it.trim().contains('.') }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OpsSlate
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compose & Send Report Email", style = MaterialTheme.typography.titleLarge, color = OpsWhite)
                OpsStatusPill(label = draft.reportType, containerColor = OpsCyanDeep, contentColor = OpsCyan)
            }

            Text(
                text = "Enter To and CC email addresses for this send (kept blank by policy). Attachments saved to phone & ready to send.",
                style = MaterialTheme.typography.bodySmall,
                color = OpsAmber
            )

            OutlinedTextField(
                value = toInput,
                onValueChange = { toInput = it },
                label = { Text("To (required — comma-separated emails)") },
                placeholder = { Text("example1@domain.com, example2@domain.com") },
                isError = toInput.isNotBlank() && !hasValidTo,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_to_input")
            )
            OutlinedTextField(
                value = ccInput,
                onValueChange = { ccInput = it },
                label = { Text("CC (optional — comma-separated emails)") },
                placeholder = { Text("cc-team@domain.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_cc_input")
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_subject_input")
            )
            OutlinedTextField(
                value = bodyText,
                onValueChange = { bodyText = it },
                label = { Text("Message Body & Signature") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Attachments (${draft.attachments.size})", style = MaterialTheme.typography.labelLarge, color = OpsCyan)
            draft.attachments.forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, tint = OpsEmerald, modifier = Modifier.size(16.dp))
                    Text(
                        text = "${file.fileName} (${(file.sizeBytes / 1024).coerceAtLeast(1)} KB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpsWhite
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = alsoUploadDrive,
                    onCheckedChange = { alsoUploadDrive = it },
                    colors = CheckboxDefaults.colors(checkedColor = OpsCyan, checkmarkColor = OpsObsidian)
                )
                Text(
                    text = "Also upload attachments to Google Drive (Masaken Mesaimeer Ops)",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsWhite
                )
            }

            SpringButton(
                onClick = { onSendGmailApi(toInput, ccInput, subject, bodyText, alsoUploadDrive) },
                enabled = hasValidTo && subject.isNotBlank() && !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("send_gmail_api_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = OpsObsidian, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Dispatching...")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Directly (Gmail API)", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpringOutlinedButton(
                    onClick = { onSendMailApp(toInput, ccInput, subject, bodyText) },
                    enabled = hasValidTo && subject.isNotBlank() && !isSending,
                    border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_mail_app_button")
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, tint = OpsAmber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mail App", color = OpsAmber)
                }
                SpringOutlinedButton(
                    onClick = onUploadDriveOnly,
                    enabled = !isSending,
                    border = BorderStroke(1.dp, OpsEmerald.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("upload_drive_only_button")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = OpsEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Drive Only", color = OpsEmerald)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BacklogFileItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    title: String,
    fileName: String,
    summary: String,
    previewTag: String,
    openTag: String,
    isBusy: Boolean,
    onPreview: () -> Unit,
    onOpenXlsx: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsConsoleElevated),
        border = BorderStroke(1.dp, OpsBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = OpsWhite)
                    Text(fileName, style = MaterialTheme.typography.labelSmall, color = OpsSteel)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
                        color = accentColor
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpringOutlinedButton(
                    onClick = onPreview,
                    border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(previewTag)
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = OpsCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Preview", color = OpsCyan)
                }
                SpringButton(
                    onClick = onOpenXlsx,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = OpsObsidian),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(openTag)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open .xlsx", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BacklogPreviewBottomSheet(
    backlogType: String,
    keys: List<KeyRegisterEntity>,
    visitors: List<VisitorDayEntity>,
    maintenance: List<MaintenanceRequestEntity>,
    sports: List<SportsBookingEntity>,
    onOpenXlsx: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember(backlogType) { mutableStateOf("") }
    var visitorsTab by remember(backlogType) { mutableStateOf("VISITORS") }

    val title = when (backlogType) {
        "SPORTS" -> "Padel & Football Backlog"
        "KEYS" -> "MM Keys Register Backlog"
        else -> "Visitors & Maintenance Backlog"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OpsSlate
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = OpsWhite)
                    Text(
                        text = when (backlogType) {
                            "SPORTS" -> "${sports.size} bookings logged"
                            "KEYS" -> "${keys.size} register rows"
                            else -> "${visitors.size} days & ${maintenance.size} maintenance requests"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OpsSteel
                    )
                }
                SpringButton(
                    onClick = onOpenXlsx,
                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                    modifier = Modifier.testTag("sheet_open_xlsx_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open .xlsx", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close backlog preview", tint = OpsSteel)
                }
            }

            if (backlogType == "VISITORS_MAINT") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = visitorsTab == "VISITORS",
                        onClick = { visitorsTab = "VISITORS" },
                        label = { Text("PM Visitors 2026 (${visitors.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = OpsConsole,
                            labelColor = OpsSteel,
                            selectedContainerColor = OpsCyanDeep,
                            selectedLabelColor = OpsCyan
                        )
                    )
                    FilterChip(
                        selected = visitorsTab == "MAINTENANCE",
                        onClick = { visitorsTab = "MAINTENANCE" },
                        label = { Text("Maintenance (${maintenance.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = OpsConsole,
                            labelColor = OpsSteel,
                            selectedContainerColor = OpsAmberDeep,
                            selectedLabelColor = OpsAmber
                        )
                    )
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search unit, name, date, SI/SN, or status...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = OpsSteel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backlog_preview_search")
            )

            val q = search.trim().lowercase(Locale.ROOT)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when (backlogType) {
                    "SPORTS" -> {
                        val rows = sports.sortedByDescending { it.sn }.filter { s ->
                            q.isBlank() ||
                                s.name.lowercase(Locale.ROOT).contains(q) ||
                                s.unitDetails.lowercase(Locale.ROOT).contains(q) ||
                                s.sport.lowercase(Locale.ROOT).contains(q) ||
                                s.date.lowercase(Locale.ROOT).contains(q) ||
                                s.sn.toString() == q
                        }
                        items(rows, key = { it.sn }) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = OpsConsole),
                                border = BorderStroke(1.dp, OpsBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "SN ${item.sn} • ${item.sport} (${item.unitDetails})",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = OpsTabularFontFamily),
                                            color = OpsWhite
                                        )
                                        Text(
                                            text = "${item.durationPlayed} hr",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = OpsEmerald
                                        )
                                    }
                                    Text(
                                        text = "${item.date} • ${item.time} • ${item.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OpsSteel
                                    )
                                    if (item.remarks.isNotBlank() || item.referenceNo.isNotBlank()) {
                                        Text(
                                            text = "Ref: ${item.referenceNo.ifBlank { "-" }} • ${item.remarks.ifBlank { item.discount }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OpsCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "KEYS" -> {
                        val rows = keys.sortedByDescending { it.si }.filter { k ->
                            q.isBlank() ||
                                k.si.toString().contains(q) ||
                                k.flatUnitLocation.lowercase(Locale.ROOT).contains(q) ||
                                k.issuedTo.lowercase(Locale.ROOT).contains(q) ||
                                k.status.lowercase(Locale.ROOT).contains(q) ||
                                k.dateOut.lowercase(Locale.ROOT).contains(q)
                        }
                        items(rows, key = { it.si }) { item ->
                            val container = when {
                                item.isUnreturnedAlert -> OpsCrimsonSurface
                                item.isMoveInPendingTCode -> OpsAmberDeep
                                else -> OpsConsole
                            }
                            val accent = when {
                                item.isUnreturnedAlert -> OpsCrimsonText
                                item.isMoveInPendingTCode -> OpsAmber
                                else -> OpsEmerald
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = container),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "SI ${item.si} • ${item.flatUnitLocation}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = OpsTabularFontFamily),
                                            color = OpsWhite
                                        )
                                        Text(
                                            text = item.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent
                                        )
                                    }
                                    Text(
                                        text = "Out: ${item.dateOut} ${item.timeOut} • ${item.issuedTo} (${item.purpose})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OpsSteel
                                    )
                                    if (item.dateIn.isNotBlank() || item.returnedBy.isNotBlank() || item.remarks.isNotBlank()) {
                                        Text(
                                            text = "In: ${item.dateIn} ${item.timeIn} ${item.returnedBy} ${if (item.remarks.isNotBlank()) "• ${item.remarks}" else ""}".trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        if (visitorsTab == "VISITORS") {
                            val rows = visitors.sortedByDescending { it.dateIso }.filter { v ->
                                q.isBlank() ||
                                    v.displayDate.lowercase(Locale.ROOT).contains(q) ||
                                    v.flatDetails.lowercase(Locale.ROOT).contains(q) ||
                                    v.notes.lowercase(Locale.ROOT).contains(q)
                            }
                            items(rows, key = { it.dateIso }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                                    border = BorderStroke(1.dp, OpsBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.displayDate,
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = OpsTabularFontFamily),
                                                color = OpsWhite
                                            )
                                            if (item.flatDetails.isNotBlank() && item.flatDetails != "-") {
                                                Text(
                                                    text = "Flats: ${item.flatDetails}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = OpsSteel
                                                )
                                            }
                                            if (item.notes.isNotBlank()) {
                                                Text(
                                                    text = item.notes,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = OpsAmber
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${item.visitorCount} visitors",
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = OpsTabularFontFamily),
                                                color = OpsCyan
                                            )
                                            Text(
                                                text = "${item.viewingCount} viewings",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = OpsTabularFontFamily),
                                                color = OpsEmerald
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val rows = maintenance.sortedByDescending { it.sn }.filter { m ->
                                q.isBlank() ||
                                    m.sn.toString() == q ||
                                    m.names.lowercase(Locale.ROOT).contains(q) ||
                                    m.flatDetails.lowercase(Locale.ROOT).contains(q) ||
                                    m.workOrderNumber.lowercase(Locale.ROOT).contains(q) ||
                                    m.requestDetails.lowercase(Locale.ROOT).contains(q)
                            }
                            items(rows, key = { it.sn }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                                    border = BorderStroke(1.dp, OpsBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "SN ${item.sn} • ${item.flatDetails}",
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = OpsTabularFontFamily),
                                                color = OpsWhite
                                            )
                                            Text(
                                                text = item.date,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OpsAmber
                                            )
                                        }
                                        Text(
                                            text = "${item.names} (${item.phoneNumber}) • WO: ${item.workOrderNumber.ifBlank { "-" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OpsSteel
                                        )
                                        Text(
                                            text = item.requestDetails,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OpsCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

