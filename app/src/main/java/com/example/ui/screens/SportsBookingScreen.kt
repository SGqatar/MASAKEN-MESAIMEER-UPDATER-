package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ai.ParsedBookingDraft
import com.example.data.db.SportsBookingEntity
import com.example.ui.AiVisionUiState
import com.example.ui.SportFilter
import com.example.ui.components.AnimatedDecimalCounter
import com.example.ui.components.AnimatedIntCounter
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.OpsStatusPill
import com.example.ui.components.SpringButton
import com.example.ui.components.SpringFloatingActionButton
import com.example.ui.components.SpringListItemContainer
import com.example.ui.components.SpringOutlinedButton
import com.example.ui.components.springPressScale
import com.example.ui.theme.OpsTabularFontFamily
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
import java.util.Locale

@Composable
fun SportsBookingScreen(
    allBookings: List<SportsBookingEntity>,
    filteredBookings: List<SportsBookingEntity>,
    selectedFilter: SportFilter,
    selectedDateFilter: String,
    searchQuery: String,
    aiVisionState: AiVisionUiState,
    onSelectFilter: (SportFilter) -> Unit,
    onSelectDateFilter: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onParseTextOffline: (String, String?) -> ParsedBookingDraft,
    onSavePickedPhoto: suspend (android.net.Uri) -> String?,
    onExtractWithGeminiVision: (String, String) -> Unit,
    onResetAiVision: () -> Unit,
    onSaveBooking: (SportsBookingEntity) -> Unit,
    onDeleteBooking: (Int) -> Unit,
    onOpenLiveIngest: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingBooking by remember { mutableStateOf<SportsBookingEntity?>(null) }
    var showQuickParserSheet by remember { mutableStateOf(false) }

    val footballList = remember(allBookings) { allBookings.filter { it.sport.equals("FOOTBALL", true) } }
    val padelList = remember(allBookings) { allBookings.filter { it.sport.equals("PADEL", true) } }
    val footballHours = remember(footballList) { footballList.sumOf { it.durationPlayed } }
    val padelHours = remember(padelList) { padelList.sumOf { it.durationPlayed } }
    val activeHours = remember(filteredBookings) { filteredBookings.sumOf { it.durationPlayed } }
    val hasNov26 = remember(allBookings) { allBookings.any { it.date.startsWith("26/11") } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OpsObsidian,
        floatingActionButton = {
            SpringFloatingActionButton(
                onClick = {
                    onResetAiVision()
                    showQuickParserSheet = true
                },
                containerColor = OpsAmber,
                contentColor = OpsObsidian,
                modifier = Modifier.testTag("fab_quick_parse_sport")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Quick Parse & Log Booking")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Worksheet Header matching "November 2026"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Padel & Football Log",
                            style = MaterialTheme.typography.headlineMedium,
                            color = OpsWhite
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OpsStatusPill(
                                label = "NOVEMBER 2026 SHEET",
                                containerColor = OpsAmberDeep,
                                contentColor = OpsAmber
                            )
                            OpsStatusPill(
                                label = "ALL FREE",
                                containerColor = OpsEmeraldDeep,
                                contentColor = OpsEmerald
                            )
                        }
                    }
                    Row {
                        IconButton(
                            onClick = onOpenLiveIngest,
                            modifier = Modifier.testTag("sports_live_ingest_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Live Add or Update via XLSX, CSV, or Photo", tint = OpsCyan)
                        }
                        IconButton(
                            onClick = onExportCsv,
                            modifier = Modifier.testTag("export_sports_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Export Padel & Football CSV", tint = OpsAmber)
                        }
                    }
                }
            }

            // Live Animated Roll-Up Bar (Single-Page Combined Totals)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.4f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("SHOWING (${selectedFilter.label.uppercase()})", style = MaterialTheme.typography.labelSmall, color = OpsSteel)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AnimatedIntCounter(
                                        targetValue = filteredBookings.size,
                                        color = OpsWhite,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    AnimatedDecimalCounter(
                                        targetValue = activeHours,
                                        suffix = " hrs",
                                        color = OpsAmber,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Football: ${footballList.size} (${String.format(Locale.US, "%.1fh", footballHours)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OpsCyan
                                )
                                Text(
                                    text = "Padel: ${padelList.size} (${String.format(Locale.US, "%.1fh", padelHours)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OpsEmerald
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hasNov26) OpsEmeraldDeep else OpsAmberDeep)
                                .clickable {
                                    onResetAiVision()
                                    showQuickParserSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                LivePulseBeacon(color = if (hasNov26) OpsEmerald else OpsAmber, size = 7.dp)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = if (hasNov26) OpsEmerald else OpsAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (hasNov26) {
                                        "Nov 26 logged (SN 140–144) • Next-day ~07:40 AM routine logging for prior day's court bookings"
                                    } else {
                                        "Nov 26 pending next-day entry (~07:40 AM routine check)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsWhite
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasNov26) "+ ADD" else "LOG",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasNov26) OpsEmerald else OpsAmber
                            )
                        }
                    }
                }
            }

            // Single-Page Sport Filter Chip Bar ("All", "Football", "Padel") + Date/Unit Quick Filters + Search
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SportFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                SportFilter.ALL -> allBookings.size
                                SportFilter.FOOTBALL -> footballList.size
                                SportFilter.PADEL -> padelList.size
                            }
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { onSelectFilter(filter) },
                                label = { Text("${filter.label} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = OpsConsole,
                                    labelColor = OpsSteel,
                                    selectedContainerColor = OpsAmberDeep,
                                    selectedLabelColor = OpsAmber
                                ),
                                modifier = Modifier.testTag("sport_chip_${filter.name.lowercase()}")
                            )
                        }
                        FilterChip(
                            selected = selectedDateFilter == "NOV26",
                            onClick = { onSelectDateFilter(if (selectedDateFilter == "NOV26") "ALL" else "NOV26") },
                            label = { Text("26-Nov-2026") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = OpsConsole,
                                labelColor = OpsSteel,
                                selectedContainerColor = OpsCyanDeep,
                                selectedLabelColor = OpsCyan
                            ),
                            modifier = Modifier.testTag("sport_date_nov26")
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("MM-A01-22", "MM-E25-22", "MM-A02-17", "MM-D17-33", "MM-F27-37").forEach { unitCode ->
                            val selectedUnit = searchQuery.equals(unitCode, ignoreCase = true)
                            FilterChip(
                                selected = selectedUnit,
                                onClick = { onSearchQueryChange(if (selectedUnit) "" else unitCode) },
                                label = { Text(unitCode) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = OpsConsole,
                                    labelColor = OpsSteel,
                                    selectedContainerColor = OpsCyanDeep,
                                    selectedLabelColor = OpsCyan
                                ),
                                modifier = Modifier.testTag("sport_unit_chip_$unitCode")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search name, unit, T-code, date, or SN...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = OpsSteel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sports_search_input")
                    )
                }
            }

            itemsIndexed(filteredBookings, key = { _, booking -> booking.sn }) { index, booking ->
                SpringListItemContainer(itemKey = booking.sn, index = index) {
                    SportsBookingRowCard(
                        booking = booking,
                        onClick = {
                            onResetAiVision()
                            editingBooking = booking
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showQuickParserSheet) {
        SportsQuickEntrySheet(
            initialBooking = SportsBookingEntity(
                sn = 0,
                sport = "PADEL",
                date = "26/11/2026",
                time = "18:00 - 19:00",
                name = "",
                unitDetails = "",
                discount = "FREE",
                referenceNo = "",
                durationPlayed = 1.0,
                remarks = ""
            ),
            aiVisionState = aiVisionState,
            onParseTextOffline = onParseTextOffline,
            onSavePickedPhoto = onSavePickedPhoto,
            onExtractWithGeminiVision = onExtractWithGeminiVision,
            onDismiss = { showQuickParserSheet = false },
            onSave = {
                onSaveBooking(it)
                showQuickParserSheet = false
            },
            onDelete = null
        )
    }

    editingBooking?.let { target ->
        SportsQuickEntrySheet(
            initialBooking = target,
            aiVisionState = aiVisionState,
            onParseTextOffline = onParseTextOffline,
            onSavePickedPhoto = onSavePickedPhoto,
            onExtractWithGeminiVision = onExtractWithGeminiVision,
            onDismiss = { editingBooking = null },
            onSave = {
                onSaveBooking(it)
                editingBooking = null
            },
            onDelete = {
                onDeleteBooking(target.sn)
                editingBooking = null
            }
        )
    }
}

@Composable
private fun SportsBookingRowCard(
    booking: SportsBookingEntity,
    onClick: () -> Unit
) {
    val isFootball = booking.sport.equals("FOOTBALL", ignoreCase = true)
    val sportColor = if (isFootball) OpsCyan else OpsEmerald
    val sportDeep = if (isFootball) OpsCyanDeep else OpsEmeraldDeep

    val cardInteraction = remember { MutableInteractionSource() }
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsConsole),
        border = BorderStroke(1.dp, OpsBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .springPressScale(cardInteraction, pressedScale = 0.98f)
            .testTag("sports_row_${booking.sn}")
            .clickable(
                interactionSource = cardInteraction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (isFootball) Icons.Filled.SportsSoccer else Icons.Filled.SportsTennis,
                        contentDescription = booking.sport,
                        tint = sportColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SN ${booking.sn} • ${booking.sport}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = OpsTabularFontFamily),
                        color = sportColor
                    )
                    if (!booking.attachmentPath.isNullOrBlank()) {
                        Icon(Icons.Filled.Image, contentDescription = "Has photo attachment", tint = OpsAmber, modifier = Modifier.size(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OpsStatusPill(
                        label = booking.discount.ifBlank { "FREE" },
                        containerColor = OpsEmeraldDeep,
                        contentColor = OpsEmerald
                    )
                    OpsStatusPill(
                        label = String.format(Locale.US, "%.1fh", booking.durationPlayed),
                        containerColor = sportDeep,
                        contentColor = sportColor
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${booking.name} (${booking.unitDetails.ifBlank { "-" }})",
                style = MaterialTheme.typography.titleMedium,
                color = OpsWhite
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${booking.date}  |  ${booking.time}  |  Ref: ${booking.referenceNo.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
                color = OpsSteel
            )
            if (booking.remarks.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = booking.remarks,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsSteel.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportsQuickEntrySheet(
    initialBooking: SportsBookingEntity,
    aiVisionState: AiVisionUiState,
    onParseTextOffline: (String, String?) -> ParsedBookingDraft,
    onSavePickedPhoto: suspend (android.net.Uri) -> String?,
    onExtractWithGeminiVision: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SportsBookingEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var rawPasteText by remember { mutableStateOf("") }
    var attachmentPath by remember { mutableStateOf(initialBooking.attachmentPath) }
    var sport by remember { mutableStateOf(initialBooking.sport) }
    var date by remember { mutableStateOf(initialBooking.date) }
    var time by remember { mutableStateOf(initialBooking.time) }
    var name by remember { mutableStateOf(initialBooking.name) }
    var unit by remember { mutableStateOf(initialBooking.unitDetails) }
    var discount by remember { mutableStateOf(initialBooking.discount) }
    var refNo by remember { mutableStateOf(initialBooking.referenceNo) }
    var durationStr by remember { mutableStateOf(initialBooking.durationPlayed.toString()) }
    var remarks by remember { mutableStateOf(initialBooking.remarks) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val localPath = onSavePickedPhoto(uri)
                if (localPath != null) {
                    attachmentPath = localPath
                }
            }
        }
    }

    LaunchedEffect(aiVisionState) {
        if (aiVisionState is AiVisionUiState.Extracted) {
            val d = aiVisionState.draft
            sport = d.sport
            date = d.date
            time = d.time
            if (d.name.isNotBlank()) name = d.name
            if (d.unitDetails.isNotBlank()) unit = d.unitDetails
            discount = d.discount.ifBlank { "FREE" }
            if (d.referenceNo.isNotBlank()) refNo = d.referenceNo
            durationStr = d.durationPlayed.toString()
            if (d.remarks.isNotBlank()) remarks = d.remarks
            if (d.attachmentPath != null) attachmentPath = d.attachmentPath
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = OpsSlate) {
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
                Text(
                    text = if (initialBooking.sn == 0) "Quick-Entry Text & Photo Parser" else "Edit Booking SN ${initialBooking.sn}",
                    style = MaterialTheme.typography.titleLarge,
                    color = OpsWhite
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete booking", tint = OpsCrimson)
                    }
                }
            }

            // Offline Text Paste Parser + Photo Attachment + Optional Gemini Vision
            Card(
                colors = CardDefaults.cardColors(containerColor = OpsConsole),
                border = BorderStroke(1.dp, OpsBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rawPasteText,
                        onValueChange = { rawPasteText = it },
                        label = { Text("Paste WhatsApp / SMS booking text (optional)") },
                        placeholder = { Text("e.g. Padel 26/11/2026 19:00-20:30 Khalid M-A04-16 T-PD-2290 FREE") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sports_paste_text_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpringOutlinedButton(
                            onClick = {
                                val parsed = onParseTextOffline(rawPasteText, attachmentPath)
                                sport = parsed.sport
                                date = parsed.date
                                time = parsed.time
                                if (parsed.name.isNotBlank()) name = parsed.name
                                if (parsed.unitDetails.isNotBlank()) unit = parsed.unitDetails
                                discount = parsed.discount
                                if (parsed.referenceNo.isNotBlank()) refNo = parsed.referenceNo
                                durationStr = parsed.durationPlayed.toString()
                                if (parsed.remarks.isNotBlank()) remarks = parsed.remarks
                            },
                            enabled = rawPasteText.isNotBlank(),
                            border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parse_text_offline_button")
                        ) {
                            Icon(Icons.Filled.TextSnippet, contentDescription = null, tint = OpsCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Parse Offline", color = OpsCyan)
                        }

                        SpringOutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_booking_photo_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = OpsAmber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (attachmentPath == null) "Attach Photo" else "Change Photo", color = OpsAmber)
                        }
                    }

                    attachmentPath?.let { path ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OpsObsidian)
                        ) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = "Attached booking photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        SpringButton(
                            onClick = { onExtractWithGeminiVision(path, rawPasteText) },
                            enabled = aiVisionState !is AiVisionUiState.Extracting,
                            colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extract_gemini_vision_button")
                        ) {
                            if (aiVisionState is AiVisionUiState.Extracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = OpsObsidian,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Extracting with Gemini Vision...")
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Extract with Gemini Vision (Online)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (aiVisionState is AiVisionUiState.Error) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OpsCrimsonSurface),
                            border = BorderStroke(1.dp, OpsCrimson)
                        ) {
                            Text(
                                text = aiVisionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsCrimsonText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Editable Worksheet Fields
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("FOOTBALL", "PADEL").forEachIndexed { idx, s ->
                    SegmentedButton(
                        selected = sport.equals(s, true),
                        onClick = { sport = s },
                        shape = SegmentedButtonDefaults.itemShape(idx, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = OpsAmberDeep,
                            activeContentColor = OpsAmber,
                            inactiveContainerColor = OpsConsole,
                            inactiveContentColor = OpsSteel
                        )
                    ) {
                        Text(s)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("DATE") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("TIME") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("NAME") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("UNIT DETAILS") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("DISCOUNT") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = refNo, onValueChange = { refNo = it }, label = { Text("REFERENCE NO") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = durationStr,
                    onValueChange = { durationStr = it },
                    label = { Text("HRS") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.75f)
                )
            }
            OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("REMARKS") }, modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpringOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = OpsSteel) }
                SpringButton(
                    onClick = {
                        onSave(
                            initialBooking.copy(
                                sport = sport.uppercase(Locale.ROOT),
                                date = date.trim(),
                                time = time.trim(),
                                name = name.trim(),
                                unitDetails = unit.trim(),
                                discount = discount.trim().ifBlank { "FREE" },
                                referenceNo = refNo.trim(),
                                durationPlayed = durationStr.toDoubleOrNull() ?: 1.0,
                                remarks = remarks.trim(),
                                attachmentPath = attachmentPath
                            )
                        )
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_sports_booking_button")
                ) {
                    Text("Save Booking", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
