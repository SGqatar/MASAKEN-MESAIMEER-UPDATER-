package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.VisitorDayEntity
import com.example.ui.MonthlyVisitorSummary
import com.example.ui.components.AnimatedIntCounter
import com.example.ui.components.OpsStatusPill
import com.example.ui.components.SpringButton
import com.example.ui.components.SpringFloatingActionButton
import com.example.ui.components.SpringListItemContainer
import com.example.ui.components.SpringOutlinedButton
import com.example.ui.components.springPressScale
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.OpsTabularFontFamily
import com.example.ui.theme.OpsAmber
import com.example.ui.theme.OpsBorder
import com.example.ui.theme.OpsConsole
import com.example.ui.theme.OpsCrimson
import com.example.ui.theme.OpsCyan
import com.example.ui.theme.OpsCyanDeep
import com.example.ui.theme.OpsEmerald
import com.example.ui.theme.OpsEmeraldDeep
import com.example.ui.theme.OpsObsidian
import com.example.ui.theme.OpsSlate
import com.example.ui.theme.OpsSteel
import com.example.ui.theme.OpsWhite

@Composable
fun VisitorsMaintenanceScreen(
    visitors: List<VisitorDayEntity>,
    maintenance: List<MaintenanceRequestEntity>,
    maintenanceQuery: String,
    onMaintenanceQueryChange: (String) -> Unit,
    onPlusOneVisitor: () -> Unit,
    onPlusOneViewing: (String) -> Unit,
    onSaveVisitorDay: (VisitorDayEntity) -> Unit,
    onSaveMaintenance: (MaintenanceRequestEntity) -> Unit,
    onDeleteMaintenance: (Int) -> Unit,
    onOpenLiveIngest: () -> Unit,
    onExportVisitorsCsv: () -> Unit,
    onExportMaintenanceCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = PM Visitors, 1 = Maintenance Log
    var selectedMonthKey by remember { mutableStateOf<String?>(null) }
    var showAddViewingDialog by remember { mutableStateOf(false) }
    var editingVisitorDay by remember { mutableStateOf<VisitorDayEntity?>(null) }
    var editingMaintenance by remember { mutableStateOf<MaintenanceRequestEntity?>(null) }

    val todayRecord = remember(visitors) { visitors.firstOrNull { it.dateIso == "2026-11-26" } }
    val monthlySummaries = remember(visitors) {
        visitors.groupBy { it.monthKey }
            .entries
            .sortedByDescending { it.key }
            .map { (key, list) ->
                val label = when (key) {
                    "2026-12" -> "Dec 2026 SUM"
                    "2026-11" -> "Nov 2026 SUM"
                    "2026-10" -> "Oct 2026 SUM"
                    "2026-09" -> "Sep 2026 SUM"
                    "2026-08" -> "Aug 2026 SUM"
                    "2026-07" -> "Jul 2026 SUM"
                    "2026-06" -> "Jun 2026 SUM"
                    "2026-05" -> "May 2026 SUM"
                    "2026-04" -> "Apr 2026 SUM"
                    "2026-03" -> "Mar 2026 SUM"
                    "2026-02" -> "Feb 2026 SUM"
                    "2026-01" -> "Jan 2026 SUM"
                    else -> "$key SUM"
                }
                MonthlyVisitorSummary(
                    monthKey = key,
                    monthLabel = label,
                    totalVisitors = list.sumOf { it.visitorCount },
                    totalViewings = list.sumOf { it.viewingCount },
                    daysLogged = list.size
                )
            }
    }
    val displayedVisitors = remember(visitors, selectedMonthKey) {
        val m = selectedMonthKey
        if (m == null) visitors else visitors.filter { it.monthKey == m }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OpsObsidian,
        floatingActionButton = {
            SpringFloatingActionButton(
                onClick = {
                    if (activeTab == 0) {
                        editingVisitorDay = VisitorDayEntity(
                            dateIso = "2026-11-27",
                            displayDate = "27-Nov-2026",
                            monthKey = "2026-11",
                            visitorCount = 0,
                            viewingCount = 0,
                            flatDetails = ""
                        )
                    } else {
                        editingMaintenance = MaintenanceRequestEntity(
                            sn = 0,
                            date = "26/11/2026",
                            names = "",
                            phoneNumber = "+974 ",
                            workOrderNumber = "-",
                            qidNumber = "",
                            flatDetails = "",
                            requestDetails = ""
                        )
                    }
                },
                containerColor = OpsCyan,
                contentColor = OpsObsidian,
                modifier = Modifier.testTag("fab_visitors_maintenance")
            ) {
                Icon(Icons.Filled.Add, contentDescription = if (activeTab == 0) "Add Visitor Day" else "New Work Order")
            }
        }
    ) { innerPadding ->
        val reducedMotion = LocalReducedMotion.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PM Visitors & Maintenance",
                        style = MaterialTheme.typography.headlineMedium,
                        color = OpsWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(
                            onClick = onOpenLiveIngest,
                            modifier = Modifier.testTag("vm_live_ingest_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Live Add or Update via XLSX, CSV, or Photo", tint = OpsCyan)
                        }
                        IconButton(
                            onClick = if (activeTab == 0) onExportVisitorsCsv else onExportMaintenanceCsv,
                            modifier = Modifier.testTag("export_vm_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Export CSV", tint = OpsAmber)
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = OpsCyanDeep,
                            activeContentColor = OpsCyan,
                            inactiveContainerColor = OpsConsole,
                            inactiveContentColor = OpsSteel
                        ),
                        modifier = Modifier.testTag("tab_pm_visitors")
                    ) {
                        Text("PM Visitors (2026)")
                    }
                    SegmentedButton(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = OpsCyanDeep,
                            activeContentColor = OpsCyan,
                            inactiveContainerColor = OpsConsole,
                            inactiveContentColor = OpsSteel
                        ),
                        modifier = Modifier.testTag("tab_maintenance_log")
                    ) {
                        Text("Maintenance (${maintenance.size})")
                    }
                }
            }

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val forward = targetState > initialState
                        val enterOffset = if (forward) 1 else -1
                        val exitOffset = -enterOffset
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { it * enterOffset / 3 }
                        ) + fadeIn(tween(180))) togetherWith (slideOutHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            targetOffsetX = { it * exitOffset / 4 }
                        ) + fadeOut(tween(140)))
                    }
                },
                modifier = Modifier.weight(1f),
                label = "visitorsMaintenanceTabTransition"
            ) { tab ->
                if (tab == 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Today's Quick Actions (+1 Visitor / +1 Viewing)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpsConsole),
                        border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.4f)),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Today: ${todayRecord?.displayDate ?: "26-Nov-2026"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OpsWhite
                                )
                                OpsStatusPill(
                                    label = "QUICK LOG",
                                    containerColor = OpsCyanDeep,
                                    contentColor = OpsCyan
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Visitors", style = MaterialTheme.typography.labelSmall, color = OpsSteel)
                                    AnimatedIntCounter(
                                        targetValue = todayRecord?.visitorCount ?: 0,
                                        color = OpsCyan
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Viewings", style = MaterialTheme.typography.labelSmall, color = OpsSteel)
                                    AnimatedIntCounter(
                                        targetValue = todayRecord?.viewingCount ?: 0,
                                        color = OpsAmber
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Flats Viewed: ${todayRecord?.flatDetails ?: "-"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
                                color = OpsSteel
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SpringButton(
                                    onClick = onPlusOneVisitor,
                                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("plus_one_visitor_button")
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("+1 Visitor", fontWeight = FontWeight.Bold)
                                }
                                SpringOutlinedButton(
                                    onClick = { showAddViewingDialog = true },
                                    border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("plus_one_viewing_button")
                                ) {
                                    Icon(Icons.Filled.HomeWork, contentDescription = null, tint = OpsAmber, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("+1 Viewing", color = OpsAmber)
                                }
                            }
                        }
                    }
                }

                // Monthly SUM Roll-Ups (tap to filter daily rows)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        monthlySummaries.forEach { sum ->
                            val isSelected = selectedMonthKey == sum.monthKey
                            val cardInteraction = remember { MutableInteractionSource() }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) OpsCyanDeep else OpsSlate
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) OpsCyan else OpsBorder
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .springPressScale(cardInteraction, pressedScale = 0.96f)
                                    .clickable(
                                        interactionSource = cardInteraction,
                                        indication = androidx.compose.material3.ripple()
                                    ) {
                                        selectedMonthKey = if (isSelected) null else sum.monthKey
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = sum.monthLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) OpsCyan else OpsAmber
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${sum.totalVisitors} Visitors • ${sum.totalViewings} Viewings",
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = OpsTabularFontFamily),
                                        color = OpsWhite
                                    )
                                    Text(
                                        text = if (isSelected) "${sum.daysLogged} days • Tap to show all" else "${sum.daysLogged} days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OpsSteel
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily Visitor Rows
                itemsIndexed(displayedVisitors, key = { _, day -> day.dateIso }) { index, day ->
                    SpringListItemContainer(itemKey = day.dateIso, index = index) {
                        val rowInteraction = remember { MutableInteractionSource() }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OpsConsole),
                            border = BorderStroke(1.dp, OpsBorder),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPressScale(rowInteraction, pressedScale = 0.98f)
                                .testTag("visitor_row_${day.dateIso}")
                                .clickable(
                                    interactionSource = rowInteraction,
                                    indication = androidx.compose.material3.ripple()
                                ) { editingVisitorDay = day }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = day.displayDate,
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = OpsTabularFontFamily),
                                        color = OpsWhite
                                    )
                                    Text(
                                        text = "Flats: ${day.flatDetails.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OpsSteel
                                    )
                                    if (day.notes.isNotBlank()) {
                                        Text(
                                            text = day.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OpsSteel.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    OpsStatusPill(
                                        label = "${day.visitorCount} VIS",
                                        containerColor = OpsCyanDeep,
                                        contentColor = OpsCyan
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    OpsStatusPill(
                                        label = "${day.viewingCount} VIEW",
                                        containerColor = OpsEmeraldDeep,
                                        contentColor = OpsEmerald
                                    )
                                }
                            }
                        }
                    }
                }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                // Maintenance Request Log
                item {
                    OutlinedTextField(
                        value = maintenanceQuery,
                        onValueChange = onMaintenanceQueryChange,
                        placeholder = { Text("Search WO#, QID, flat, resident, or issue...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = OpsSteel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("maintenance_search_input")
                    )
                }

                itemsIndexed(maintenance, key = { _, item -> item.sn }) { index, item ->
                    SpringListItemContainer(itemKey = item.sn, index = index) {
                        val maintInteraction = remember { MutableInteractionSource() }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OpsConsole),
                            border = BorderStroke(1.dp, OpsBorder),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .springPressScale(maintInteraction, pressedScale = 0.98f)
                                .testTag("maintenance_row_${item.sn}")
                                .clickable(
                                    interactionSource = maintInteraction,
                                    indication = androidx.compose.material3.ripple()
                                ) { editingMaintenance = item }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Build, contentDescription = null, tint = OpsAmber, modifier = Modifier.size(18.dp))
                                        val displaySn = if (item.sn > 1000) "2026 #${item.sn - 1000}" else "2025 #${item.sn}"
                                        Text(
                                            text = "$displaySn • ${item.workOrderNumber}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = OpsTabularFontFamily),
                                            color = OpsAmber
                                        )
                                    }
                                    OpsStatusPill(
                                        label = item.flatDetails,
                                        containerColor = OpsCyanDeep,
                                        contentColor = OpsCyan
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = item.requestDetails,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OpsWhite
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${item.date}  |  ${item.names}  |  ${item.phoneNumber}  |  QID: ${item.qidNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
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
    }

    if (showAddViewingDialog) {
        var flatCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddViewingDialog = false },
            containerColor = OpsSlate,
            titleContentColor = OpsWhite,
            textContentColor = OpsSteel,
            title = { Text("Log Apartment Viewing (+1)") },
            text = {
                OutlinedTextField(
                    value = flatCode,
                    onValueChange = { flatCode = it },
                    label = { Text("Flat Details (e.g. M-C16-04)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("viewing_flat_input")
                )
            },
            confirmButton = {
                SpringButton(
                    onClick = {
                        onPlusOneViewing(flatCode)
                        showAddViewingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                    modifier = Modifier.testTag("confirm_plus_one_viewing")
                ) {
                    Text("Save Viewing")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddViewingDialog = false }) {
                    Text("Cancel", color = OpsSteel)
                }
            }
        )
    }

    editingVisitorDay?.let { target ->
        VisitorDayEditSheet(
            initial = target,
            onDismiss = { editingVisitorDay = null },
            onSave = { updated ->
                onSaveVisitorDay(updated)
                editingVisitorDay = null
            }
        )
    }

    editingMaintenance?.let { target ->
        MaintenanceEditSheet(
            initial = target,
            onDismiss = { editingMaintenance = null },
            onSave = { updated ->
                onSaveMaintenance(updated)
                editingMaintenance = null
            },
            onDelete = if (target.sn > 0) {
                {
                    onDeleteMaintenance(target.sn)
                    editingMaintenance = null
                }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitorDayEditSheet(
    initial: VisitorDayEntity,
    onDismiss: () -> Unit,
    onSave: (VisitorDayEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayDate by remember { mutableStateOf(initial.displayDate) }
    var visitorsText by remember { mutableStateOf(initial.visitorCount.toString()) }
    var viewingsText by remember { mutableStateOf(initial.viewingCount.toString()) }
    var flats by remember { mutableStateOf(initial.flatDetails) }
    var notes by remember { mutableStateOf(initial.notes) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = OpsSlate) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Edit PM Visitor Day", style = MaterialTheme.typography.titleLarge, color = OpsWhite)
            OutlinedTextField(value = displayDate, onValueChange = { displayDate = it }, label = { Text("Display Date (e.g. 26-Nov-2026)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = visitorsText,
                    onValueChange = { visitorsText = it },
                    label = { Text("Visitors") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewingsText,
                    onValueChange = { viewingsText = it },
                    label = { Text("Viewings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(value = flats, onValueChange = { flats = it }, label = { Text("Flat Details") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpringOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = OpsSteel) }
                SpringButton(
                    onClick = {
                        onSave(
                            initial.copy(
                                displayDate = displayDate.trim(),
                                visitorCount = visitorsText.toIntOrNull() ?: 0,
                                viewingCount = viewingsText.toIntOrNull() ?: 0,
                                flatDetails = flats.trim().ifBlank { "-" },
                                notes = notes.trim()
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Day", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceEditSheet(
    initial: MaintenanceRequestEntity,
    onDismiss: () -> Unit,
    onSave: (MaintenanceRequestEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf(initial.date) }
    var names by remember { mutableStateOf(initial.names) }
    var phone by remember { mutableStateOf(initial.phoneNumber) }
    var wo by remember { mutableStateOf(initial.workOrderNumber) }
    var qid by remember { mutableStateOf(initial.qidNumber) }
    var flat by remember { mutableStateOf(initial.flatDetails) }
    var details by remember { mutableStateOf(initial.requestDetails) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = OpsSlate) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val editLabel = when {
                    initial.sn == 0 -> "New Maintenance Request"
                    initial.sn > 1000 -> "Edit Request 2026 #${initial.sn - 1000}"
                    else -> "Edit Request 2025 #${initial.sn}"
                }
                Text(
                    text = editLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = OpsWhite
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete request", tint = OpsCrimson)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = flat, onValueChange = { flat = it }, label = { Text("Flat Details") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = names, onValueChange = { names = it }, label = { Text("Names") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = qid, onValueChange = { qid = it }, label = { Text("Q.I.D Number") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = wo, onValueChange = { wo = it }, label = { Text("Work Order Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Maintenance Request Details") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpringOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = OpsSteel) }
                SpringButton(
                    onClick = {
                        onSave(
                            initial.copy(
                                date = date.trim(),
                                names = names.trim(),
                                phoneNumber = phone.trim(),
                                workOrderNumber = wo.trim(),
                                qidNumber = qid.trim(),
                                flatDetails = flat.trim(),
                                requestDetails = details.trim()
                            )
                        )
                    },
                    enabled = names.isNotBlank() && details.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Request", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
