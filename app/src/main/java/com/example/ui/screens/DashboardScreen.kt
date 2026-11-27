package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.cloud.CloudSyncState
import com.example.data.cloud.RtdbSyncState
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.OpsReminderEntity
import com.example.data.db.ReminderType
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.example.ui.components.DailyRoutineChecklistCard
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.OpsAlertBanner
import com.example.ui.components.OpsMetricCard
import com.example.ui.components.OpsQuickActionPill
import com.example.ui.components.OpsStatusPill
import com.example.ui.components.SpringButton
import com.example.ui.components.SpringOutlinedButton
import com.example.ui.components.StaggeredMotionReveal
import com.example.ui.components.springPressScale
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
import com.example.ui.theme.OpsTabularFontFamily
import com.example.ui.theme.OpsWhite
import java.util.Locale

@Composable
fun DashboardScreen(
    keys: List<KeyRegisterEntity>,
    visitors: List<VisitorDayEntity>,
    maintenance: List<MaintenanceRequestEntity>,
    sports: List<SportsBookingEntity>,
    reminders: List<OpsReminderEntity>,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onNavigateToKeys: () -> Unit,
    onNavigateToVisitors: () -> Unit,
    onNavigateToSports: () -> Unit,
    onNavigateToReports: () -> Unit = {},
    onPlusOneVisitor: () -> Unit,
    onPlusOneViewing: (String) -> Unit,
    onToggleDailyStep: (OpsReminderEntity, Int) -> Unit,
    onSnoozeReminder: (OpsReminderEntity) -> Unit,
    onDoneReminder: (OpsReminderEntity, Boolean) -> Unit,
    onPreviewNotify: (OpsReminderEntity) -> Unit,
    onOpenLiveIngest: () -> Unit,
    onExportXlsx: () -> Unit,
    onExportJson: () -> Unit,
    operatorLabel: String = "",
    cloudSyncState: CloudSyncState = CloudSyncState.SignedOut,
    rtdbUrl: String = "",
    rtdbSyncState: RtdbSyncState = RtdbSyncState.Unconfigured,
    onSaveRtdbUrl: (String) -> Unit = {},
    onSyncRtdbNow: () -> Unit = {},
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val novUnreturnedKeys = remember(keys) { keys.filter { it.isCurrentMonthUnreturnedAlert } }
    val backlogRedKeys = remember(keys) { keys.filter { it.isUnreturnedAlert && !it.isCurrentMonthUnreturnedAlert } }
    val allUnreturnedKeys = remember(keys) { keys.filter { it.isUnreturnedAlert } }
    val moveInPendingKeys = remember(keys) { keys.filter { it.isMoveInPendingTCode } }
    val todayVisitor = remember(visitors) { visitors.firstOrNull { it.dateIso == "2026-11-26" } }
    val footballCount = remember(sports) { sports.count { it.sport.equals("FOOTBALL", ignoreCase = true) } }
    val padelCount = remember(sports) { sports.count { it.sport.equals("PADEL", ignoreCase = true) } }
    val totalSportsHours = remember(sports) { sports.sumOf { it.durationPlayed } }
    val daily0740 = remember(reminders) { reminders.firstOrNull { it.type == ReminderType.DAILY_0740 } }
    val daily1530 = remember(reminders) { reminders.firstOrNull { it.type == ReminderType.DAILY_1530 } }
    val satAudit = remember(reminders) { reminders.firstOrNull { it.type == ReminderType.SAT_AUDIT_0900 } }
    val keyReminders = remember(reminders) { reminders.filter { it.type == ReminderType.KEY_NEXT_DAY && !it.isDone } }

    var showHomeViewingDialog by remember { mutableStateOf(false) }
    var showRtdbDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OpsObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header Card — "MASAKEN MESAIMEER"
        item {
            StaggeredMotionReveal(delayMillis = 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(OpsSlate)
                ) {
                    val bannerContext = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(bannerContext)
                            .data(R.drawable.img_doha_ops_banner_1795716743874)
                            .crossfade(false)
                            .build(),
                        contentDescription = "Masaken Mesaimeer Doha banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        OpsObsidian.copy(alpha = 0.94f),
                                        OpsObsidian.copy(alpha = 0.72f),
                                        OpsObsidian.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OpsStatusPill(
                                label = "MASAKEN MESAIMEER",
                                containerColor = OpsCyanDeep.copy(alpha = 0.92f),
                                contentColor = OpsCyan
                            )
                        }
                        Column {
                            Text(
                                text = "Masaken Mesaimeer",
                                style = MaterialTheme.typography.headlineMedium,
                                color = OpsWhite
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Key Register SI 1645 • Sports Nov 26 (140 rows) • Daily Briefing 3:30 PM",
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsSteel
                            )
                        }
                    }
                }
            }
        }

        // Signed-in Operator Session & Automatic Real-Time Background Sync Strip
        item {
            StaggeredMotionReveal(delayMillis = 25) {
                val (syncLabel, syncColor, syncIcon) = when (cloudSyncState) {
                    is CloudSyncState.Synced -> Triple("AUTO-SYNC LIVE", OpsEmerald, Icons.Filled.CloudDone)
                    is CloudSyncState.Syncing -> Triple("SYNCING IN BACKGROUND...", OpsCyan, Icons.Filled.CloudSync)
                    is CloudSyncState.Error -> Triple("OFFLINE QUEUE", OpsAmber, Icons.Filled.CloudOff)
                    is CloudSyncState.SignedOut -> Triple("SIGNED OUT", OpsSteel, Icons.Filled.CloudOff)
                }
                val secondarySyncText = when {
                    rtdbUrl.isBlank() -> syncLabel
                    rtdbSyncState is RtdbSyncState.Synced -> "$syncLabel  •  RTDB ${rtdbSyncState.lastSyncedLabel}"
                    rtdbSyncState is RtdbSyncState.Syncing -> "$syncLabel  •  RTDB Auto-Syncing..."
                    rtdbSyncState is RtdbSyncState.Error -> "$syncLabel  •  RTDB Retry Queued"
                    else -> "$syncLabel  •  RTDB Auto-Mirror On"
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = syncIcon,
                                contentDescription = syncLabel,
                                tint = syncColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = operatorLabel.ifBlank { "Signed-in Operator" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OpsWhite
                                )
                                Text(
                                    text = secondarySyncText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = syncColor
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { showRtdbDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("rtdb_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Background RTDB mirror settings",
                                    tint = if (rtdbUrl.isBlank()) OpsSteel else OpsCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            SpringOutlinedButton(
                                onClick = onSignOut,
                                border = BorderStroke(1.dp, OpsBorder),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign out",
                                    tint = OpsSteel,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out", color = OpsWhite)
                            }
                        }
                    }
                }
            }
        }

        // Tactile Real-Time Command Action Strip
        item {
            StaggeredMotionReveal(delayMillis = 50) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OpsQuickActionPill(
                        label = "XLSX · CSV · Photo",
                        icon = Icons.Filled.PhotoCamera,
                        accentColor = OpsCyan,
                        testTag = "quick_live_ingest_pill",
                        onClick = onOpenLiveIngest
                    )
                    OpsQuickActionPill(
                        label = "Reports & Insp.",
                        icon = Icons.Filled.Assessment,
                        accentColor = OpsAmber,
                        testTag = "quick_reports_pill",
                        onClick = onNavigateToReports
                    )
                    OpsQuickActionPill(
                        label = "+1 Visitor",
                        icon = Icons.Filled.PersonAdd,
                        accentColor = OpsEmerald,
                        testTag = "quick_plus_visitor_pill",
                        onClick = onPlusOneVisitor
                    )
                    OpsQuickActionPill(
                        label = "+1 Viewing",
                        icon = Icons.Filled.HomeWork,
                        accentColor = OpsAmber,
                        testTag = "quick_plus_viewing_pill",
                        onClick = { showHomeViewingDialog = true }
                    )
                    OpsQuickActionPill(
                        label = "Keys (${novUnreturnedKeys.size})",
                        icon = Icons.Filled.Key,
                        accentColor = if (novUnreturnedKeys.isNotEmpty()) OpsCrimson else OpsEmerald,
                        testTag = "quick_keys_pill",
                        onClick = onNavigateToKeys
                    )
                }
            }
        }

        // Notification permission prompt when needed on Android 13+
        if (!hasNotificationPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsAmberDeep),
                    border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = OpsAmber
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Native Notifications",
                                style = MaterialTheme.typography.titleSmall,
                                color = OpsWhite
                            )
                            Text(
                                text = "Allow alerts for 07:40 AM daily checks, 3:30 PM briefing, Sat 09:00 AM audit, Sat Weekly & 1st-of-Month reports.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsSteel
                            )
                        }
                        SpringButton(
                            onClick = onRequestNotificationPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian),
                            modifier = Modifier.testTag("enable_notifications_button")
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        // 2x2 Operational KPI Grid with Staggered Spring Motion
        item {
            StaggeredMotionReveal(delayMillis = 100) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val novUnitLabels = novUnreturnedKeys.joinToString(", ") {
                            it.flatUnitLocation.removePrefix("M-")
                        }.ifBlank { "All returned" }
                        val backlogNote = if (backlogRedKeys.isNotEmpty()) {
                            " • SI ${backlogRedKeys.joinToString { it.si.toString() }} (Jul)"
                        } else ""
                        OpsMetricCard(
                            title = "Unreturned Keys",
                            count = novUnreturnedKeys.size,
                            subtitle = "$novUnitLabels • ${moveInPendingKeys.size} Move-In$backlogNote",
                            accentColor = if (novUnreturnedKeys.isNotEmpty()) OpsCrimson else OpsEmerald,
                            icon = Icons.Filled.Key,
                            testTag = "kpi_unreturned_keys",
                            onClick = onNavigateToKeys,
                            pulseWhenPositive = true,
                            modifier = Modifier.weight(1f)
                        )
                        OpsMetricCard(
                            title = "Today Visitors",
                            count = todayVisitor?.visitorCount ?: 8,
                            subtitle = "${todayVisitor?.viewingCount ?: 2} viewings (${todayVisitor?.flatDetails ?: "M-C13-16, M-C15-21"})",
                            accentColor = OpsCyan,
                            icon = Icons.Filled.Groups,
                            testTag = "kpi_today_visitors",
                            onClick = onNavigateToVisitors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OpsMetricCard(
                            title = "Nov Sports Log",
                            count = sports.size,
                            subtitle = String.format(Locale.US, "%d FB / %d PD • %.1f hrs", footballCount, padelCount, totalSportsHours),
                            accentColor = OpsAmber,
                            icon = Icons.Filled.SportsSoccer,
                            testTag = "kpi_sports_bookings",
                            onClick = onNavigateToSports,
                            modifier = Modifier.weight(1f)
                        )
                        OpsMetricCard(
                            title = "Maintenance",
                            count = maintenance.size,
                            subtitle = "Active work orders & resident requests",
                            accentColor = OpsEmerald,
                            icon = Icons.Filled.Build,
                            testTag = "kpi_maintenance_orders",
                            onClick = onNavigateToVisitors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3:30 PM Daily Briefing Card
        item {
            StaggeredMotionReveal(delayMillis = 125) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag("daily_briefing_330_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                LivePulseBeacon(color = OpsCyan, size = 7.dp)
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = OpsCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "3:30 PM Daily Briefing",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OpsWhite
                                )
                            }
                            OpsStatusPill(
                                label = "26-NOV-2026",
                                containerColor = OpsCyanDeep,
                                contentColor = OpsCyan
                            )
                        }
                        val novAlertLine = novUnreturnedKeys.joinToString(", ") {
                            "${it.flatUnitLocation} (SI ${it.si})"
                        }.ifBlank { "None" }
                        val backlogSuffix = if (backlogRedKeys.isNotEmpty()) {
                            " + ${backlogRedKeys.size} Jul backlog (${backlogRedKeys.joinToString { "SI ${it.si}" }} confirm later)"
                        } else ""
                        Text(
                            text = "• Nov Unreturned (${novUnreturnedKeys.size}): $novAlertLine$backlogSuffix",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (novUnreturnedKeys.isNotEmpty()) OpsCrimsonText else OpsEmerald
                        )
                        val moveInUnits = moveInPendingKeys.joinToString(", ") { it.flatUnitLocation }.ifBlank { "None" }
                        Text(
                            text = "• Yesterday Move-In (${moveInPendingKeys.size}): $moveInUnits — Pending T-code (Mr. Shajith)",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsAmber
                        )
                        Text(
                            text = "• PM Visitors (26-Nov): ${todayVisitor?.visitorCount ?: 8} visitors, ${todayVisitor?.viewingCount ?: 2} viewings (${todayVisitor?.flatDetails ?: "M-C13-16, M-C15-21"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )
                        Text(
                            text = "• Court Bookings (Nov 1–26): ${sports.size} total ($footballCount Football / $padelCount Padel • ${String.format(Locale.US, "%.1f", totalSportsHours)} hrs)",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpringButton(
                                onClick = onNavigateToReports,
                                colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("briefing_open_reports_btn")
                            ) {
                                Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reports & Insp.", fontWeight = FontWeight.Bold)
                            }
                            if (daily1530 != null) {
                                SpringOutlinedButton(
                                    onClick = { onPreviewNotify(daily1530) },
                                    border = BorderStroke(1.dp, OpsCyan.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("briefing_preview_notify_btn")
                                ) {
                                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = OpsCyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Notify", color = OpsCyan)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saturday Nov 28, 2026 09:00 AM Audit Banner
        if (satAudit != null && !satAudit.isDone) {
            item {
                StaggeredMotionReveal(delayMillis = 150) {
                    OpsAlertBanner(
                        reminder = satAudit,
                        accentColor = OpsAmber,
                        containerColor = OpsConsole,
                        onActionClick = onNavigateToKeys,
                        actionLabel = "Inspect Keys",
                        onSnoozeClick = { onSnoozeReminder(satAudit) },
                        onDoneClick = { onDoneReminder(satAudit, true) },
                        onPreviewNotifyClick = { onPreviewNotify(satAudit) }
                    )
                }
            }
        }

        // Daily 07:40 AM Next-Day Sports & Log Check Card
        item {
            StaggeredMotionReveal(delayMillis = 190) {
                DailyRoutineChecklistCard(
                    reminder = daily0740,
                    onToggleStep = { bit -> daily0740?.let { onToggleDailyStep(it, bit) } },
                    onSnooze = { daily0740?.let(onSnoozeReminder) },
                    onMarkDone = { done -> daily0740?.let { onDoneReminder(it, done) } },
                    onPreviewNotify = { daily0740?.let(onPreviewNotify) }
                )
            }
        }

        // Next-Day Unreturned Key Verification Alerts
        if (allUnreturnedKeys.isNotEmpty()) {
            item {
                StaggeredMotionReveal(delayMillis = 220) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OpsCrimsonSurface),
                        border = BorderStroke(1.dp, OpsCrimsonBorder),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LivePulseBeacon(color = OpsCrimson, size = 8.dp)
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = OpsCrimson
                                )
                                val backlogTag = if (backlogRedKeys.isNotEmpty()) " (+${backlogRedKeys.size} Jul)" else ""
                                Text(
                                    text = "Unreturned Keys: ${novUnreturnedKeys.size} This Month$backlogTag",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OpsWhite
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Nov alerts (M-A04-16, M-F30-18) plus July SI 1283 kept red until confirmed returned (Move-In keys excluded).",
                                style = MaterialTheme.typography.bodySmall,
                                color = OpsCrimsonText
                            )
                            Spacer(Modifier.height(10.dp))
                            allUnreturnedKeys.forEach { key ->
                                val rowInteraction = remember { MutableInteractionSource() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .springPressScale(rowInteraction, pressedScale = 0.98f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(
                                            interactionSource = rowInteraction,
                                            indication = androidx.compose.material3.ripple(),
                                            onClick = onNavigateToKeys
                                        )
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SI ${key.si} • ${key.flatUnitLocation}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = OpsTabularFontFamily),
                                            color = OpsWhite
                                        )
                                        Text(
                                            text = "${key.issuedTo} (${key.dateOut} ${key.timeOut}) — ${key.status}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OpsCrimsonText
                                        )
                                    }
                                    OpsStatusPill(
                                        label = "VERIFY",
                                        containerColor = OpsCrimsonBorder.copy(alpha = 0.45f),
                                        contentColor = OpsCrimsonText
                                    )
                                }
                            }
                            if (keyReminders.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                SpringOutlinedButton(
                                    onClick = { onPreviewNotify(keyReminders.first()) },
                                    border = BorderStroke(1.dp, OpsCrimsonText.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("test_key_notification_btn")
                                ) {
                                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = OpsCrimsonText, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Send Next-Day Key Alert", color = OpsCrimsonText)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Direct Live Add / Update & Export Hub
        item {
            StaggeredMotionReveal(delayMillis = 250) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OpsConsole),
                    border = BorderStroke(1.dp, OpsBorder),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Live Add / Update & Sync (XLSX · CSV · Photo)",
                            style = MaterialTheme.typography.titleMedium,
                            color = OpsWhite
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Add or update any ledger in real time via .xlsx, .csv, .json, or photo, or export Excel sheets with red unreturned-key styling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsSteel
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SpringButton(
                                onClick = onOpenLiveIngest,
                                colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("import_workbook_button")
                            ) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("XLSX / CSV / Photo", fontWeight = FontWeight.Bold)
                            }
                            SpringOutlinedButton(
                                onClick = onExportXlsx,
                                border = BorderStroke(1.dp, OpsAmber.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_xlsx_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, tint = OpsAmber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export .xlsx", color = OpsAmber)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SpringOutlinedButton(
                            onClick = onExportJson,
                            border = BorderStroke(1.dp, OpsBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("export_json_button")
                        ) {
                            Text("Export Full JSON Snapshot", color = OpsSteel)
                        }
                    }
                }
            }
        }
    }

    if (showHomeViewingDialog) {
        var flatCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showHomeViewingDialog = false },
            containerColor = OpsSlate,
            titleContentColor = OpsWhite,
            textContentColor = OpsSteel,
            title = { Text("Quick Log Viewing (+1)") },
            text = {
                OutlinedTextField(
                    value = flatCode,
                    onValueChange = { flatCode = it },
                    label = { Text("Flat Details (e.g. M-C16-04)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                SpringButton(
                    onClick = {
                        onPlusOneViewing(flatCode)
                        showHomeViewingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpsAmber, contentColor = OpsObsidian)
                ) {
                    Text("Log Viewing")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHomeViewingDialog = false }) { Text("Cancel", color = OpsSteel) }
            }
        )
    }

    if (showRtdbDialog) {
        var urlDraft by remember(rtdbUrl) { mutableStateOf(rtdbUrl) }
        AlertDialog(
            onDismissRequest = { showRtdbDialog = false },
            containerColor = OpsSlate,
            titleContentColor = OpsWhite,
            textContentColor = OpsSteel,
            title = { Text("Background Realtime DB Mirror") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cloud Firestore syncs automatically in the background. Optionally enter your .firebaseio.com URL to also auto-mirror changes in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpsSteel
                    )
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("https://<your-db>.firebaseio.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                SpringButton(
                    onClick = {
                        onSaveRtdbUrl(urlDraft)
                        showRtdbDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OpsCyan, contentColor = OpsObsidian)
                ) {
                    Text("Save & Auto-Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRtdbDialog = false }) { Text("Cancel", color = OpsSteel) }
            }
        )
    }
}
