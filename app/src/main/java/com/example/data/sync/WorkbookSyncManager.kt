package com.example.data.sync

import android.content.Context
import android.net.Uri
import com.example.data.OperationsRepository
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.MasakenDatabase
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

enum class IngestTarget(val label: String) {
    AUTO("Auto-Detect"),
    KEYS("Key Register"),
    VISITORS("PM Visitors"),
    MAINTENANCE("Maintenance"),
    SPORTS("Padel & Football")
}

data class ImportPreviewResult(
    val sourceLabel: String,
    val bundle: ImportedWorkbookBundle,
    val newKeysCount: Int,
    val updatedKeysCount: Int,
    val visitorsUpsertCount: Int,
    val maintenanceUpsertCount: Int,
    val sportsUpsertCount: Int
) {
    val totalRows: Int
        get() = bundle.keys.size + bundle.visitors.size + bundle.maintenance.size + bundle.sports.size
}

data class SyncResult(
    val message: String,
    val keysCount: Int = 0,
    val visitorsCount: Int = 0,
    val maintenanceCount: Int = 0,
    val sportsCount: Int = 0,
    val skippedTestCount: Int = 0
)

class WorkbookSyncManager(
    private val context: Context,
    private val db: MasakenDatabase
) {

    suspend fun exportXlsxToUri(uri: Uri): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val keys = db.keyRegisterDao().getAllOnce()
            val visitors = db.visitorDayDao().getAllOnce()
            val maintenance = db.maintenanceRequestDao().getAllOnce()
            val sports = db.sportsBookingDao().getAllAscendingOnce()

            context.contentResolver.openOutputStream(uri)?.use { out ->
                XlsxEngine.writeAllWorkbooksXlsx(out, keys, visitors, maintenance, sports)
            } ?: return@withContext Result.failure(IllegalStateException("Could not open output file."))

            Result.success(
                SyncResult(
                    message = "Exported .xlsx workbook (${keys.size} keys, ${visitors.size} visitor days, ${maintenance.size} work orders, ${sports.size} sports).",
                    keysCount = keys.size,
                    visitorsCount = visitors.size,
                    maintenanceCount = maintenance.size,
                    sportsCount = sports.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportCsvToUri(uri: Uri, targetWorkbook: String): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val csv = buildString {
                when (targetWorkbook) {
                    "keys" -> {
                        appendLine("SI,Date Out,Time Out,Flat / Unit / Location,Key / Card Type,Purpose,Issued To (Q-Out),QID / ID or Note,Date In,Time In,Returned By (Q-In),Remarks,Status")
                        db.keyRegisterDao().getAllOnce().sortedBy { it.si }.forEach { k ->
                            appendLine(csvRow(k.si, k.dateOut, k.timeOut, k.flatUnitLocation, k.keyCardType, k.purpose, k.issuedTo, k.qidOrNote, k.dateIn, k.timeIn, k.returnedBy, k.remarks, k.status))
                        }
                    }
                    "visitors" -> {
                        appendLine("Date,Number of Visitors,Apartment Viewing,Flat Details,Notes")
                        db.visitorDayDao().getAllOnce().sortedBy { it.dateIso }.forEach { v ->
                            appendLine(csvRow(v.displayDate, v.visitorCount, v.viewingCount, v.flatDetails, v.notes))
                        }
                    }
                    "maintenance" -> {
                        appendLine("SN,Date,Names,Phone Number,Work Order Number,Q.I.D Number,Flat Details,Maintenance Request Details")
                        db.maintenanceRequestDao().getAllOnce().sortedBy { it.sn }.forEach { m ->
                            appendLine(csvRow(m.sn, m.date, m.names, m.phoneNumber, m.workOrderNumber, m.qidNumber, m.flatDetails, m.requestDetails))
                        }
                    }
                    else -> {
                        appendLine("SN,SPORT,DATE,TIME,NAME,UNIT DETAILS,DISCOUNT,REFERENCE NO,DURATION PLAYED,REMARKS")
                        db.sportsBookingDao().getAllAscendingOnce().forEach { s ->
                            appendLine(csvRow(s.sn, s.sport, s.date, s.time, s.name, s.unitDetails, s.discount, s.referenceNo, s.durationPlayed, s.remarks))
                        }
                    }
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(csv.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Could not open CSV output."))

            Result.success(SyncResult("Exported $targetWorkbook CSV ledger successfully."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportJsonSnapshotToUri(uri: Uri): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val keys = db.keyRegisterDao().getAllOnce()
            val visitors = db.visitorDayDao().getAllOnce()
            val maintenance = db.maintenanceRequestDao().getAllOnce()
            val sports = db.sportsBookingDao().getAllAscendingOnce()

            val root = JSONObject().apply {
                put("property", "Masaken Mesaimeer (Doha, Qatar)")
                put("exportedAtMillis", System.currentTimeMillis())
                put("keyRegister", JSONArray().apply {
                    keys.forEach { k ->
                        put(JSONObject().apply {
                            put("si", k.si)
                            put("dateOut", k.dateOut)
                            put("timeOut", k.timeOut)
                            put("flatUnitLocation", k.flatUnitLocation)
                            put("keyCardType", k.keyCardType)
                            put("purpose", k.purpose)
                            put("issuedTo", k.issuedTo)
                            put("qidOrNote", k.qidOrNote)
                            put("dateIn", k.dateIn)
                            put("timeIn", k.timeIn)
                            put("returnedBy", k.returnedBy)
                            put("remarks", k.remarks)
                            put("status", k.status)
                        })
                    }
                })
                put("pmVisitors", JSONArray().apply {
                    visitors.forEach { v ->
                        put(JSONObject().apply {
                            put("dateIso", v.dateIso)
                            put("displayDate", v.displayDate)
                            put("monthKey", v.monthKey)
                            put("visitorCount", v.visitorCount)
                            put("viewingCount", v.viewingCount)
                            put("flatDetails", v.flatDetails)
                            put("notes", v.notes)
                        })
                    }
                })
                put("maintenanceLog", JSONArray().apply {
                    maintenance.forEach { m ->
                        put(JSONObject().apply {
                            put("sn", m.sn)
                            put("date", m.date)
                            put("names", m.names)
                            put("phoneNumber", m.phoneNumber)
                            put("workOrderNumber", m.workOrderNumber)
                            put("qidNumber", m.qidNumber)
                            put("flatDetails", m.flatDetails)
                            put("requestDetails", m.requestDetails)
                        })
                    }
                })
                put("padelFootball", JSONArray().apply {
                    sports.forEach { s ->
                        put(JSONObject().apply {
                            put("sn", s.sn)
                            put("sport", s.sport)
                            put("date", s.date)
                            put("time", s.time)
                            put("name", s.name)
                            put("unitDetails", s.unitDetails)
                            put("discount", s.discount)
                            put("referenceNo", s.referenceNo)
                            put("durationPlayed", s.durationPlayed)
                            put("remarks", s.remarks)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Could not open JSON output."))

            Result.success(
                SyncResult(
                    message = "Exported full JSON backup (${keys.size} keys, ${sports.size} sports bookings).",
                    keysCount = keys.size,
                    visitorsCount = visitors.size,
                    maintenanceCount = maintenance.size,
                    sportsCount = sports.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun previewImportFromUri(
        uri: Uri,
        targetHint: IngestTarget = IngestTarget.AUTO
    ): Result<ImportPreviewResult> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
            val path = uri.lastPathSegment ?: uri.toString()
            val lowerPath = path.lowercase(Locale.ROOT)

            val bundle = when {
                mime.contains("spreadsheet") || lowerPath.endsWith(".xlsx") -> {
                    context.contentResolver.openInputStream(uri)?.use { XlsxEngine.readXlsx(it) }
                        ?: return@withContext Result.failure(IllegalStateException("Unable to read .xlsx file."))
                }
                mime.contains("json") || lowerPath.endsWith(".json") -> {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@withContext Result.failure(IllegalStateException("Unable to read .json file."))
                    parseJsonBundle(text)
                }
                else -> {
                    val lines = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readLines()
                    } ?: return@withContext Result.failure(IllegalStateException("Unable to read .csv file."))
                    parseCsvBundle(lines, targetHint)
                }
            }

            val existingKeySis = db.keyRegisterDao().getAllOnce().map { it.si }.toSet()
            val updatedKeys = bundle.keys.count { it.si in existingKeySis }
            val newKeys = bundle.keys.size - updatedKeys

            Result.success(
                ImportPreviewResult(
                    sourceLabel = path.substringAfterLast('/'),
                    bundle = bundle,
                    newKeysCount = newKeys,
                    updatedKeysCount = updatedKeys,
                    visitorsUpsertCount = bundle.visitors.size,
                    maintenanceUpsertCount = bundle.maintenance.size,
                    sportsUpsertCount = bundle.sports.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun commitBundle(
        bundle: ImportedWorkbookBundle,
        repository: OperationsRepository
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            bundle.keys.forEach { key ->
                repository.saveKeyEntry(key)
            }
            if (bundle.visitors.isNotEmpty()) db.visitorDayDao().upsertAll(bundle.visitors)
            if (bundle.maintenance.isNotEmpty()) db.maintenanceRequestDao().upsertAll(bundle.maintenance)
            if (bundle.sports.isNotEmpty()) db.sportsBookingDao().upsertAll(bundle.sports)

            val summary = buildString {
                append("Applied real-time update: ")
                val parts = mutableListOf<String>()
                if (bundle.keys.isNotEmpty()) parts.add("${bundle.keys.size} key(s)")
                if (bundle.visitors.isNotEmpty()) parts.add("${bundle.visitors.size} visitor day(s)")
                if (bundle.maintenance.isNotEmpty()) parts.add("${bundle.maintenance.size} work order(s)")
                if (bundle.sports.isNotEmpty()) parts.add("${bundle.sports.size} sports row(s)")
                append(if (parts.isEmpty()) "0 matching rows" else parts.joinToString(", "))
                if (bundle.skippedTestRecords > 0) {
                    append(" • filtered ${bundle.skippedTestRecords} test record(s)")
                }
            }

            Result.success(
                SyncResult(
                    message = summary,
                    keysCount = bundle.keys.size,
                    visitorsCount = bundle.visitors.size,
                    maintenanceCount = bundle.maintenance.size,
                    sportsCount = bundle.sports.size,
                    skippedTestCount = bundle.skippedTestRecords
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseJsonBundle(text: String): ImportedWorkbookBundle {
        val root = JSONObject(text)
        val keys = mutableListOf<KeyRegisterEntity>()
        val visitors = mutableListOf<VisitorDayEntity>()
        val maintenance = mutableListOf<MaintenanceRequestEntity>()
        val sports = mutableListOf<SportsBookingEntity>()
        var skipped = 0

        root.optJSONArray("keyRegister")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val k = KeyRegisterEntity(
                    si = o.optInt("si"),
                    dateOut = o.optString("dateOut"),
                    timeOut = o.optString("timeOut"),
                    flatUnitLocation = o.optString("flatUnitLocation"),
                    keyCardType = o.optString("keyCardType"),
                    purpose = o.optString("purpose"),
                    issuedTo = o.optString("issuedTo"),
                    qidOrNote = o.optString("qidOrNote"),
                    dateIn = o.optString("dateIn"),
                    timeIn = o.optString("timeIn"),
                    returnedBy = o.optString("returnedBy"),
                    remarks = o.optString("remarks"),
                    status = o.optString("status", "Key issued")
                )
                if (TestRecordFilter.isDeletedTestKeyRecord(k)) skipped++ else keys.add(k)
            }
        }

        root.optJSONArray("pmVisitors")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val dateIso = o.optString("dateIso")
                if (dateIso.isNotBlank()) {
                    visitors.add(
                        VisitorDayEntity(
                            dateIso = dateIso,
                            displayDate = o.optString("displayDate", dateIso),
                            monthKey = o.optString("monthKey", dateIso.take(7)),
                            visitorCount = o.optInt("visitorCount"),
                            viewingCount = o.optInt("viewingCount"),
                            flatDetails = o.optString("flatDetails", "-"),
                            notes = o.optString("notes")
                        )
                    )
                }
            }
        }

        root.optJSONArray("maintenanceLog")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                maintenance.add(
                    MaintenanceRequestEntity(
                        sn = o.optInt("sn", i + 1),
                        date = o.optString("date"),
                        names = o.optString("names"),
                        phoneNumber = o.optString("phoneNumber"),
                        workOrderNumber = o.optString("workOrderNumber"),
                        qidNumber = o.optString("qidNumber"),
                        flatDetails = o.optString("flatDetails"),
                        requestDetails = o.optString("requestDetails")
                    )
                )
            }
        }

        root.optJSONArray("padelFootball")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                sports.add(
                    SportsBookingEntity(
                        sn = o.optInt("sn", i + 1),
                        sport = o.optString("sport", "PADEL").uppercase(Locale.ROOT),
                        date = o.optString("date"),
                        time = o.optString("time"),
                        name = o.optString("name"),
                        unitDetails = o.optString("unitDetails"),
                        discount = o.optString("discount", "FREE"),
                        referenceNo = o.optString("referenceNo"),
                        durationPlayed = o.optDouble("durationPlayed", 1.0),
                        remarks = o.optString("remarks")
                    )
                )
            }
        }

        return ImportedWorkbookBundle(keys, visitors, maintenance, sports, skipped)
    }

    private fun parseCsvBundle(lines: List<String>, targetHint: IngestTarget): ImportedWorkbookBundle {
        if (lines.isEmpty()) return ImportedWorkbookBundle()
        val parsed = lines.filter { it.isNotBlank() }.map(::splitCsvLine)
        val headers = parsed.first().map { it.trim().lowercase(Locale.ROOT) }
        val data = parsed.drop(1)

        val keys = mutableListOf<KeyRegisterEntity>()
        val visitors = mutableListOf<VisitorDayEntity>()
        val maintenance = mutableListOf<MaintenanceRequestEntity>()
        val sports = mutableListOf<SportsBookingEntity>()
        var skipped = 0

        val detected = when {
            targetHint != IngestTarget.AUTO -> targetHint
            headers.any { it.contains("date out") || it.contains("key / card") || it == "si" } -> IngestTarget.KEYS
            headers.any { it.contains("sport") || it.contains("duration") } -> IngestTarget.SPORTS
            headers.any { it.contains("work order") || it.contains("maintenance") } -> IngestTarget.MAINTENANCE
            headers.any { it.contains("visitor") || it.contains("viewing") } -> IngestTarget.VISITORS
            else -> IngestTarget.AUTO
        }

        when (detected) {
            IngestTarget.KEYS -> {
                data.forEach { r ->
                    val si = r.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    val k = KeyRegisterEntity(
                        si = si,
                        dateOut = r.getOrElse(1) { "" },
                        timeOut = r.getOrElse(2) { "" },
                        flatUnitLocation = r.getOrElse(3) { "" },
                        keyCardType = r.getOrElse(4) { "" },
                        purpose = r.getOrElse(5) { "" },
                        issuedTo = r.getOrElse(6) { "" },
                        qidOrNote = r.getOrElse(7) { "" },
                        dateIn = r.getOrElse(8) { "" },
                        timeIn = r.getOrElse(9) { "" },
                        returnedBy = r.getOrElse(10) { "" },
                        remarks = r.getOrElse(11) { "" },
                        status = r.getOrElse(12) { "Key issued" }
                    )
                    if (TestRecordFilter.isDeletedTestKeyRecord(k)) skipped++ else keys.add(k)
                }
            }
            IngestTarget.SPORTS -> {
                data.forEachIndexed { idx, r ->
                    val sport = r.getOrElse(1) { "PADEL" }.uppercase(Locale.ROOT)
                    if (sport != "FOOTBALL" && sport != "PADEL") return@forEachIndexed
                    sports.add(
                        SportsBookingEntity(
                            sn = r.getOrNull(0)?.toIntOrNull() ?: (idx + 1),
                            sport = sport,
                            date = r.getOrElse(2) { "" },
                            time = r.getOrElse(3) { "" },
                            name = r.getOrElse(4) { "" },
                            unitDetails = r.getOrElse(5) { "" },
                            discount = r.getOrElse(6) { "FREE" },
                            referenceNo = r.getOrElse(7) { "" },
                            durationPlayed = r.getOrNull(8)?.toDoubleOrNull() ?: 1.0,
                            remarks = r.getOrElse(9) { "" }
                        )
                    )
                }
            }
            IngestTarget.MAINTENANCE -> {
                data.forEachIndexed { idx, r ->
                    maintenance.add(
                        MaintenanceRequestEntity(
                            sn = r.getOrNull(0)?.toIntOrNull() ?: (idx + 1),
                            date = r.getOrElse(1) { "" },
                            names = r.getOrElse(2) { "" },
                            phoneNumber = r.getOrElse(3) { "" },
                            workOrderNumber = r.getOrElse(4) { "" },
                            qidNumber = r.getOrElse(5) { "" },
                            flatDetails = r.getOrElse(6) { "" },
                            requestDetails = r.getOrElse(7) { "" }
                        )
                    )
                }
            }
            IngestTarget.VISITORS -> {
                data.forEachIndexed { idx, r ->
                    val disp = r.getOrElse(0) { "" }
                    if (disp.isBlank() || disp.startsWith("SUM", true)) return@forEachIndexed
                    val normalized = parseVisitorDate(disp, idx)
                    visitors.add(
                        VisitorDayEntity(
                            dateIso = normalized.first,
                            displayDate = normalized.second,
                            monthKey = normalized.first.take(7),
                            visitorCount = r.getOrNull(1)?.toIntOrNull() ?: 0,
                            viewingCount = r.getOrNull(2)?.toIntOrNull() ?: 0,
                            flatDetails = r.getOrElse(3) { "-" },
                            notes = r.getOrElse(4) { "" }
                        )
                    )
                }
            }
            IngestTarget.AUTO -> Unit
        }

        return ImportedWorkbookBundle(keys, visitors, maintenance, sports, skipped)
    }

    private fun parseVisitorDate(raw: String, idx: Int): Pair<String, String> {
        val formats = listOf("dd-MMM-yyyy", "dd/MM/yyyy", "yyyy-MM-dd")
        for (fmt in formats) {
            try {
                val d = SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }.parse(raw.trim()) ?: continue
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d) to SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(d)
            } catch (_: Exception) {
            }
        }
        val iso = String.format("2026-11-%02d", (idx + 1).coerceAtMost(30))
        return iso to raw.trim()
    }

    private fun csvRow(vararg values: Any?): String =
        values.joinToString(",") { v ->
            val s = v?.toString().orEmpty()
            if (s.contains(',') || s.contains('"') || s.contains('\n')) {
                "\"${s.replace("\"", "\"\"")}\""
            } else {
                s
            }
        }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
