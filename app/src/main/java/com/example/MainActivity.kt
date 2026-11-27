package com.example

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.configureAppCheck
import com.example.data.sync.IngestTarget
import com.example.notifications.OpsNotificationHelper
import com.example.ui.OpsViewModel
import com.example.ui.components.RealTimeIngestSheet
import com.example.ui.components.opsScreenEnterTransition
import com.example.ui.components.opsScreenExitTransition
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.KeyRegisterScreen
import com.example.ui.screens.SportsBookingScreen
import com.example.ui.screens.VisitorsMaintenanceScreen
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MasakenOpsTheme
import com.example.ui.theme.OpsCrimson
import com.example.ui.theme.OpsCyan
import com.example.ui.theme.OpsCyanDeep
import com.example.ui.theme.OpsObsidian
import com.example.ui.theme.OpsSlate
import com.example.ui.theme.OpsSteel
import com.example.ui.theme.OpsWhite
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_KEYS = "keys"
const val ROUTE_VISITORS = "visitors"
const val ROUTE_SPORTS = "sports"

private data class OpsDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val opsDestinations = listOf(
    OpsDestination(ROUTE_DASHBOARD, "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    OpsDestination(ROUTE_KEYS, "Keys", Icons.Filled.Key, Icons.Outlined.Key),
    OpsDestination(ROUTE_VISITORS, "PM Log", Icons.Filled.Groups, Icons.Outlined.Groups),
    OpsDestination(ROUTE_SPORTS, "Sports", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        val initialNavTarget = intent?.getStringExtra(OpsNotificationHelper.EXTRA_NAV_TARGET)

        setContent {
            MasakenOpsTheme {
                MasakenAuthGate(initialNavTarget = initialNavTarget)
            }
        }
    }
}

@Composable
private fun MasakenAuthGate(initialNavTarget: String?) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MasakenOpsApp }
    val auth = remember(app) { app.firebaseAuth }
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen()
    } else {
        key(user.uid) {
            val opsViewModel: OpsViewModel = viewModel(key = user.uid)
            val operatorLabel = remember(user) {
                user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.takeIf { it.isNotBlank() }
                    ?: "Authenticated Operator"
            }
            MasakenOpsRoot(
                initialNavTarget = initialNavTarget,
                operatorLabel = operatorLabel,
                opsViewModel = opsViewModel,
                onSignOut = {
                    scope.launch {
                        withContext(NonCancellable) {
                            app.firestoreSyncRepository.detachListeners()
                            try {
                                CredentialManager.create(context)
                                    .clearCredentialState(ClearCredentialStateRequest())
                            } catch (e: Exception) {
                                Log.w("Auth", "Clear credential state skipped", e)
                            }
                            Firebase.auth.signOut()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MasakenOpsRoot(
    initialNavTarget: String?,
    operatorLabel: String,
    opsViewModel: OpsViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val cloudSyncState by opsViewModel.cloudSyncState.collectAsStateWithLifecycle()
    val keys by opsViewModel.keys.collectAsStateWithLifecycle()
    val filteredKeys by opsViewModel.filteredKeys.collectAsStateWithLifecycle()
    val keyFilter by opsViewModel.keyFilter.collectAsStateWithLifecycle()
    val keyQuery by opsViewModel.keyQuery.collectAsStateWithLifecycle()

    val visitors by opsViewModel.visitors.collectAsStateWithLifecycle()
    val maintenance by opsViewModel.filteredMaintenance.collectAsStateWithLifecycle()
    val maintenanceQuery by opsViewModel.maintenanceQuery.collectAsStateWithLifecycle()

    val sports by opsViewModel.sportsBookings.collectAsStateWithLifecycle()
    val filteredSports by opsViewModel.filteredSports.collectAsStateWithLifecycle()
    val sportFilter by opsViewModel.sportFilter.collectAsStateWithLifecycle()
    val sportQuery by opsViewModel.sportQuery.collectAsStateWithLifecycle()

    val reminders by opsViewModel.reminders.collectAsStateWithLifecycle()
    val aiVisionState by opsViewModel.aiVisionState.collectAsStateWithLifecycle()
    val ingestState by opsViewModel.ingestState.collectAsStateWithLifecycle()

    var ingestSheetTarget by remember { mutableStateOf<IngestTarget?>(null) }

    var hasNotifPermission by remember {
        mutableStateOf(OpsNotificationHelper.hasNotificationPermission(context))
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    var pendingCsvTarget by remember { mutableStateOf("keys") }

    val exportXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let { opsViewModel.exportXlsx(it) }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { opsViewModel.exportCsv(it, pendingCsvTarget) }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { opsViewModel.exportJsonBackup(it) }
    }

    LaunchedEffect(Unit) {
        opsViewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(initialNavTarget) {
        if (initialNavTarget in listOf(ROUTE_DASHBOARD, ROUTE_KEYS, ROUTE_VISITORS, ROUTE_SPORTS)) {
            navController.navigate(initialNavTarget!!) {
                launchSingleTop = true
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_DASHBOARD
    val unreturnedCount = remember(keys) { keys.count { it.isUnreturnedAlert } }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OpsObsidian)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                NavigationRail(
                    containerColor = OpsSlate,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    opsDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTo(dest.route) },
                            icon = {
                                NavIconWithOptionalBadge(dest, selected, unreturnedCount)
                            },
                            label = { Text(dest.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = OpsCyan,
                                selectedTextColor = OpsCyan,
                                indicatorColor = OpsCyanDeep,
                                unselectedIconColor = OpsSteel,
                                unselectedTextColor = OpsSteel
                            ),
                            modifier = Modifier.testTag("nav_${dest.route}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = OpsObsidian,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = OpsSlate,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            opsDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        NavIconWithOptionalBadge(dest, selected, unreturnedCount)
                                    },
                                    label = { Text(dest.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = OpsCyan,
                                        selectedTextColor = OpsCyan,
                                        indicatorColor = OpsCyanDeep,
                                        unselectedIconColor = OpsSteel,
                                        unselectedTextColor = OpsSteel
                                    ),
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val reducedMotion = LocalReducedMotion.current
                    fun routeIndex(route: String?): Int {
                        val idx = opsDestinations.indexOfFirst { it.route == route }
                        return if (idx >= 0) idx else 0
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        enterTransition = {
                            val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                            opsScreenEnterTransition(forward = forward, reducedMotion = reducedMotion)
                        },
                        exitTransition = {
                            val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                            opsScreenExitTransition(forward = forward, reducedMotion = reducedMotion)
                        },
                        popEnterTransition = {
                            val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                            opsScreenEnterTransition(forward = forward, reducedMotion = reducedMotion)
                        },
                        popExitTransition = {
                            val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                            opsScreenExitTransition(forward = forward, reducedMotion = reducedMotion)
                        }
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                keys = keys,
                                visitors = visitors,
                                maintenance = maintenance,
                                sports = sports,
                                reminders = reminders,
                                hasNotificationPermission = hasNotifPermission,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onNavigateToKeys = { navigateTo(ROUTE_KEYS) },
                                onNavigateToVisitors = { navigateTo(ROUTE_VISITORS) },
                                onNavigateToSports = { navigateTo(ROUTE_SPORTS) },
                                onPlusOneVisitor = opsViewModel::incrementTodayVisitor,
                                onPlusOneViewing = opsViewModel::incrementTodayViewing,
                                onToggleDailyStep = opsViewModel::toggleChecklistItem,
                                onSnoozeReminder = opsViewModel::snoozeReminder,
                                onDoneReminder = opsViewModel::markReminderDone,
                                onPreviewNotify = opsViewModel::previewNotificationNow,
                                onOpenLiveIngest = {
                                    opsViewModel.resetIngestState()
                                    ingestSheetTarget = IngestTarget.AUTO
                                },
                                onExportXlsx = {
                                    exportXlsxLauncher.launch("Masaken_Mesaimeer_Workbooks.xlsx")
                                },
                                onExportJson = {
                                    exportJsonLauncher.launch("masaken_mesaimeer_backup.json")
                                },
                                operatorLabel = operatorLabel,
                                cloudSyncState = cloudSyncState,
                                onSignOut = onSignOut
                            )
                        }

                        composable(ROUTE_KEYS) {
                            KeyRegisterScreen(
                                allKeys = keys,
                                filteredKeys = filteredKeys,
                                selectedFilter = keyFilter,
                                searchQuery = keyQuery,
                                onSelectFilter = opsViewModel::setKeyFilter,
                                onSearchQueryChange = opsViewModel::setKeyQuery,
                                onAllocateNextSi = opsViewModel::allocateNextKeySi,
                                onSaveKeyEntry = opsViewModel::saveKeyEntry,
                                onMarkKeyReturned = opsViewModel::markKeyReturned,
                                onReconcileNovember = opsViewModel::reconcileNovemberKeys,
                                onOpenLiveIngest = {
                                    opsViewModel.resetIngestState()
                                    ingestSheetTarget = IngestTarget.KEYS
                                },
                                onExportCsv = {
                                    pendingCsvTarget = "keys"
                                    exportCsvLauncher.launch("WSF-PMD-F-07_Key_Register.csv")
                                }
                            )
                        }

                        composable(ROUTE_VISITORS) {
                            VisitorsMaintenanceScreen(
                                visitors = visitors,
                                maintenance = maintenance,
                                maintenanceQuery = maintenanceQuery,
                                onMaintenanceQueryChange = opsViewModel::setMaintenanceQuery,
                                onPlusOneVisitor = opsViewModel::incrementTodayVisitor,
                                onPlusOneViewing = opsViewModel::incrementTodayViewing,
                                onSaveVisitorDay = opsViewModel::saveVisitorDay,
                                onSaveMaintenance = opsViewModel::saveMaintenanceRequest,
                                onDeleteMaintenance = opsViewModel::deleteMaintenanceRequest,
                                onOpenLiveIngest = {
                                    opsViewModel.resetIngestState()
                                    ingestSheetTarget = IngestTarget.VISITORS
                                },
                                onExportVisitorsCsv = {
                                    pendingCsvTarget = "visitors"
                                    exportCsvLauncher.launch("PM_Visitors_2026.csv")
                                },
                                onExportMaintenanceCsv = {
                                    pendingCsvTarget = "maintenance"
                                    exportCsvLauncher.launch("Maintenance_Request_Log_2026.csv")
                                }
                            )
                        }

                        composable(ROUTE_SPORTS) {
                            SportsBookingScreen(
                                allBookings = sports,
                                filteredBookings = filteredSports,
                                selectedFilter = sportFilter,
                                searchQuery = sportQuery,
                                aiVisionState = aiVisionState,
                                onSelectFilter = opsViewModel::setSportFilter,
                                onSearchQueryChange = opsViewModel::setSportQuery,
                                onParseTextOffline = opsViewModel::parseTextOffline,
                                onSavePickedPhoto = opsViewModel::savePickedBookingPhoto,
                                onExtractWithGeminiVision = opsViewModel::extractBookingWithGeminiVision,
                                onResetAiVision = opsViewModel::resetAiVisionState,
                                onSaveBooking = opsViewModel::saveSportsBooking,
                                onDeleteBooking = opsViewModel::deleteSportsBooking,
                                onOpenLiveIngest = {
                                    opsViewModel.resetIngestState()
                                    ingestSheetTarget = IngestTarget.SPORTS
                                },
                                onExportCsv = {
                                    pendingCsvTarget = "sports"
                                    exportCsvLauncher.launch("Padel_Football_November_2026.csv")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    ingestSheetTarget?.let { target ->
        RealTimeIngestSheet(
            initialTarget = target,
            ingestState = ingestState,
            onPreviewFile = opsViewModel::previewWorkbookFile,
            onSavePickedPhoto = opsViewModel::savePickedBookingPhoto,
            onExtractFromPhoto = opsViewModel::extractIngestFromPhoto,
            onCreateOfflineDraft = opsViewModel::createOfflineDraftFromNote,
            onCommitBundle = opsViewModel::commitIngestPreview,
            onReset = opsViewModel::resetIngestState,
            onDismiss = { ingestSheetTarget = null }
        )
    }
}

@Composable
private fun NavIconWithOptionalBadge(
    dest: OpsDestination,
    selected: Boolean,
    unreturnedKeyCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest.route == ROUTE_KEYS && unreturnedKeyCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = OpsCrimson, contentColor = OpsWhite) {
                    Text(unreturnedKeyCount.toString())
                }
            }
        ) {
            Icon(icon, contentDescription = dest.label)
        }
    } else {
        Icon(icon, contentDescription = dest.label)
    }
}
