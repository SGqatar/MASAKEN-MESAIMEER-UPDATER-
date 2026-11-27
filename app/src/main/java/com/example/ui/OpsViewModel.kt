package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MasakenOpsApp
import com.example.data.ai.InspectionPhotoDraft
import com.example.data.ai.ParsedBookingDraft
import com.example.data.cloud.CloudSyncState
import com.example.data.cloud.EmailAttachmentBytes
import com.example.data.cloud.RtdbSyncState
import com.example.data.cloud.WorkspaceAuthOutcome
import com.example.data.db.InspectionReportEntity
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.OpsReminderEntity
import com.example.data.db.ReportDispatchEntity
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.example.data.sync.ArchiveSubfolder
import com.example.data.sync.ArchivedFileResult
import com.example.data.sync.ImportPreviewResult
import com.example.data.sync.ImportedWorkbookBundle
import com.example.data.sync.IngestTarget
import com.example.data.sync.InspectionExportItem
import com.example.data.sync.LocalArchiveEntry
import com.example.data.sync.LocalFolderArchiveManager
import com.example.data.sync.XlsxEngine
import com.example.notifications.OpsNotificationHelper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class KeyFilterMode(val label: String) {
    ALL("All"),
    UNRETURNED("Unreturned Red"),
    MOVE_IN_PENDING("Move-In T-Code"),
    RETURNED("Returned / Settled"),
    NEEDS_NOV_RECONCILE("Missing Nov Time")
}

enum class SportFilter(val label: String) {
    ALL("All"),
    FOOTBALL("Football"),
    PADEL("Padel")
}

sealed interface AiVisionUiState {
    data object Idle : AiVisionUiState
    data object Extracting : AiVisionUiState
    data class Extracted(val draft: ParsedBookingDraft) : AiVisionUiState
    data class Error(val message: String) : AiVisionUiState
}

sealed interface RealTimeIngestUiState {
    data object Idle : RealTimeIngestUiState
    data object Processing : RealTimeIngestUiState
    data class PreviewReady(val preview: ImportPreviewResult) : RealTimeIngestUiState
    data class Error(val message: String) : RealTimeIngestUiState
}

data class MonthlyVisitorSummary(
    val monthKey: String,
    val monthLabel: String,
    val totalVisitors: Int,
    val totalViewings: Int,
    val daysLogged: Int
)

data class PendingEmailDraft(
    val reportType: String, // "WEEKLY" | "MONTHLY" | "INSPECTION"
    val periodLabel: String,
    val defaultSubject: String,
    val defaultBody: String,
    val htmlBody: String,
    val attachments: List<ArchivedFileResult>,
    val inspectionReportId: String? = null
)

sealed interface PendingWorkspaceTask {
    data class SendEmailAndUpload(
        val draft: PendingEmailDraft,
        val toAddresses: List<String>,
        val ccAddresses: List<String>,
        val subject: String,
        val bodyText: String,
        val alsoUploadDrive: Boolean
    ) : PendingWorkspaceTask

    data class UploadDraftToDrive(val draft: PendingEmailDraft) : PendingWorkspaceTask
    data object BackupFullSnapshotToDrive : PendingWorkspaceTask
}

class OpsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MasakenOpsApp
    private val repo = app.repository
    private val cloudSync = app.firestoreSyncRepository
    private val sync = app.workbookSyncManager
    private val archive = app.localFolderArchiveManager
    private val workspace = app.googleWorkspaceService
    private val rtdbMirror = app.realtimeDbMirrorService
    private val parser = app.bookingParserService
    private val universalPhotoIngest = app.universalPhotoIngestService
    private val inspectionAi = app.inspectionAiService

    val cloudSyncState: StateFlow<CloudSyncState> = cloudSync.syncState
    val rtdbSyncState: StateFlow<RtdbSyncState> = rtdbMirror.syncState
    val rtdbUrl: StateFlow<String> = rtdbMirror.rtdbUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val keys: StateFlow<List<KeyRegisterEntity>> = repo.keysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visitors: StateFlow<List<VisitorDayEntity>> = repo.visitorsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maintenanceRequests: StateFlow<List<MaintenanceRequestEntity>> = repo.maintenanceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportsBookings: StateFlow<List<SportsBookingEntity>> = repo.sportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<OpsReminderEntity>> = repo.remindersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inspectionReports: StateFlow<List<InspectionReportEntity>> = repo.inspectionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reportDispatches: StateFlow<List<ReportDispatchEntity>> = repo.dispatchesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _keyFilter = MutableStateFlow(KeyFilterMode.ALL)
    val keyFilter: StateFlow<KeyFilterMode> = _keyFilter.asStateFlow()

    private val _keyDateFilter = MutableStateFlow("ALL") // "ALL" | "NOV26"
    val keyDateFilter: StateFlow<String> = _keyDateFilter.asStateFlow()

    private val _keyQuery = MutableStateFlow("")
    val keyQuery: StateFlow<String> = _keyQuery.asStateFlow()

    val filteredKeys: StateFlow<List<KeyRegisterEntity>> = combine(
        keys,
        _keyFilter,
        _keyDateFilter,
        _keyQuery
    ) { list, filter, dateFilter, query ->
        val q = query.trim().lowercase()
        list.filter { item ->
            val matchesFilter = when (filter) {
                KeyFilterMode.ALL -> true
                KeyFilterMode.UNRETURNED -> item.isUnreturnedAlert
                KeyFilterMode.MOVE_IN_PENDING -> item.isMoveInPendingTCode
                KeyFilterMode.RETURNED -> !item.isUnreturnedAlert && !item.isMoveInPendingTCode
                KeyFilterMode.NEEDS_NOV_RECONCILE -> item.needsNovemberReconciliation
            }
            val matchesDate = when (dateFilter) {
                "NOV26" -> matchesNov26Date(item.dateOut)
                else -> true
            }
            val matchesQuery = q.isBlank() ||
                item.si.toString().contains(q) ||
                item.flatUnitLocation.lowercase().contains(q) ||
                item.issuedBy.lowercase().contains(q) ||
                item.issuedTo.lowercase().contains(q) ||
                item.returnedBy.lowercase().contains(q) ||
                item.qidOrNote.lowercase().contains(q) ||
                item.keyCardType.lowercase().contains(q) ||
                item.purpose.lowercase().contains(q)
            matchesFilter && matchesDate && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sportFilter = MutableStateFlow(SportFilter.ALL)
    val sportFilter: StateFlow<SportFilter> = _sportFilter.asStateFlow()

    private val _sportDateFilter = MutableStateFlow("ALL") // "ALL" | "NOV26"
    val sportDateFilter: StateFlow<String> = _sportDateFilter.asStateFlow()

    private val _sportQuery = MutableStateFlow("")
    val sportQuery: StateFlow<String> = _sportQuery.asStateFlow()

    val filteredSports: StateFlow<List<SportsBookingEntity>> = combine(
        sportsBookings,
        _sportFilter,
        _sportDateFilter,
        _sportQuery
    ) { list, filter, dateFilter, query ->
        val q = query.trim().lowercase()
        list.filter { item ->
            val matchesFilter = when (filter) {
                SportFilter.ALL -> true
                SportFilter.FOOTBALL -> item.sport.equals("FOOTBALL", ignoreCase = true)
                SportFilter.PADEL -> item.sport.equals("PADEL", ignoreCase = true)
            }
            val matchesDate = when (dateFilter) {
                "NOV26" -> matchesNov26Date(item.date)
                else -> true
            }
            val matchesQuery = q.isBlank() ||
                item.name.lowercase().contains(q) ||
                item.unitDetails.lowercase().contains(q) ||
                item.referenceNo.lowercase().contains(q) ||
                item.date.lowercase().contains(q) ||
                item.sn.toString() == q
            matchesFilter && matchesDate && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _visitorQuery = MutableStateFlow("")
    val visitorQuery: StateFlow<String> = _visitorQuery.asStateFlow()

    private val _maintenanceQuery = MutableStateFlow("")
    val maintenanceQuery: StateFlow<String> = _maintenanceQuery.asStateFlow()

    val filteredMaintenance: StateFlow<List<MaintenanceRequestEntity>> = combine(
        maintenanceRequests,
        _maintenanceQuery
    ) { list, query ->
        val q = query.trim().lowercase()
        if (q.isBlank()) list else list.filter {
            it.names.lowercase().contains(q) ||
                it.flatDetails.lowercase().contains(q) ||
                it.workOrderNumber.lowercase().contains(q) ||
                it.qidNumber.lowercase().contains(q) ||
                it.requestDetails.lowercase().contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inspectionDrafts = MutableStateFlow<List<InspectionPhotoDraft>>(emptyList())
    val inspectionDrafts: StateFlow<List<InspectionPhotoDraft>> = _inspectionDrafts.asStateFlow()

    private val _isAnalyzingInspection = MutableStateFlow(false)
    val isAnalyzingInspection: StateFlow<Boolean> = _isAnalyzingInspection.asStateFlow()

    private val _isGeneratingReport = MutableStateFlow(false)
    val isGeneratingReport: StateFlow<Boolean> = _isGeneratingReport.asStateFlow()

    private val _pendingEmailDraft = MutableStateFlow<PendingEmailDraft?>(null)
    val pendingEmailDraft: StateFlow<PendingEmailDraft?> = _pendingEmailDraft.asStateFlow()

    private val _localArchiveFiles = MutableStateFlow<List<LocalArchiveEntry>>(emptyList())
    val localArchiveFiles: StateFlow<List<LocalArchiveEntry>> = _localArchiveFiles.asStateFlow()

    private var pendingWorkspaceTask: PendingWorkspaceTask? = null

    private val _workspaceAuthLauncherRequest = MutableSharedFlow<IntentSenderRequest>()
    val workspaceAuthLauncherRequest: SharedFlow<IntentSenderRequest> = _workspaceAuthLauncherRequest.asSharedFlow()

    private val _aiVisionState = MutableStateFlow<AiVisionUiState>(AiVisionUiState.Idle)
    val aiVisionState: StateFlow<AiVisionUiState> = _aiVisionState.asStateFlow()

    private val _ingestState = MutableStateFlow<RealTimeIngestUiState>(RealTimeIngestUiState.Idle)
    val ingestState: StateFlow<RealTimeIngestUiState> = _ingestState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    @OptIn(FlowPreview::class)
    private fun startBackgroundRtdbAutoSync() {
        viewModelScope.launch {
            combine(keys, visitors, sportsBookings, rtdbUrl) { k, v, s, url ->
                Triple(k, v, s) to url.trim()
            }
                .debounce(1500)
                .distinctUntilChanged()
                .collect { (datasets, url) ->
                    if (url.isNotBlank() && datasets.first.isNotEmpty()) {
                        rtdbMirror.pushAllDatasets(
                            keys = datasets.first,
                            visitors = datasets.second,
                            sports = datasets.third
                        )
                    }
                }
        }
    }

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            cloudSync.startSessionSync()
            refreshLocalArchives()
        }
        startBackgroundRtdbAutoSync()
    }

    override fun onCleared() {
        cloudSync.detachListeners()
        super.onCleared()
    }

    private fun matchesNov26Date(raw: String): Boolean {
        val clean = raw.trim()
        return clean.startsWith("26/11") || clean.startsWith("11/26") || clean == "2026-11-26"
    }

    fun setKeyFilter(mode: KeyFilterMode) {
        _keyFilter.value = mode
        _keyQuery.value = ""
        _keyDateFilter.value = "ALL"
    }

    fun jumpToKeySummaryFilter(
        mode: KeyFilterMode,
        dateFilter: String = "ALL",
        query: String = ""
    ) {
        _keyFilter.value = mode
        _keyDateFilter.value = dateFilter
        _keyQuery.value = query
    }

    fun resetAllKeyFilters() {
        _keyFilter.value = KeyFilterMode.ALL
        _keyDateFilter.value = "ALL"
        _keyQuery.value = ""
    }

    fun setKeyDateFilter(mode: String) {
        _keyDateFilter.value = mode
    }

    fun setKeyQuery(query: String) {
        _keyQuery.value = query
    }

    fun setSportFilter(filter: SportFilter) {
        _sportFilter.value = filter
    }

    fun setSportDateFilter(mode: String) {
        _sportDateFilter.value = mode
    }

    fun setSportQuery(query: String) {
        _sportQuery.value = query
    }

    fun setVisitorQuery(query: String) {
        _visitorQuery.value = query
    }

    fun setMaintenanceQuery(query: String) {
        _maintenanceQuery.value = query
    }

    suspend fun allocateNextKeySi(): Int = repo.getNextKeySi()

    fun saveKeyEntry(entry: KeyRegisterEntity) {
        viewModelScope.launch {
            val saved = repo.saveKeyEntry(entry)
            if (!saved) {
                _events.emit("Blocked test record excluded per operational policy.")
            } else if (entry.isUnreturnedAlert) {
                _events.emit("Saved SI ${entry.si} • Red alert & next-day reminder active.")
            } else if (entry.isMoveInPendingTCode) {
                _events.emit("Saved SI ${entry.si} • Move-In awaiting T-code from Mr. Shajith.")
            } else {
                _events.emit("Saved SI ${entry.si} (${entry.status}).")
            }
            autoMirrorRtdbIfConfigured()
        }
    }

    fun markKeyReturned(entry: KeyRegisterEntity, returnedBy: String, dateIn: String, timeIn: String) {
        viewModelScope.launch {
            repo.markKeyReturned(entry, returnedBy, dateIn, timeIn)
            _events.emit("SI ${entry.si} marked returned • Red alert cleared.")
            autoMirrorRtdbIfConfigured()
        }
    }

    fun reconcileNovemberKeys() {
        viewModelScope.launch {
            val count = repo.reconcileNovemberReturnedRows()
            if (count > 0) {
                _events.emit("Reconciled $count November returned row(s) with 11/26/2026 at 16:36.")
                autoMirrorRtdbIfConfigured()
            } else {
                _events.emit("All November returned rows already have complete return timestamps.")
            }
        }
    }

    fun incrementTodayVisitor() {
        viewModelScope.launch {
            repo.incrementTodayVisitor()
            _events.emit("+1 Visitor logged for 26-Nov-2026.")
            autoMirrorRtdbIfConfigured()
        }
    }

    fun incrementTodayViewing(flatDetails: String) {
        viewModelScope.launch {
            repo.incrementTodayViewing(flatDetails)
            _events.emit("+1 Viewing (${flatDetails.ifBlank { "Unit" }}) logged for 26-Nov-2026.")
            autoMirrorRtdbIfConfigured()
        }
    }

    fun saveVisitorDay(day: VisitorDayEntity) {
        viewModelScope.launch {
            repo.saveVisitorDay(day)
            _events.emit("Updated PM Visitor log for ${day.displayDate}.")
            autoMirrorRtdbIfConfigured()
        }
    }

    fun saveMaintenanceRequest(item: MaintenanceRequestEntity) {
        viewModelScope.launch {
            repo.saveMaintenanceRequest(item)
            _events.emit("Saved Maintenance Request ${item.workOrderNumber.ifBlank { "#${item.sn}" }}.")
        }
    }

    fun deleteMaintenanceRequest(sn: Int) {
        viewModelScope.launch {
            repo.deleteMaintenanceRequest(sn)
            _events.emit("Deleted maintenance request SN $sn.")
        }
    }

    fun saveSportsBooking(booking: SportsBookingEntity) {
        viewModelScope.launch {
            repo.saveSportsBooking(booking)
            _events.emit("Saved ${booking.sport} booking for ${booking.name}.")
            autoMirrorRtdbIfConfigured()
        }
    }

    fun deleteSportsBooking(sn: Int) {
        viewModelScope.launch {
            repo.deleteSportsBooking(sn)
            _events.emit("Removed sports booking SN $sn.")
            autoMirrorRtdbIfConfigured()
        }
    }

    fun toggleChecklistItem(reminder: OpsReminderEntity, bitIndex: Int) {
        viewModelScope.launch {
            repo.toggleReminderChecklist(reminder, bitIndex)
        }
    }

    fun snoozeReminder(reminder: OpsReminderEntity) {
        viewModelScope.launch {
            repo.snoozeReminder30Min(reminder)
            _events.emit("Snoozed '${reminder.title}' for 30 minutes.")
        }
    }

    fun markReminderDone(reminder: OpsReminderEntity, done: Boolean) {
        viewModelScope.launch {
            repo.setReminderDone(reminder, done)
            _events.emit(if (done) "Marked reminder complete." else "Re-activated reminder.")
        }
    }

    fun previewNotificationNow(reminder: OpsReminderEntity) {
        OpsNotificationHelper.showReminderNotification(app, reminder)
        viewModelScope.launch {
            if (OpsNotificationHelper.hasNotificationPermission(app)) {
                _events.emit("Posted native notification for '${reminder.title}'.")
            } else {
                _events.emit("Enable notification permission to preview system alerts.")
            }
        }
    }

    // --- Firebase Realtime Database Mirror ---

    fun saveRtdbUrl(url: String) {
        viewModelScope.launch {
            rtdbMirror.saveRtdbUrl(url)
            if (url.isNotBlank()) {
                syncAllToRealtimeDb()
            } else {
                _events.emit("Cleared Realtime Database mirror URL.")
            }
        }
    }

    fun syncAllToRealtimeDb() {
        viewModelScope.launch {
            rtdbMirror.pushAllDatasets(
                keys = keys.value,
                visitors = visitors.value,
                sports = sportsBookings.value
            ).fold(
                onSuccess = { _events.emit(it) },
                onFailure = { _events.emit(it.localizedMessage ?: "Realtime DB sync failed.") }
            )
        }
    }

    private suspend fun autoMirrorRtdbIfConfigured() {
        if (rtdbUrl.value.isNotBlank()) {
            rtdbMirror.pushAllDatasets(
                keys = keys.value,
                visitors = visitors.value,
                sports = sportsBookings.value
            )
        }
    }

    // --- Building Inspection XLSX + Photo Organization ---

    fun addInspectionPhotos(uris: List<Uri>, defaultArea: String = "General Corridor") {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = uris.mapNotNull { uri ->
                val path = archive.importPickedPhotoToLocal(uri, prefix = "insp")
                path?.let {
                    InspectionPhotoDraft(
                        id = UUID.randomUUID().toString(),
                        photoPath = it,
                        area = defaultArea,
                        finding = "",
                        severity = "Pass",
                        actionRequired = ""
                    )
                }
            }
            if (added.isNotEmpty()) {
                _inspectionDrafts.value = _inspectionDrafts.value + added
                _events.emit("Added ${added.size} inspection photo(s) for organization.")
            } else {
                _events.emit("Could not read selected inspection photo(s).")
            }
        }
    }

    fun addManualInspectionRow(area: String, finding: String, severity: String, actionRequired: String) {
        _inspectionDrafts.value = _inspectionDrafts.value + InspectionPhotoDraft(
            id = UUID.randomUUID().toString(),
            photoPath = "",
            area = area.ifBlank { "General Corridor" },
            finding = finding.ifBlank { "Routine check completed" },
            severity = severity,
            actionRequired = actionRequired.ifBlank { "None" }
        )
    }

    fun updateInspectionDraft(updated: InspectionPhotoDraft) {
        _inspectionDrafts.value = _inspectionDrafts.value.map {
            if (it.id == updated.id) updated else it
        }
    }

    fun removeInspectionDraft(id: String) {
        _inspectionDrafts.value = _inspectionDrafts.value.filterNot { it.id == id }
    }

    fun clearInspectionDrafts() {
        _inspectionDrafts.value = emptyList()
    }

    fun analyzeInspectionPhotosWithAi(buildingName: String, operatorHint: String) {
        val current = _inspectionDrafts.value
        val withPhotos = current.filter { it.photoPath.isNotBlank() }
        if (withPhotos.isEmpty()) {
            viewModelScope.launch { _events.emit("Attach one or more building inspection photos first.") }
            return
        }
        viewModelScope.launch {
            _isAnalyzingInspection.value = true
            inspectionAi.analyzePhotosBatch(buildingName, current, operatorHint).fold(
                onSuccess = { organized ->
                    _inspectionDrafts.value = organized.sortedBy { it.area.lowercase(Locale.ROOT) }
                    _events.emit("AI organized ${withPhotos.size} photo(s) into inspection areas & findings.")
                },
                onFailure = { err ->
                    _events.emit(err.localizedMessage ?: "AI photo organization failed.")
                }
            )
            _isAnalyzingInspection.value = false
        }
    }

    fun generateBuildingInspectionXlsx(
        buildingName: String,
        inspectionDate: String,
        inspectorName: String,
        openEmailComposerAfter: Boolean = false
    ) {
        val current = _inspectionDrafts.value
        if (current.isEmpty()) {
            viewModelScope.launch { _events.emit("Add at least one inspection photo or finding first.") }
            return
        }
        val cleanBuilding = buildingName.trim().ifBlank { "Masaken Building" }
        val cleanDate = inspectionDate.trim().ifBlank { "27/11/2026" }
        val cleanInspector = inspectorName.trim().ifBlank { "Emmanuel Ssali" }

        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val exportItems = current.sortedBy { it.area.lowercase(Locale.ROOT) }.map { item ->
                    val jpeg = if (item.photoPath.isNotBlank()) {
                        archive.loadScaledJpegForExcel(item.photoPath)
                    } else null
                    InspectionExportItem(
                        id = item.id,
                        area = item.area,
                        finding = item.finding.ifBlank { "Visual check completed" },
                        severity = item.severity,
                        actionRequired = item.actionRequired.ifBlank { "None" },
                        jpegBytes = jpeg
                    )
                }

                val baos = ByteArrayOutputStream()
                XlsxEngine.writeInspectionReportXlsx(
                    output = baos,
                    buildingName = cleanBuilding,
                    inspectionDate = cleanDate,
                    inspectorName = cleanInspector,
                    items = exportItems
                )
                val safeBuildingSlug = cleanBuilding.replace(Regex("[^A-Za-z0-9_-]+"), "_")
                val safeDateSlug = cleanDate.replace('/', '-')
                val fileName = "Inspection_${safeBuildingSlug}_${safeDateSlug}.xlsx"

                val saved = archive.saveExportArtifact(
                    subfolder = ArchiveSubfolder.INSPECTIONS,
                    fileName = fileName,
                    mimeType = LocalFolderArchiveManager.MIME_XLSX,
                    bytes = baos.toByteArray()
                )

                val areaSummary = current.groupBy { it.area.ifBlank { "General" } }
                    .entries
                    .joinToString(", ") { "${it.key} (${it.value.size})" }
                val reportId = "insp_${System.currentTimeMillis()}"
                val entity = InspectionReportEntity(
                    id = reportId,
                    buildingName = cleanBuilding,
                    inspectionDate = cleanDate,
                    inspectorName = cleanInspector,
                    itemCount = current.size,
                    summaryNote = areaSummary.take(180),
                    localFilePath = saved.cacheFile.absolutePath,
                    driveFileId = ""
                )
                repo.saveInspectionReport(entity)
                refreshLocalArchives()

                if (openEmailComposerAfter) {
                    val subject = "Building Inspection Report - $cleanBuilding ($cleanDate)"
                    val urgentCount = current.count { it.severity.equals("Urgent", true) }
                    val minorCount = current.count { it.severity.equals("Minor", true) }
                    val plainBody = buildString {
                        appendLine("Dear All,")
                        appendLine()
                        appendLine("Greetings.")
                        appendLine()
                        appendLine("Kindly find attached the building inspection report (.xlsx with embedded inspection photos) for $cleanBuilding dated $cleanDate.")
                        appendLine()
                        appendLine("Inspection Items   | ${current.size}")
                        appendLine("Urgent Findings    | $urgentCount")
                        appendLine("Minor Findings     | $minorCount")
                        appendLine("Areas Covered      | $areaSummary")
                        appendLine()
                        append(EMMANUEL_SIGNATURE)
                    }
                    val htmlBody = buildHtmlReportEmail(
                        greetingLine = "Kindly find attached the building inspection report (.xlsx with embedded inspection photos) for <b>$cleanBuilding</b> dated <b>$cleanDate</b>.",
                        tableRows = listOf(
                            "Building" to cleanBuilding,
                            "Inspection Items" to current.size.toString(),
                            "Urgent / Minor" to "$urgentCount Urgent / $minorCount Minor",
                            "Areas Covered" to areaSummary
                        )
                    )
                    _pendingEmailDraft.value = PendingEmailDraft(
                        reportType = "INSPECTION",
                        periodLabel = "$cleanBuilding ($cleanDate)",
                        defaultSubject = subject,
                        defaultBody = plainBody,
                        htmlBody = htmlBody,
                        attachments = listOf(saved),
                        inspectionReportId = reportId
                    )
                } else {
                    _events.emit("Saved photo-embedded .xlsx to ${saved.publicDisplayPath}")
                }
            } catch (e: Exception) {
                _events.emit("Inspection export failed: ${e.localizedMessage}")
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    // --- Weekly & Monthly Reports + Mandatory To/CC Email Composer ---

    fun prepareWeeklyReportEmail(
        weekNumber: Int = 48,
        startDateLabel: String = "20 Nov",
        endDateLabel: String = "26 Nov",
        dateIsoStart: String = "2026-11-20",
        dateIsoEnd: String = "2026-11-26"
    ) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val periodVisitors = visitors.value.filter { it.dateIso in dateIsoStart..dateIsoEnd }
                val totalVisitors = periodVisitors.sumOf { it.visitorCount }
                val totalViewings = periodVisitors.sumOf { it.viewingCount }

                val weekFileName = "WSF-PMD-F-09 - Key- Access card Register -Iss 00- 11-2026 Week $weekNumber ( $startDateLabel to $endDateLabel).xlsx"
                val weekKeysBytes = sync.generateKeyRegisterReportXlsxBytes(
                    keysFilter = { true },
                    sheetTitle = "Week $weekNumber Keys"
                )
                val savedFile = archive.saveExportArtifact(
                    subfolder = ArchiveSubfolder.WEEKLY_MONTHLY,
                    fileName = weekFileName,
                    mimeType = LocalFolderArchiveManager.MIME_XLSX,
                    bytes = weekKeysBytes
                )
                refreshLocalArchives()

                val subject = "Weekly keys log report; Week $weekNumber ( $startDateLabel to $endDateLabel)"
                val visitorStr = String.format(Locale.US, "%02d", totalVisitors)
                val viewingStr = String.format(Locale.US, "%02d", totalViewings)

                val plainBody = buildString {
                    appendLine("Dear All,")
                    appendLine()
                    appendLine("Greetings.")
                    appendLine()
                    appendLine("Kindly receive attached weekly keys log report; Week $weekNumber ( $startDateLabel to $endDateLabel).")
                    appendLine()
                    appendLine("Number of visitors              | $visitorStr")
                    appendLine("Number of vacant flat viewings  | $viewingStr")
                    appendLine()
                    append(EMMANUEL_SIGNATURE)
                }
                val htmlBody = buildHtmlReportEmail(
                    greetingLine = "Kindly receive attached weekly keys log report; Week $weekNumber ( $startDateLabel to $endDateLabel).",
                    tableRows = listOf(
                        "Number of visitors" to visitorStr,
                        "Number of vacant flat viewings" to viewingStr
                    )
                )

                _pendingEmailDraft.value = PendingEmailDraft(
                    reportType = "WEEKLY",
                    periodLabel = "Week $weekNumber ($startDateLabel to $endDateLabel)",
                    defaultSubject = subject,
                    defaultBody = plainBody,
                    htmlBody = htmlBody,
                    attachments = listOf(savedFile)
                )
            } catch (e: Exception) {
                _events.emit("Weekly report generation failed: ${e.localizedMessage}")
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    fun prepareMonthlyReportEmail(
        monthKey: String = "2026-11",
        monthLabel: String = "November 2026"
    ) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val monthVisitorsList = visitors.value.filter { it.monthKey == monthKey }
                val totalVisitors = monthVisitorsList.sumOf { it.visitorCount }
                val totalViewings = monthVisitorsList.sumOf { it.viewingCount }
                val allSports = sportsBookings.value
                val fbCount = allSports.count { it.sport.equals("FOOTBALL", ignoreCase = true) }
                val pdCount = allSports.count { it.sport.equals("PADEL", ignoreCase = true) }
                val totalSportsHours = allSports.sumOf { it.durationPlayed }

                val upperMonth = monthLabel.uppercase(Locale.ROOT)
                val mmPart = monthKey.takeLast(2)
                val yyyyPart = monthKey.take(4)

                val keyFileName = "WSF-PMD-F-09 - Key- Access card Register -Iss 00- $mmPart-$yyyyPart END OF MONTH REPORT- $upperMonth.xlsx"
                val visitorsFileName = "PM VISITORS LOG JANUARY TO DECEMBER $yyyyPart.xlsx"
                val sportsFileName = "MM-PADEL-AND-FOOTBALL-COURT-MONTHLY-REPORT-${upperMonth.replace(' ', '-')}.xlsx"

                val savedKeys = archive.saveExportArtifact(
                    subfolder = ArchiveSubfolder.WEEKLY_MONTHLY,
                    fileName = keyFileName,
                    mimeType = LocalFolderArchiveManager.MIME_XLSX,
                    bytes = sync.generateKeyRegisterReportXlsxBytes(sheetTitle = "$upperMonth Keys")
                )
                val savedVisitors = archive.saveExportArtifact(
                    subfolder = ArchiveSubfolder.WEEKLY_MONTHLY,
                    fileName = visitorsFileName,
                    mimeType = LocalFolderArchiveManager.MIME_XLSX,
                    bytes = sync.generateVisitorsReportXlsxBytes(sheetTitle = "PM VISITORS $yyyyPart")
                )
                val savedSports = archive.saveExportArtifact(
                    subfolder = ArchiveSubfolder.WEEKLY_MONTHLY,
                    fileName = sportsFileName,
                    mimeType = LocalFolderArchiveManager.MIME_XLSX,
                    bytes = sync.generateSportsReportXlsxBytes(sheetTitle = monthLabel)
                )
                refreshLocalArchives()

                val subject = "End of month- $monthLabel Key(s) log & Visitors' log reports"
                val sportsLine = "${allSports.size} bookings ($fbCount Football, $pdCount Padel • ${String.format(Locale.US, "%.1f", totalSportsHours)} hrs)"
                val plainBody = buildString {
                    appendLine("Dear All,")
                    appendLine()
                    appendLine("Greetings.")
                    appendLine()
                    appendLine("Please receive attached file for your reference.")
                    appendLine()
                    appendLine("Total number of visitors      | $totalVisitors")
                    appendLine("Total vacant flat viewings    | $totalViewings")
                    appendLine("Football & Padel Court Log    | $sportsLine")
                    appendLine()
                    append(EMMANUEL_SIGNATURE)
                }
                val htmlBody = buildHtmlReportEmail(
                    greetingLine = "Please receive attached file for your reference.",
                    tableRows = listOf(
                        "Total number of visitors" to totalVisitors.toString(),
                        "Total vacant flat viewings" to totalViewings.toString(),
                        "Football & Padel Court Log" to sportsLine
                    )
                )

                _pendingEmailDraft.value = PendingEmailDraft(
                    reportType = "MONTHLY",
                    periodLabel = monthLabel,
                    defaultSubject = subject,
                    defaultBody = plainBody,
                    htmlBody = htmlBody,
                    attachments = listOf(savedKeys, savedVisitors, savedSports)
                )
            } catch (e: Exception) {
                _events.emit("Monthly report generation failed: ${e.localizedMessage}")
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    fun dismissEmailDraft() {
        _pendingEmailDraft.value = null
    }

    fun openBacklogWorkbookXlsx(backlogType: String) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                val (subfolder, fileName, bytes) = when (backlogType) {
                    "SPORTS" -> Triple(
                        ArchiveSubfolder.WEEKLY_MONTHLY,
                        "MM-PADEL-AND-FOOTBALL-COURT-NOVEMBER-2026.xlsx",
                        sync.generateSportsReportXlsxBytes(sheetTitle = "November 2026")
                    )
                    "KEYS" -> Triple(
                        ArchiveSubfolder.KEY_REGISTER,
                        "WSF-PMD-F-07-MM-KEYS-REGISTER-BACKLOG.xlsx",
                        sync.generateKeyRegisterReportXlsxBytes(sheetTitle = "MM Keys Register")
                    )
                    else -> Triple(
                        ArchiveSubfolder.WEEKLY_MONTHLY,
                        "PM-VISITORS-2026-AND-MAINTENANCE-BACKLOG.xlsx",
                        sync.generateVisitorsAndMaintenanceReportXlsxBytes()
                    )
                }

                val saved = archive.saveExportArtifact(
                    subfolder = subfolder,
                    fileName = fileName,
                    mimeType = LocalFolderArchiveManager.MIME_XLSX,
                    bytes = bytes
                )
                refreshLocalArchives()

                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(saved.shareUri, LocalFolderArchiveManager.MIME_XLSX)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launched = try {
                    app.startActivity(viewIntent)
                    true
                } catch (_: ActivityNotFoundException) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = LocalFolderArchiveManager.MIME_XLSX
                        putExtra(Intent.EXTRA_STREAM, saved.shareUri)
                        putExtra(Intent.EXTRA_SUBJECT, saved.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        val chooser = Intent.createChooser(shareIntent, "Open or Share ${saved.fileName}").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        app.startActivity(chooser)
                        true
                    } catch (_: Exception) {
                        false
                    }
                } catch (_: Exception) {
                    false
                }

                if (launched) {
                    _events.emit("Opened ${saved.fileName} (saved to ${saved.publicDisplayPath})")
                } else {
                    _events.emit("Saved ${saved.fileName} to ${saved.publicDisplayPath}")
                }
            } catch (e: Exception) {
                _events.emit("Could not export XLSX: ${e.localizedMessage}")
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    fun openArchivedFileEmailComposer(entry: LocalArchiveEntry) {
        viewModelScope.launch {
            val file = java.io.File(entry.filePath)
            val uri = archive.getShareUriForPath(entry.filePath)
            if (!file.exists() || uri == null) {
                _events.emit("Archived file could not be read.")
                return@launch
            }
            val archived = ArchivedFileResult(
                fileName = entry.fileName,
                subfolder = ArchiveSubfolder.entries.firstOrNull { it.dirName == entry.subfolderName }
                    ?: ArchiveSubfolder.WEEKLY_MONTHLY,
                publicDisplayPath = entry.filePath,
                cacheFile = file,
                shareUri = uri,
                mimeType = entry.mimeType,
                sizeBytes = entry.sizeBytes
            )
            _pendingEmailDraft.value = PendingEmailDraft(
                reportType = if (entry.subfolderName == ArchiveSubfolder.INSPECTIONS.dirName) "INSPECTION" else "WEEKLY",
                periodLabel = entry.fileName,
                defaultSubject = "Masaken Mesaimeer Ops Report - ${entry.fileName.substringBeforeLast('.')}",
                defaultBody = "Dear All,\n\nGreetings.\n\nPlease receive attached file for your reference.\n\n$EMMANUEL_SIGNATURE",
                htmlBody = buildHtmlReportEmail(
                    greetingLine = "Please receive attached file for your reference.",
                    tableRows = listOf("Attachment" to entry.fileName)
                ),
                attachments = listOf(archived)
            )
        }
    }

    fun sendDraftViaGmailApi(
        activity: Activity,
        draft: PendingEmailDraft,
        toInput: String,
        ccInput: String,
        subject: String,
        bodyText: String,
        alsoUploadToDrive: Boolean
    ) {
        val toList = parseEmailList(toInput)
        val ccList = parseEmailList(ccInput)
        if (toList.isEmpty()) {
            viewModelScope.launch { _events.emit("Please enter at least one To email recipient.") }
            return
        }
        val task = PendingWorkspaceTask.SendEmailAndUpload(
            draft = draft,
            toAddresses = toList,
            ccAddresses = ccList,
            subject = subject.trim(),
            bodyText = bodyText,
            alsoUploadDrive = alsoUploadToDrive
        )
        runWithWorkspaceAuth(activity, task)
    }

    fun uploadDraftToDriveOnly(activity: Activity, draft: PendingEmailDraft) {
        runWithWorkspaceAuth(activity, PendingWorkspaceTask.UploadDraftToDrive(draft))
    }

    fun backupFullSnapshotToPhoneAndDrive(activity: Activity) {
        runWithWorkspaceAuth(activity, PendingWorkspaceTask.BackupFullSnapshotToDrive)
    }

    fun dispatchViaMailAppChooser(
        draft: PendingEmailDraft,
        toInput: String,
        ccInput: String,
        subject: String,
        bodyText: String
    ) {
        val toList = parseEmailList(toInput)
        val ccList = parseEmailList(ccInput)
        if (toList.isEmpty()) {
            viewModelScope.launch { _events.emit("Please enter at least one To email recipient before opening Mail App.") }
            return
        }
        val uris = draft.attachments.map { it.shareUri }
        val launched = workspace.launchShareEmailChooser(
            context = app,
            toAddresses = toList,
            ccAddresses = ccList,
            subject = subject.trim(),
            plainBody = bodyText,
            attachmentUris = uris
        )
        viewModelScope.launch {
            if (launched) {
                val dispatch = ReportDispatchEntity(
                    id = "disp_${System.currentTimeMillis()}",
                    reportType = draft.reportType,
                    subject = subject.trim(),
                    periodLabel = draft.periodLabel,
                    recipientsTo = toList.joinToString(", "),
                    recipientsCc = ccList.joinToString(", "),
                    attachmentNames = draft.attachments.joinToString(", ") { it.fileName },
                    localPaths = draft.attachments.joinToString(", ") { it.publicDisplayPath },
                    driveStatus = "Local Share",
                    dispatchChannel = "MAIL_APP_INTENT"
                )
                repo.saveReportDispatch(dispatch)
                _pendingEmailDraft.value = null
                _events.emit("Opened Mail App with ${draft.attachments.size} attachment(s).")
            } else {
                _events.emit("No email app found on device. Use 'Send Directly (Gmail API)' instead.")
            }
        }
    }

    private fun runWithWorkspaceAuth(activity: Activity, task: PendingWorkspaceTask) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            pendingWorkspaceTask = task
            when (val auth = workspace.authorizeScopes(activity)) {
                is WorkspaceAuthOutcome.Authorized -> {
                    executeWorkspaceTask(auth.accessToken, task)
                }
                is WorkspaceAuthOutcome.NeedsResolution -> {
                    _isGeneratingReport.value = false
                    _workspaceAuthLauncherRequest.emit(auth.intentSenderRequest)
                }
                is WorkspaceAuthOutcome.Error -> {
                    _isGeneratingReport.value = false
                    pendingWorkspaceTask = null
                    _events.emit(auth.message)
                }
            }
        }
    }

    fun onWorkspaceAuthResolutionResult(activity: Activity, data: Intent?) {
        val task = pendingWorkspaceTask ?: return
        viewModelScope.launch {
            _isGeneratingReport.value = true
            when (val res = workspace.handleAuthorizationResult(activity, data)) {
                is WorkspaceAuthOutcome.Authorized -> executeWorkspaceTask(res.accessToken, task)
                is WorkspaceAuthOutcome.NeedsResolution -> {
                    _isGeneratingReport.value = false
                    _events.emit("Additional Google Workspace permission needed.")
                }
                is WorkspaceAuthOutcome.Error -> {
                    _isGeneratingReport.value = false
                    pendingWorkspaceTask = null
                    _events.emit(res.message)
                }
            }
        }
    }

    private suspend fun executeWorkspaceTask(accessToken: String, task: PendingWorkspaceTask) {
        try {
            when (task) {
                is PendingWorkspaceTask.SendEmailAndUpload -> {
                    val draft = task.draft
                    val attachmentsBytes = draft.attachments.mapNotNull { file ->
                        if (file.cacheFile.exists()) {
                            EmailAttachmentBytes(
                                fileName = file.fileName,
                                mimeType = file.mimeType,
                                bytes = file.cacheFile.readBytes()
                            )
                        } else null
                    }
                    var driveStatus = "Skipped"
                    var firstDriveId = ""
                    if (task.alsoUploadDrive) {
                        val uploadedIds = mutableListOf<String>()
                        for (file in draft.attachments) {
                            val bytes = if (file.cacheFile.exists()) file.cacheFile.readBytes() else continue
                            val up = workspace.uploadToDriveFolder(
                                accessToken = accessToken,
                                subfolderName = file.subfolder.dirName,
                                fileName = file.fileName,
                                mimeType = file.mimeType,
                                bytes = bytes
                            )
                            up.onSuccess {
                                uploadedIds.add(it.fileId)
                                if (firstDriveId.isBlank()) firstDriveId = it.fileId
                            }
                        }
                        driveStatus = if (uploadedIds.size == draft.attachments.size) {
                            "Uploaded (${uploadedIds.size} file(s))"
                        } else {
                            "Partial (${uploadedIds.size}/${draft.attachments.size})"
                        }
                    }

                    val gmailResult = workspace.sendEmailViaGmailApi(
                        accessToken = accessToken,
                        toAddresses = task.toAddresses,
                        ccAddresses = task.ccAddresses,
                        subject = task.subject,
                        plainBody = task.bodyText,
                        htmlBody = draft.htmlBody,
                        attachments = attachmentsBytes
                    )
                    gmailResult.fold(
                        onSuccess = {
                            if (draft.inspectionReportId != null && firstDriveId.isNotBlank()) {
                                val existing = inspectionReports.value.firstOrNull { it.id == draft.inspectionReportId }
                                if (existing != null) {
                                    repo.saveInspectionReport(existing.copy(driveFileId = firstDriveId))
                                }
                            }
                            repo.saveReportDispatch(
                                ReportDispatchEntity(
                                    id = "disp_${System.currentTimeMillis()}",
                                    reportType = draft.reportType,
                                    subject = task.subject,
                                    periodLabel = draft.periodLabel,
                                    recipientsTo = task.toAddresses.joinToString(", "),
                                    recipientsCc = task.ccAddresses.joinToString(", "),
                                    attachmentNames = draft.attachments.joinToString(", ") { it.fileName },
                                    localPaths = draft.attachments.joinToString(", ") { it.publicDisplayPath },
                                    driveStatus = driveStatus,
                                    dispatchChannel = "GMAIL_API"
                                )
                            )
                            _pendingEmailDraft.value = null
                            _events.emit("Sent via Gmail to ${task.toAddresses.joinToString(", ")} • Drive: $driveStatus")
                        },
                        onFailure = { err ->
                            _events.emit(err.localizedMessage ?: "Gmail send failed.")
                        }
                    )
                }

                is PendingWorkspaceTask.UploadDraftToDrive -> {
                    val draft = task.draft
                    var uploadedCount = 0
                    var firstId = ""
                    draft.attachments.forEach { file ->
                        if (file.cacheFile.exists()) {
                            workspace.uploadToDriveFolder(
                                accessToken = accessToken,
                                subfolderName = file.subfolder.dirName,
                                fileName = file.fileName,
                                mimeType = file.mimeType,
                                bytes = file.cacheFile.readBytes()
                            ).onSuccess {
                                uploadedCount++
                                if (firstId.isBlank()) firstId = it.fileId
                            }
                        }
                    }
                    if (draft.inspectionReportId != null && firstId.isNotBlank()) {
                        val existing = inspectionReports.value.firstOrNull { it.id == draft.inspectionReportId }
                        if (existing != null) {
                            repo.saveInspectionReport(existing.copy(driveFileId = firstId))
                        }
                    }
                    repo.saveReportDispatch(
                        ReportDispatchEntity(
                            id = "disp_${System.currentTimeMillis()}",
                            reportType = draft.reportType,
                            subject = draft.defaultSubject,
                            periodLabel = draft.periodLabel,
                            recipientsTo = "-",
                            recipientsCc = "-",
                            attachmentNames = draft.attachments.joinToString(", ") { it.fileName },
                            localPaths = draft.attachments.joinToString(", ") { it.publicDisplayPath },
                            driveStatus = "Uploaded ($uploadedCount/${draft.attachments.size})",
                            dispatchChannel = "DRIVE_ARCHIVE_ONLY"
                        )
                    )
                    _pendingEmailDraft.value = null
                    _events.emit("Saved & uploaded $uploadedCount/${draft.attachments.size} file(s) to Google Drive.")
                }

                is PendingWorkspaceTask.BackupFullSnapshotToDrive -> {
                    val bytes = sync.buildFullSnapshotJsonBytes()
                    val dateStamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                    val fileName = "Masaken_Mesaimeer_Backup_$dateStamp.json"
                    val archived = archive.saveExportArtifact(
                        subfolder = ArchiveSubfolder.BACKUPS,
                        fileName = fileName,
                        mimeType = LocalFolderArchiveManager.MIME_JSON,
                        bytes = bytes
                    )
                    refreshLocalArchives()
                    workspace.uploadToDriveFolder(
                        accessToken = accessToken,
                        subfolderName = ArchiveSubfolder.BACKUPS.dirName,
                        fileName = fileName,
                        mimeType = LocalFolderArchiveManager.MIME_JSON,
                        bytes = bytes
                    ).fold(
                        onSuccess = {
                            _events.emit("Backup saved locally (${archived.publicDisplayPath}) & uploaded to Drive.")
                        },
                        onFailure = { err ->
                            _events.emit("Saved locally, Drive backup failed: ${err.localizedMessage}")
                        }
                    )
                }
            }
        } finally {
            pendingWorkspaceTask = null
            _isGeneratingReport.value = false
        }
    }

    fun refreshLocalArchives() {
        viewModelScope.launch {
            _localArchiveFiles.value = archive.listCachedArchives()
        }
    }

    private fun parseEmailList(raw: String): List<String> =
        raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.contains('@') && it.contains('.') }

    private fun buildHtmlReportEmail(
        greetingLine: String,
        tableRows: List<Pair<String, String>>
    ): String = buildString {
        append("<div style=\"font-family:Calibri,Arial,sans-serif;font-size:14px;color:#111827;\">")
        append("<p>Dear All,</p>")
        append("<p>Greetings.</p>")
        append("<p>$greetingLine</p>")
        append("<table style=\"border-collapse:collapse;width:460px;margin:12px 0;\">")
        tableRows.forEach { (k, v) ->
            append("<tr>")
            append("<td style=\"border:1px solid #94A3B8;padding:6px 10px;\">$k</td>")
            append("<td style=\"border:1px solid #94A3B8;padding:6px 10px;text-align:right;font-weight:bold;\">$v</td>")
            append("</tr>")
        }
        append("</table>")
        append("<p>Thanks and Kind Regards,<br/>")
        append("<b>EMMANUEL SSALI</b><br/>")
        append("Masaken Mesaimeer &ndash; Security Team Leader<br/>")
        append("<b>Falcon Eye Security Services.</b><br/>")
        append("Mobile : 7731 3544<br/>")
        append("Email: falconeyes115@gmail.com</p>")
        append("</div>")
    }

    // --- Existing Booking & Workbook Ingest Parsers ---

    fun parseTextOffline(rawText: String, attachmentPath: String?): ParsedBookingDraft =
        parser.parseTextOffline(rawText, attachmentPath)

    suspend fun savePickedBookingPhoto(uri: Uri): String? =
        parser.savePickedPhotoLocally(uri)

    fun extractBookingWithGeminiVision(imagePath: String, hint: String) {
        viewModelScope.launch {
            _aiVisionState.value = AiVisionUiState.Extracting
            val result = parser.extractFromPhotoWithGemini(imagePath, hint)
            result.fold(
                onSuccess = { draft -> _aiVisionState.value = AiVisionUiState.Extracted(draft) },
                onFailure = { err ->
                    _aiVisionState.value = AiVisionUiState.Error(
                        err.localizedMessage ?: "Failed to parse booking photo."
                    )
                }
            )
        }
    }

    fun resetAiVisionState() {
        _aiVisionState.value = AiVisionUiState.Idle
    }

    fun resetIngestState() {
        _ingestState.value = RealTimeIngestUiState.Idle
    }

    fun previewWorkbookFile(uri: Uri, targetHint: IngestTarget) {
        viewModelScope.launch {
            _ingestState.value = RealTimeIngestUiState.Processing
            sync.previewImportFromUri(uri, targetHint).fold(
                onSuccess = { preview ->
                    if (preview.totalRows == 0) {
                        _ingestState.value = RealTimeIngestUiState.Error(
                            "No matching rows detected in ${preview.sourceLabel}. Select the target sheet chip above and retry."
                        )
                    } else {
                        _ingestState.value = RealTimeIngestUiState.PreviewReady(preview)
                    }
                },
                onFailure = { err ->
                    _ingestState.value = RealTimeIngestUiState.Error(
                        err.localizedMessage ?: "Unable to parse selected file."
                    )
                }
            )
        }
    }

    fun extractIngestFromPhoto(imagePath: String, target: IngestTarget, hint: String) {
        viewModelScope.launch {
            _ingestState.value = RealTimeIngestUiState.Processing
            val nextKeySi = repo.getNextKeySi()
            val nextMaintSn = (maintenanceRequests.value.maxOfOrNull { it.sn } ?: 0) + 1
            val nextSportsSn = (sportsBookings.value.maxOfOrNull { it.sn } ?: 0) + 1

            universalPhotoIngest.extractRowsFromPhoto(
                imagePath = imagePath,
                target = target,
                nextKeySi = nextKeySi,
                nextMaintenanceSn = nextMaintSn,
                nextSportsSn = nextSportsSn,
                hint = hint
            ).fold(
                onSuccess = { bundle ->
                    val existingSis = keys.value.map { it.si }.toSet()
                    val updatedKeys = bundle.keys.count { it.si in existingSis }
                    val preview = ImportPreviewResult(
                        sourceLabel = "Photo (${target.label})",
                        bundle = bundle,
                        newKeysCount = bundle.keys.size - updatedKeys,
                        updatedKeysCount = updatedKeys,
                        visitorsUpsertCount = bundle.visitors.size,
                        maintenanceUpsertCount = bundle.maintenance.size,
                        sportsUpsertCount = bundle.sports.size
                    )
                    if (preview.totalRows == 0) {
                        _ingestState.value = RealTimeIngestUiState.Error("No structured rows found in photo. Try adding a short note or selecting the exact target workbook.")
                    } else {
                        _ingestState.value = RealTimeIngestUiState.PreviewReady(preview)
                    }
                },
                onFailure = { err ->
                    _ingestState.value = RealTimeIngestUiState.Error(
                        err.localizedMessage ?: "Photo extraction failed."
                    )
                }
            )
        }
    }

    fun createOfflineDraftFromNote(note: String, target: IngestTarget, attachmentPath: String?) {
        viewModelScope.launch {
            val effectiveTarget = if (target == IngestTarget.AUTO) IngestTarget.SPORTS else target
            val bundle = when (effectiveTarget) {
                IngestTarget.SPORTS, IngestTarget.AUTO -> {
                    val d = parser.parseTextOffline(note, attachmentPath)
                    val sn = (sportsBookings.value.maxOfOrNull { it.sn } ?: 0) + 1
                    ImportedWorkbookBundle(
                        sports = listOf(
                            SportsBookingEntity(
                                sn = sn,
                                sport = d.sport,
                                date = d.date,
                                time = d.time,
                                name = d.name.ifBlank { "Walk-in Resident" },
                                unitDetails = d.unitDetails.ifBlank { "M-A04-16" },
                                discount = d.discount,
                                referenceNo = d.referenceNo,
                                durationPlayed = d.durationPlayed,
                                remarks = d.remarks,
                                attachmentPath = attachmentPath
                            )
                        )
                    )
                }
                IngestTarget.KEYS -> {
                    val si = repo.getNextKeySi()
                    val unitRegex = Regex("""\b[Mm]-?[A-Za-z]\d{2}-\d{2}\b""")
                    val unit = unitRegex.find(note)?.value?.uppercase() ?: "M-A04-16"
                    val isMoveIn = KeyStatus.isMoveInPurpose(note)
                    val isReturned = note.contains("returned", ignoreCase = true) && !note.contains("not yet returned", true)
                    val status = when {
                        isMoveIn && !isReturned -> KeyStatus.MOVE_IN_PENDING_TCODE
                        isReturned -> KeyStatus.RETURNED
                        else -> KeyStatus.ISSUED
                    }
                    ImportedWorkbookBundle(
                        keys = listOf(
                            KeyRegisterEntity(
                                si = si,
                                dateOut = "26/11/2026",
                                timeOut = "16:36",
                                flatUnitLocation = unit,
                                keyCardType = "Apartment Key Set",
                                purpose = note.take(40).ifBlank { "Operations Issue" },
                                issuedTo = if (isMoveIn) "Pending T-code" else "Desk Entry",
                                qidOrNote = if (isMoveIn) "T-code follow-up required from Mr. Shajith" else "",
                                dateIn = if (isReturned) "11/26/2026" else "",
                                timeIn = if (isReturned) "16:36" else "",
                                returnedBy = if (isReturned) "Desk Entry" else "",
                                remarks = note,
                                status = status,
                                issuedBy = if (isMoveIn) "Mr. Shajith" else "Mr. Charles"
                            )
                        )
                    )
                }
                IngestTarget.VISITORS -> {
                    val nums = Regex("""\d+""").findAll(note).mapNotNull { it.value.toIntOrNull() }.toList()
                    ImportedWorkbookBundle(
                        visitors = listOf(
                            VisitorDayEntity(
                                dateIso = "2026-11-26",
                                displayDate = "26-Nov-2026",
                                monthKey = "2026-11",
                                visitorCount = nums.getOrElse(0) { 1 },
                                viewingCount = nums.getOrElse(1) { 0 },
                                flatDetails = Regex("""\b[Mm]-?[A-Za-z]\d{2}-\d{2}\b""").find(note)?.value?.uppercase() ?: "-",
                                notes = note
                            )
                        )
                    )
                }
                IngestTarget.MAINTENANCE -> {
                    val sn = (maintenanceRequests.value.maxOfOrNull { it.sn } ?: 0) + 1
                    val unit = Regex("""\b[Mm]-?[A-Za-z]\d{2}-\d{2}\b""").find(note)?.value?.uppercase() ?: "M-A04-16"
                    ImportedWorkbookBundle(
                        maintenance = listOf(
                            MaintenanceRequestEntity(
                                sn = sn,
                                date = "26/11/2026",
                                names = "Resident Request",
                                phoneNumber = "+974",
                                workOrderNumber = "WO-2026-${1189 + sn}",
                                qidNumber = "",
                                flatDetails = unit,
                                requestDetails = note.ifBlank { "Logged from desk note" }
                            )
                        )
                    )
                }
            }
            val existingSis = keys.value.map { it.si }.toSet()
            val updatedKeys = bundle.keys.count { it.si in existingSis }
            _ingestState.value = RealTimeIngestUiState.PreviewReady(
                ImportPreviewResult(
                    sourceLabel = "Offline Note (${effectiveTarget.label})",
                    bundle = bundle,
                    newKeysCount = bundle.keys.size - updatedKeys,
                    updatedKeysCount = updatedKeys,
                    visitorsUpsertCount = bundle.visitors.size,
                    maintenanceUpsertCount = bundle.maintenance.size,
                    sportsUpsertCount = bundle.sports.size
                )
            )
        }
    }

    fun commitIngestPreview(bundle: ImportedWorkbookBundle) {
        viewModelScope.launch {
            sync.commitBundle(bundle, repo).fold(
                onSuccess = {
                    _ingestState.value = RealTimeIngestUiState.Idle
                    _events.emit(it.message)
                    autoMirrorRtdbIfConfigured()
                },
                onFailure = { err ->
                    _ingestState.value = RealTimeIngestUiState.Error(
                        err.localizedMessage ?: "Failed to apply update."
                    )
                }
            )
        }
    }

    fun exportXlsx(uri: Uri) {
        viewModelScope.launch {
            sync.exportXlsxToUri(uri).fold(
                onSuccess = { _events.emit(it.message) },
                onFailure = { _events.emit("XLSX export failed: ${it.localizedMessage}") }
            )
        }
    }

    fun exportCsv(uri: Uri, targetWorkbook: String) {
        viewModelScope.launch {
            sync.exportCsvToUri(uri, targetWorkbook).fold(
                onSuccess = { _events.emit(it.message) },
                onFailure = { _events.emit("CSV export failed: ${it.localizedMessage}") }
            )
        }
    }

    fun exportJsonBackup(uri: Uri) {
        viewModelScope.launch {
            sync.exportJsonSnapshotToUri(uri).fold(
                onSuccess = { _events.emit(it.message) },
                onFailure = { _events.emit("JSON export failed: ${it.localizedMessage}") }
            )
        }
    }

    companion object {
        const val EMMANUEL_SIGNATURE =
            "Thanks and Kind Regards,\n" +
                "EMMANUEL SSALI\n" +
                "Masaken Mesaimeer \u2013 Security Team Leader\n" +
                "Falcon Eye Security Services.\n" +
                "Mobile : 7731 3544\n" +
                "Email: falconeyes115@gmail.com"
    }
}
