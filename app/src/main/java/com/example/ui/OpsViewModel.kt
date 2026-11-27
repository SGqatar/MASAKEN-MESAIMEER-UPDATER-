package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MasakenOpsApp
import com.example.data.ai.ParsedBookingDraft
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.OpsReminderEntity
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import com.example.data.sync.ImportPreviewResult
import com.example.data.sync.ImportedWorkbookBundle
import com.example.data.sync.IngestTarget
import com.example.data.cloud.CloudSyncState
import com.example.notifications.OpsNotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class KeyFilterMode(val label: String) {
    ALL("All"),
    UNRETURNED("Unreturned / Active"),
    RETURNED("Returned"),
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

class OpsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MasakenOpsApp
    private val repo = app.repository
    private val cloudSync = app.firestoreSyncRepository
    private val sync = app.workbookSyncManager
    private val parser = app.bookingParserService
    private val universalPhotoIngest = app.universalPhotoIngestService

    val cloudSyncState: StateFlow<CloudSyncState> = cloudSync.syncState

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

    private val _keyFilter = MutableStateFlow(KeyFilterMode.ALL)
    val keyFilter: StateFlow<KeyFilterMode> = _keyFilter.asStateFlow()

    private val _keyQuery = MutableStateFlow("")
    val keyQuery: StateFlow<String> = _keyQuery.asStateFlow()

    val filteredKeys: StateFlow<List<KeyRegisterEntity>> = combine(
        keys,
        _keyFilter,
        _keyQuery
    ) { list, filter, query ->
        val q = query.trim().lowercase()
        list.filter { item ->
            val matchesFilter = when (filter) {
                KeyFilterMode.ALL -> true
                KeyFilterMode.UNRETURNED -> item.isUnreturnedAlert
                KeyFilterMode.RETURNED -> !item.isUnreturnedAlert
                KeyFilterMode.NEEDS_NOV_RECONCILE -> item.needsNovemberReconciliation
            }
            val matchesQuery = q.isBlank() ||
                item.si.toString().contains(q) ||
                item.flatUnitLocation.lowercase().contains(q) ||
                item.issuedTo.lowercase().contains(q) ||
                item.qidOrNote.lowercase().contains(q) ||
                item.keyCardType.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sportFilter = MutableStateFlow(SportFilter.ALL)
    val sportFilter: StateFlow<SportFilter> = _sportFilter.asStateFlow()

    private val _sportQuery = MutableStateFlow("")
    val sportQuery: StateFlow<String> = _sportQuery.asStateFlow()

    val filteredSports: StateFlow<List<SportsBookingEntity>> = combine(
        sportsBookings,
        _sportFilter,
        _sportQuery
    ) { list, filter, query ->
        val q = query.trim().lowercase()
        list.filter { item ->
            val matchesFilter = when (filter) {
                SportFilter.ALL -> true
                SportFilter.FOOTBALL -> item.sport.equals("FOOTBALL", ignoreCase = true)
                SportFilter.PADEL -> item.sport.equals("PADEL", ignoreCase = true)
            }
            val matchesQuery = q.isBlank() ||
                item.name.lowercase().contains(q) ||
                item.unitDetails.lowercase().contains(q) ||
                item.referenceNo.lowercase().contains(q) ||
                item.date.lowercase().contains(q) ||
                item.sn.toString() == q
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _aiVisionState = MutableStateFlow<AiVisionUiState>(AiVisionUiState.Idle)
    val aiVisionState: StateFlow<AiVisionUiState> = _aiVisionState.asStateFlow()

    private val _ingestState = MutableStateFlow<RealTimeIngestUiState>(RealTimeIngestUiState.Idle)
    val ingestState: StateFlow<RealTimeIngestUiState> = _ingestState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            cloudSync.startSessionSync()
        }
    }

    override fun onCleared() {
        cloudSync.detachListeners()
        super.onCleared()
    }

    fun setKeyFilter(mode: KeyFilterMode) {
        _keyFilter.value = mode
    }

    fun setKeyQuery(query: String) {
        _keyQuery.value = query
    }

    fun setSportFilter(filter: SportFilter) {
        _sportFilter.value = filter
    }

    fun setSportQuery(query: String) {
        _sportQuery.value = query
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
                _events.emit("Saved SI ${entry.si} • Next-day return reminder scheduled.")
            } else {
                _events.emit("Saved SI ${entry.si} (${entry.status}).")
            }
        }
    }

    fun markKeyReturned(entry: KeyRegisterEntity, returnedBy: String, dateIn: String, timeIn: String) {
        viewModelScope.launch {
            repo.markKeyReturned(entry, returnedBy, dateIn, timeIn)
            _events.emit("SI ${entry.si} marked returned • Alert cleared.")
        }
    }

    fun reconcileNovemberKeys() {
        viewModelScope.launch {
            val count = repo.reconcileNovemberReturnedRows()
            if (count > 0) {
                _events.emit("Reconciled $count November returned row(s) with 11/26/2026 at 16:36.")
            } else {
                _events.emit("All November returned rows already have complete return timestamps.")
            }
        }
    }

    fun incrementTodayVisitor() {
        viewModelScope.launch {
            repo.incrementTodayVisitor()
            _events.emit("+1 Visitor logged for 26-Nov-2026.")
        }
    }

    fun incrementTodayViewing(flatDetails: String) {
        viewModelScope.launch {
            repo.incrementTodayViewing(flatDetails)
            _events.emit("+1 Viewing (${flatDetails.ifBlank { "Unit" }}) logged for 26-Nov-2026.")
        }
    }

    fun saveVisitorDay(day: VisitorDayEntity) {
        viewModelScope.launch {
            repo.saveVisitorDay(day)
            _events.emit("Updated PM Visitor log for ${day.displayDate}.")
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
        }
    }

    fun deleteSportsBooking(sn: Int) {
        viewModelScope.launch {
            repo.deleteSportsBooking(sn)
            _events.emit("Removed sports booking SN $sn.")
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

    // Real-time multi-workbook XLSX / CSV / JSON / Photo ingest
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
                    val isReturned = note.contains("returned", ignoreCase = true) && !note.contains("not yet returned", true)
                    ImportedWorkbookBundle(
                        keys = listOf(
                            KeyRegisterEntity(
                                si = si,
                                dateOut = "26/11/2026",
                                timeOut = "16:36",
                                flatUnitLocation = unit,
                                keyCardType = "Apartment Key Set",
                                purpose = note.take(40).ifBlank { "Operations Issue" },
                                issuedTo = "Desk Entry",
                                qidOrNote = "",
                                dateIn = if (isReturned) "11/26/2026" else "",
                                timeIn = if (isReturned) "16:36" else "",
                                returnedBy = if (isReturned) "Desk Entry" else "",
                                remarks = note,
                                status = if (isReturned) KeyStatus.RETURNED else KeyStatus.ISSUED
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
}
