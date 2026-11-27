package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.ui.KeyFilterMode
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.OpsStatusPill
import com.example.ui.components.SpringButton
import com.example.ui.components.SpringFloatingActionButton
import com.example.ui.components.SpringListItemContainer
import com.example.ui.components.SpringOutlinedButton
import com.example.ui.components.springPressScale
import com.example.ui.theme.OpsTabularFontFamily
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.OpsAmber
import com.example.ui.theme.OpsAmberDeep
import com.example.ui.theme.OpsBorder
import com.example.ui.theme.OpsConsole
import com.example.ui.theme.OpsConsoleElevated
import com.example.ui.theme.OpsCrimson
import com.example.ui.theme.OpsCrimsonBorder
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyRegisterScreen(
    allKeys: List<KeyRegisterEntity>,
    filteredKeys: List<KeyRegisterEntity>,
    selectedFilter: KeyFilterMode,
    searchQuery: String,
    onSelectFilter: (KeyFilterMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAllocateNextSi: suspend () -> Int,
    onSaveKeyEntry: (KeyRegisterEntity) -> Unit,
    onMarkKeyReturned: (KeyRegisterEntity, String, String, String) -> Unit,
    onReconcileNovember: () -> Unit,
    onOpenLiveIngest: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var editingEntry by remember { mutableStateOf<KeyRegisterEntity?>(null) }
    var returningEntry by remember { mutableStateOf<KeyRegisterEntity?>(null) }

    val unreturnedCount = remember(allKeys) { allKeys.count { it.isUnreturnedAlert } }
    val pendingReconcileCount = remember(allKeys) { allKeys.count { it.needsNovemberReconciliation } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OpsObsidian,
        floatingActionButton = {
            SpringFloatingActionButton(
                onClick = {
                    scope.launch {
                        val nextSi = onAllocateNextSi()
                        editingEntry = KeyRegisterEntity(
                            si = nextSi,
                            dateOut = "26/11/2026",
                            timeOut = "16:36",
                            flatUnitLocation = "",
                            keyCardType = "Apartment Key Set",
                            purpose = "",
                            issuedTo = "",
                            qidOrNote = "",
                            dateIn = "",
                            timeIn = "",
                            returnedBy = "",
                            remarks = "",
                            status = KeyStatus.ISSUED
                        )
                    }
                },
                containerColor = OpsCyan,
                contentColor = OpsObsidian,
                modifier = Modifier.testTag("fab_issue_key")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Issue Key or Access Card")
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Key & Access Card Register",
                            style = MaterialTheme.typography.headlineMedium,
                            color = OpsWhite
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OpsStatusPill(
                                label = "WSF-PMD-F-07",
                                containerColor = OpsCyanDeep,
                                contentColor = OpsCyan
                            )
                            OpsStatusPill(
                                label = "SI 1645 • 26/11/2026",
                                containerColor = OpsConsole,
                                contentColor = OpsSteel
                            )
                        }
                    }
                    Row {
                        IconButton(
                            onClick = onOpenLiveIngest,
                            modifier = Modifier.testTag("keys_live_ingest_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Live Add or Update via XLSX, CSV, or Photo", tint = OpsCyan)
                        }
                        IconButton(
                            onClick = onExportCsv,
                            modifier = Modifier.testTag("export_keys_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Export Key Register CSV", tint = OpsAmber)
                        }
                    }
                }
            }

            // November Return Reconciliation Helper (11/26/2026 at 16:36)
            if (pendingReconcileCount > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpsAmberDeep),
                        border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.55f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.HistoryToggleOff, contentDescription = null, tint = OpsAmber)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nov Return Reconciliation ($pendingReconcileCount rows)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OpsWhite
                                )
                                Text(
                                    text = "Complete missing return timestamps on November returned rows using 11/26/2026 at 16:36.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OpsSteel
                                )
                            }
                            SpringButton(
                                onClick = onReconcileNovember,
                                colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                                modifier = Modifier.testTag("reconcile_nov_keys_button")
                            ) {
                                Text("Apply 16:36")
                            }
                        }
                    }
                }
            }

            // Search & Filter Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search SI, flat (M-A04-16), name, or QID...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = OpsSteel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("key_search_input")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KeyFilterMode.entries.forEach { mode ->
                            val badge = when (mode) {
                                KeyFilterMode.ALL -> "${allKeys.size}"
                                KeyFilterMode.UNRETURNED -> "$unreturnedCount"
                                KeyFilterMode.RETURNED -> "${allKeys.size - unreturnedCount}"
                                KeyFilterMode.NEEDS_NOV_RECONCILE -> "$pendingReconcileCount"
                            }
                            FilterChip(
                                selected = selectedFilter == mode,
                                onClick = { onSelectFilter(mode) },
                                label = { Text("${mode.label} ($badge)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = OpsConsole,
                                    labelColor = OpsSteel,
                                    selectedContainerColor = if (mode == KeyFilterMode.UNRETURNED) OpsCrimsonSurface else OpsCyanDeep,
                                    selectedLabelColor = if (mode == KeyFilterMode.UNRETURNED) OpsCrimsonText else OpsCyan
                                ),
                                modifier = Modifier.testTag("key_filter_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (filteredKeys.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpsConsole),
                        border = BorderStroke(1.dp, OpsBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = OpsSteel, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No key register rows match this filter.", color = OpsSteel)
                        }
                    }
                }
            } else {
                itemsIndexed(filteredKeys, key = { _, entry -> entry.si }) { index, entry ->
                    SpringListItemContainer(itemKey = entry.si, index = index) {
                        KeyRegisterRowCard(
                            entry = entry,
                            onMarkReturnedClick = { returningEntry = entry },
                            onEditClick = { editingEntry = entry }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    returningEntry?.let { target ->
        MarkKeyReturnedDialog(
            entry = target,
            onDismiss = { returningEntry = null },
            onConfirm = { returnedBy, dateIn, timeIn ->
                onMarkKeyReturned(target, returnedBy, dateIn, timeIn)
                returningEntry = null
            }
        )
    }

    editingEntry?.let { target ->
        KeyEditBottomSheet(
            initial = target,
            onDismiss = { editingEntry = null },
            onSave = { updated ->
                onSaveKeyEntry(updated)
                editingEntry = null
            }
        )
    }
}

@Composable
private fun KeyRegisterRowCard(
    entry: KeyRegisterEntity,
    onMarkReturnedClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val isAlert = entry.isUnreturnedAlert

    // Strictly reserve red fill / red highlight for unreturned or active issued keys
    val containerColor by animateColorAsState(
        targetValue = if (isAlert) OpsCrimsonSurface else OpsConsole,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "keyRowBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isAlert) OpsCrimsonBorder else OpsBorder,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "keyRowBorder"
    )

    val cardInteraction = remember { MutableInteractionSource() }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .springPressScale(cardInteraction, pressedScale = 0.98f)
            .testTag("key_row_${entry.si}")
            .clickable(
                interactionSource = cardInteraction,
                indication = androidx.compose.material3.ripple(),
                onClick = onEditClick
            )
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
                    if (isAlert) {
                        LivePulseBeacon(color = OpsCrimson, size = 7.dp)
                    }
                    Text(
                        text = "SI ${entry.si}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = OpsTabularFontFamily),
                        color = if (isAlert) OpsCrimsonText else OpsCyan
                    )
                    Text(
                        text = entry.flatUnitLocation,
                        style = MaterialTheme.typography.titleMedium,
                        color = OpsWhite
                    )
                }
                val isTenantIssued = entry.status.equals(KeyStatus.ISSUED_TO_TENANT, ignoreCase = true)
                OpsStatusPill(
                    label = entry.status.uppercase(),
                    containerColor = when {
                        isAlert -> Color(0xFF4C111B)
                        isTenantIssued -> OpsCyanDeep
                        else -> OpsEmeraldDeep
                    },
                    contentColor = when {
                        isAlert -> OpsCrimsonText
                        isTenantIssued -> OpsCyan
                        else -> OpsEmerald
                    }
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "${entry.keyCardType} • ${entry.purpose}",
                style = MaterialTheme.typography.bodyMedium,
                color = OpsWhite
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Out: ${entry.dateOut} ${entry.timeOut}  |  To: ${entry.issuedTo}  |  ID: ${entry.qidOrNote.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
                color = if (isAlert) OpsCrimsonText.copy(alpha = 0.9f) else OpsSteel
            )

            if (entry.dateIn.isNotBlank() || entry.timeIn.isNotBlank() || entry.returnedBy.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "In: ${entry.dateIn.ifBlank { "-" }} ${entry.timeIn.ifBlank { "-" }}  |  By: ${entry.returnedBy.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = OpsTabularFontFamily),
                    color = OpsEmerald
                )
            }

            if (entry.remarks.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Remarks: ${entry.remarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsSteel
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpringOutlinedButton(
                    onClick = onEditClick,
                    border = BorderStroke(1.dp, OpsBorder),
                    modifier = Modifier.testTag("edit_key_${entry.si}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = OpsSteel, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", color = OpsSteel)
                }
                if (isAlert) {
                    Spacer(Modifier.width(8.dp))
                    SpringButton(
                        onClick = onMarkReturnedClick,
                        colors = ButtonDefaults.buttonColors(containerColor = OpsEmerald, contentColor = OpsObsidian),
                        modifier = Modifier.testTag("return_key_${entry.si}")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark Returned", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkKeyReturnedDialog(
    entry: KeyRegisterEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var returnedBy by remember { mutableStateOf(entry.returnedBy.ifBlank { entry.issuedTo }) }
    var dateIn by remember { mutableStateOf("11/26/2026") }
    var timeIn by remember { mutableStateOf("16:36") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpsSlate,
        titleContentColor = OpsWhite,
        textContentColor = OpsSteel,
        title = { Text("Return Key — SI ${entry.si} (${entry.flatUnitLocation})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Marking this key returned clears the red highlight and completes any pending next-day verification reminder.")
                OutlinedTextField(
                    value = returnedBy,
                    onValueChange = { returnedBy = it },
                    label = { Text("Returned By (Q-In)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateIn,
                        onValueChange = { dateIn = it },
                        label = { Text("Date In") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeIn,
                        onValueChange = { timeIn = it },
                        label = { Text("Time In") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            SpringButton(
                onClick = { onConfirm(returnedBy, dateIn, timeIn) },
                colors = ButtonDefaults.buttonColors(containerColor = OpsEmerald, contentColor = OpsObsidian),
                modifier = Modifier.testTag("confirm_mark_returned")
            ) {
                Text("Confirm Return")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OpsSteel) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyEditBottomSheet(
    initial: KeyRegisterEntity,
    onDismiss: () -> Unit,
    onSave: (KeyRegisterEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var flat by remember { mutableStateOf(initial.flatUnitLocation) }
    var keyType by remember { mutableStateOf(initial.keyCardType) }
    var purpose by remember { mutableStateOf(initial.purpose) }
    var issuedTo by remember { mutableStateOf(initial.issuedTo) }
    var qid by remember { mutableStateOf(initial.qidOrNote) }
    var dateOut by remember { mutableStateOf(initial.dateOut) }
    var timeOut by remember { mutableStateOf(initial.timeOut) }
    var dateIn by remember { mutableStateOf(initial.dateIn) }
    var timeIn by remember { mutableStateOf(initial.timeIn) }
    var returnedBy by remember { mutableStateOf(initial.returnedBy) }
    var remarks by remember { mutableStateOf(initial.remarks) }
    var status by remember { mutableStateOf(initial.status) }

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
            Text(
                text = "Key Register Entry • SI ${initial.si}",
                style = MaterialTheme.typography.titleLarge,
                color = OpsWhite
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    KeyStatus.ISSUED,
                    KeyStatus.NOT_YET_RETURNED,
                    KeyStatus.ISSUED_TO_TENANT,
                    KeyStatus.RETURNED
                ).forEach { st ->
                    val isAlertSt = KeyStatus.isUnreturnedOrActive(st)
                    val isTenantSt = st == KeyStatus.ISSUED_TO_TENANT
                    FilterChip(
                        selected = status == st,
                        onClick = { status = st },
                        label = { Text(st) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = OpsConsole,
                            labelColor = OpsSteel,
                            selectedContainerColor = when {
                                isAlertSt -> OpsCrimsonSurface
                                isTenantSt -> OpsCyanDeep
                                else -> OpsEmeraldDeep
                            },
                            selectedLabelColor = when {
                                isAlertSt -> OpsCrimsonText
                                isTenantSt -> OpsCyan
                                else -> OpsEmerald
                            }
                        )
                    )
                }
            }

            if (KeyStatus.isUnreturnedOrActive(status)) {
                Text(
                    text = "Next-day verification reminder will be scheduled automatically when saved as unreturned/issued.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsCrimsonText
                )
            }

            OutlinedTextField(value = flat, onValueChange = { flat = it }, label = { Text("Flat / Unit / Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = keyType, onValueChange = { keyType = it }, label = { Text("Key / Card Type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = purpose, onValueChange = { purpose = it }, label = { Text("Purpose") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = issuedTo, onValueChange = { issuedTo = it }, label = { Text("Issued To (Q-Out)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = qid, onValueChange = { qid = it }, label = { Text("QID / ID or Note") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = dateOut, onValueChange = { dateOut = it }, label = { Text("Date Out") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = timeOut, onValueChange = { timeOut = it }, label = { Text("Time Out") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = dateIn, onValueChange = { dateIn = it }, label = { Text("Date In") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = timeIn, onValueChange = { timeIn = it }, label = { Text("Time In") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = returnedBy, onValueChange = { returnedBy = it }, label = { Text("Returned By (Q-In)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpringOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = OpsSteel)
                }
                SpringButton(
                    onClick = {
                        onSave(
                            initial.copy(
                                flatUnitLocation = flat.trim(),
                                keyCardType = keyType.trim(),
                                purpose = purpose.trim(),
                                issuedTo = issuedTo.trim(),
                                qidOrNote = qid.trim(),
                                dateOut = dateOut.trim(),
                                timeOut = timeOut.trim(),
                                dateIn = dateIn.trim(),
                                timeIn = timeIn.trim(),
                                returnedBy = returnedBy.trim(),
                                remarks = remarks.trim(),
                                status = status
                            )
                        )
                    },
                    enabled = flat.isNotBlank() && issuedTo.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_key_entry_button")
                ) {
                    Text("Save Key Row", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
