package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object KeyStatus {
    const val RETURNED = "Key returned"
    const val ISSUED = "Key issued"
    const val NOT_YET_RETURNED = "Key not yet returned"

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
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val isUnreturnedAlert: Boolean
        get() = KeyStatus.isUnreturnedOrActive(status)

    val needsNovemberReconciliation: Boolean
        get() = status.equals(KeyStatus.RETURNED, ignoreCase = true) &&
            (dateOut.contains("/11/2026") || dateOut.contains("-11-2026") || dateOut.contains("Nov", ignoreCase = true)) &&
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
    const val SAT_AUDIT_0900 = "SAT_AUDIT_0900"
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
