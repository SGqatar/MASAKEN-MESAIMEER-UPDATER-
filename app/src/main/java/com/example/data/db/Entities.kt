package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object KeyStatus {
    const val RETURNED = "Key returned"
    const val ISSUED = "Key issued"
    const val ISSUED_TO_TENANT = "Key issued to tenant"
    const val MOVE_IN_PENDING_TCODE = "Move-In Keys Given to Client"
    const val NOT_YET_RETURNED = "Key not yet returned"

    fun isMoveInPurpose(purpose: String): Boolean {
        val lower = purpose.trim().lowercase()
        return lower.contains("move in") || lower.contains("move-in")
    }

    fun isUnreturnedOrActive(status: String): Boolean {
        val normalized = status.trim().lowercase()
        return normalized == ISSUED.lowercase() || normalized == NOT_YET_RETURNED.lowercase()
    }
}

@Entity(tableName = "key_register")
data class KeyRegisterEntity(
    @PrimaryKey val si: Int,
    val dateOut: String,
    val timeOut: String,
    val flatUnitLocation: String,
    val keyCardType: String,
    val purpose: String,
    val issuedTo: String,
    val qidOrNote: String,
    val dateIn: String,
    val timeIn: String,
    val returnedBy: String,
    val remarks: String,
    val status: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val issuedBy: String = ""
) {
    val isMoveInPendingTCode: Boolean
        get() = status.equals(KeyStatus.MOVE_IN_PENDING_TCODE, ignoreCase = true) ||
            (KeyStatus.isMoveInPurpose(purpose) &&
                !status.equals(KeyStatus.RETURNED, ignoreCase = true) &&
                !status.equals(KeyStatus.ISSUED_TO_TENANT, ignoreCase = true))

    val isUnreturnedAlert: Boolean
        get() = !isMoveInPendingTCode && KeyStatus.isUnreturnedOrActive(status)

    val isNovember2026: Boolean
        get() {
            val clean = dateOut.trim()
            return clean.contains("/11/2026") ||
                clean.contains("-11-2026") ||
                clean.contains("Nov", ignoreCase = true) ||
                (clean.startsWith("11/") && clean.endsWith("/2026")) ||
                clean.startsWith("2026-11")
        }

    val isCurrentMonthUnreturnedAlert: Boolean
        get() = isUnreturnedAlert && isNovember2026

    val needsNovemberReconciliation: Boolean
        get() = status.equals(KeyStatus.RETURNED, ignoreCase = true) &&
            isNovember2026 &&
            (dateIn.isBlank() || timeIn.isBlank())
}

@Entity(tableName = "visitor_days")
data class VisitorDayEntity(
    @PrimaryKey val dateIso: String, // yyyy-MM-dd
    val displayDate: String,         // dd-MMM-yyyy e.g. 26-Nov-2026
    val monthKey: String,            // yyyy-MM e.g. 2026-11
    val visitorCount: Int,
    val viewingCount: Int,
    val flatDetails: String,
    val notes: String = ""
)

@Entity(tableName = "maintenance_requests")
data class MaintenanceRequestEntity(
    @PrimaryKey(autoGenerate = true) val sn: Int = 0,
    val date: String,
    val names: String,
    val phoneNumber: String,
    val workOrderNumber: String,
    val qidNumber: String,
    val flatDetails: String,
    val requestDetails: String
)

@Entity(tableName = "sports_bookings")
data class SportsBookingEntity(
    @PrimaryKey(autoGenerate = true) val sn: Int = 0,
    val sport: String, // "FOOTBALL" | "PADEL"
    val date: String,  // dd/MM/yyyy e.g. 25/11/2026
    val time: String,
    val name: String,
    val unitDetails: String,
    val discount: String,
    val referenceNo: String,
    val durationPlayed: Double,
    val remarks: String,
    val attachmentPath: String? = null
)

object ReminderType {
    const val DAILY_0740 = "DAILY_0740"
    const val DAILY_1530 = "DAILY_1530"
    const val SAT_AUDIT_0900 = "SAT_AUDIT_0900"
    const val WEEKLY_REPORT_FRI = "WEEKLY_REPORT_FRI"
    const val MONTHLY_REPORT_EOM = "MONTHLY_REPORT_EOM"
    const val KEY_NEXT_DAY = "KEY_NEXT_DAY"
}

@Entity(tableName = "ops_reminders")
data class OpsReminderEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val message: String,
    val relatedKeySi: Int? = null,
    val scheduledAtMillis: Long,
    val snoozedUntilMillis: Long? = null,
    val isDone: Boolean = false,
    val checklistMask: Int = 0
)

@Entity(tableName = "inspection_reports")
data class InspectionReportEntity(
    @PrimaryKey val id: String,
    val buildingName: String,
    val inspectionDate: String,
    val inspectorName: String,
    val itemCount: Int,
    val summaryNote: String,
    val localFilePath: String,
    val driveFileId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "report_dispatches")
data class ReportDispatchEntity(
    @PrimaryKey val id: String,
    val reportType: String,
    val subject: String,
    val periodLabel: String,
    val recipientsTo: String,
    val recipientsCc: String,
    val attachmentNames: String,
    val localPaths: String,
    val driveStatus: String,
    val dispatchChannel: String,
    val sentAtMillis: Long = System.currentTimeMillis()
)
