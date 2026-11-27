package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Database(
    entities = [
        KeyRegisterEntity::class,
        VisitorDayEntity::class,
        MaintenanceRequestEntity::class,
        SportsBookingEntity::class,
        OpsReminderEntity::class,
        InspectionReportEntity::class,
        ReportDispatchEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MasakenDatabase : RoomDatabase() {
    abstract fun keyRegisterDao(): KeyRegisterDao
    abstract fun visitorDayDao(): VisitorDayDao
    abstract fun maintenanceRequestDao(): MaintenanceRequestDao
    abstract fun sportsBookingDao(): SportsBookingDao
    abstract fun opsReminderDao(): OpsReminderDao
    abstract fun inspectionReportDao(): InspectionReportDao
    abstract fun reportDispatchDao(): ReportDispatchDao

    companion object {
        @Volatile
        private var INSTANCE: MasakenDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE key_register ADD COLUMN issuedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inspection_reports (
                        id TEXT NOT NULL PRIMARY KEY,
                        buildingName TEXT NOT NULL,
                        inspectionDate TEXT NOT NULL,
                        inspectorName TEXT NOT NULL,
                        itemCount INTEGER NOT NULL,
                        summaryNote TEXT NOT NULL,
                        localFilePath TEXT NOT NULL,
                        driveFileId TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS report_dispatches (
                        id TEXT NOT NULL PRIMARY KEY,
                        reportType TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        periodLabel TEXT NOT NULL,
                        recipientsTo TEXT NOT NULL,
                        recipientsCc TEXT NOT NULL,
                        attachmentNames TEXT NOT NULL,
                        localPaths TEXT NOT NULL,
                        driveStatus TEXT NOT NULL,
                        dispatchChannel TEXT NOT NULL,
                        sentAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): MasakenDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MasakenDatabase::class.java,
                    "masaken_mesaimeer_ops.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                seedIfEmpty(getInstance(context))
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun seedIfEmpty(database: MasakenDatabase) = withContext(Dispatchers.IO) {
            val seedKeys = SeedDataProvider.buildKeyRegisterSeed()
            val seedSports = SeedDataProvider.buildSportsBookingsSeed()

            if (database.keyRegisterDao().getAllOnce().isEmpty()) {
                database.keyRegisterDao().upsertAll(seedKeys)
                database.visitorDayDao().upsertAll(SeedDataProvider.buildVisitorDaysSeed())
                database.maintenanceRequestDao().upsertAll(SeedDataProvider.buildMaintenanceSeed())
                database.sportsBookingDao().upsertAll(seedSports)
                database.opsReminderDao().upsertAll(SeedDataProvider.buildInitialRemindersSeed(seedKeys))
            } else {
                database.sportsBookingDao().deletePlaceholderSportsRows()
                val existingSports = database.sportsBookingDao().getAllAscendingOnce()
                val existingSportSns = existingSports.map { it.sn }.toSet()
                val missingSports = seedSports.filter { it.sn !in existingSportSns }
                if (existingSports.isEmpty()) {
                    database.sportsBookingDao().upsertAll(seedSports)
                } else if (missingSports.isNotEmpty()) {
                    database.sportsBookingDao().upsertAll(missingSports)
                }

                val deletedVisitors = database.visitorDayDao().deleteSyntheticVisitorRows()
                if (deletedVisitors > 0 || database.visitorDayDao().getAllOnce().size < 300) {
                    database.visitorDayDao().insertIgnoreAll(SeedDataProvider.buildVisitorDaysSeed())
                }

                val deletedMaint = database.maintenanceRequestDao().deleteSyntheticMaintenanceRows()
                if (deletedMaint > 0 || database.maintenanceRequestDao().getAllOnce().isEmpty()) {
                    database.maintenanceRequestDao().insertIgnoreAll(SeedDataProvider.buildMaintenanceSeed())
                }

                database.keyRegisterDao().deletePlaceholderKeyRows()
                val existingKeys = database.keyRegisterDao().getAllOnce()
                val existingBySi = existingKeys.associateBy { it.si }
                val keyUpdates = mutableListOf<KeyRegisterEntity>()
                seedKeys.forEach { seedKey ->
                    val current = existingBySi[seedKey.si]
                    when {
                        current == null -> keyUpdates.add(seedKey)
                        seedKey.si in 1634..1645 || seedKey.si == 1283 -> {
                            val isManuallySettled = current.status.equals(KeyStatus.RETURNED, ignoreCase = true) ||
                                current.status.equals(KeyStatus.ISSUED_TO_TENANT, ignoreCase = true)
                            if (!isManuallySettled) {
                                val needsSync = current.status != seedKey.status ||
                                    current.issuedBy.isBlank() ||
                                    current.flatUnitLocation != seedKey.flatUnitLocation ||
                                    current.purpose != seedKey.purpose ||
                                    (seedKey.isMoveInPendingTCode && (current.remarks != seedKey.remarks || current.qidOrNote != seedKey.qidOrNote))
                                if (needsSync) {
                                    keyUpdates.add(
                                        current.copy(
                                            dateOut = seedKey.dateOut,
                                            timeOut = seedKey.timeOut,
                                            flatUnitLocation = seedKey.flatUnitLocation,
                                            keyCardType = seedKey.keyCardType,
                                            purpose = seedKey.purpose,
                                            issuedBy = current.issuedBy.ifBlank { seedKey.issuedBy },
                                            issuedTo = if (seedKey.isMoveInPendingTCode) seedKey.issuedTo else current.issuedTo.ifBlank { seedKey.issuedTo },
                                            qidOrNote = if (seedKey.isMoveInPendingTCode) seedKey.qidOrNote else current.qidOrNote,
                                            remarks = seedKey.remarks,
                                            status = seedKey.status
                                        )
                                    )
                                }
                            } else if (current.issuedBy.isBlank() && seedKey.issuedBy.isNotBlank()) {
                                keyUpdates.add(current.copy(issuedBy = seedKey.issuedBy))
                            }
                        }
                        current.issuedBy.isBlank() && seedKey.issuedBy.isNotBlank() -> {
                            keyUpdates.add(current.copy(issuedBy = seedKey.issuedBy))
                        }
                    }
                }
                if (keyUpdates.isNotEmpty()) {
                    database.keyRegisterDao().upsertAll(keyUpdates)
                }

                val updatedKeys = database.keyRegisterDao().getAllOnce()
                val existingReminders = database.opsReminderDao().getAllOnce().associateBy { it.id }
                val targetReminders = SeedDataProvider.buildInitialRemindersSeed(updatedKeys)
                val remindersToUpsert = mutableListOf<OpsReminderEntity>()
                targetReminders.forEach { target ->
                    val existing = existingReminders[target.id]
                    if (existing == null) {
                        remindersToUpsert.add(target)
                    } else if (
                        target.id in setOf("daily_1530", "sat_audit_2026_11_28", "weekly_report_fri", "monthly_report_eom") &&
                        (existing.message != target.message || existing.title != target.title)
                    ) {
                        remindersToUpsert.add(
                            existing.copy(
                                title = target.title,
                                message = target.message,
                                scheduledAtMillis = target.scheduledAtMillis
                            )
                        )
                    }
                }
                if (remindersToUpsert.isNotEmpty()) {
                    database.opsReminderDao().upsertAll(remindersToUpsert)
                }
                // Complete stale KEY_NEXT_DAY reminders on Move-In keys that are no longer red alerts
                val nonAlertSis = updatedKeys.filter { !it.isUnreturnedAlert }.map { it.si }.toSet()
                val obsoleteKeyReminders = existingReminders.values.filter { rem ->
                    rem.type == ReminderType.KEY_NEXT_DAY && !rem.isDone && rem.relatedKeySi in nonAlertSis
                }.map { it.copy(isDone = true, snoozedUntilMillis = null) }
                if (obsoleteKeyReminders.isNotEmpty()) {
                    database.opsReminderDao().upsertAll(obsoleteKeyReminders)
                }
            }
        }
    }
}
