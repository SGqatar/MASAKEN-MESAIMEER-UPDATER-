package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        OpsReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MasakenDatabase : RoomDatabase() {
    abstract fun keyRegisterDao(): KeyRegisterDao
    abstract fun visitorDayDao(): VisitorDayDao
    abstract fun maintenanceRequestDao(): MaintenanceRequestDao
    abstract fun sportsBookingDao(): SportsBookingDao
    abstract fun opsReminderDao(): OpsReminderDao

    companion object {
        @Volatile
        private var INSTANCE: MasakenDatabase? = null

        fun getInstance(context: Context): MasakenDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MasakenDatabase::class.java,
                    "masaken_mesaimeer_ops.db"
                )
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
            if (database.keyRegisterDao().getAllOnce().isEmpty()) {
                val keys = SeedDataProvider.buildKeyRegisterSeed()
                database.keyRegisterDao().upsertAll(keys)
                database.visitorDayDao().upsertAll(SeedDataProvider.buildVisitorDaysSeed())
                database.maintenanceRequestDao().upsertAll(SeedDataProvider.buildMaintenanceSeed())
                database.sportsBookingDao().upsertAll(SeedDataProvider.buildSportsBookingsSeed())
                database.opsReminderDao().upsertAll(SeedDataProvider.buildInitialRemindersSeed(keys))
            } else {
                val deletedSports = database.sportsBookingDao().deletePlaceholderSportsRows()
                if (deletedSports > 0 || database.sportsBookingDao().getAllAscendingOnce().isEmpty()) {
                    database.sportsBookingDao().upsertAll(SeedDataProvider.buildSportsBookingsSeed())
                }

                val deletedVisitors = database.visitorDayDao().deleteSyntheticVisitorRows()
                if (deletedVisitors > 0 || database.visitorDayDao().getAllOnce().size < 300) {
                    database.visitorDayDao().insertIgnoreAll(SeedDataProvider.buildVisitorDaysSeed())
                }

                val deletedMaint = database.maintenanceRequestDao().deleteSyntheticMaintenanceRows()
                if (deletedMaint > 0 || database.maintenanceRequestDao().getAllOnce().isEmpty()) {
                    database.maintenanceRequestDao().insertIgnoreAll(SeedDataProvider.buildMaintenanceSeed())
                }

                val deletedKeys = database.keyRegisterDao().deletePlaceholderKeyRows()
                if (deletedKeys > 0 || database.keyRegisterDao().getAllOnce().none { it.si == 2 }) {
                    val keys = SeedDataProvider.buildKeyRegisterSeed()
                    database.keyRegisterDao().insertIgnoreAll(keys)
                    val existingReminderIds = database.opsReminderDao().getAllOnce().map { it.id }.toSet()
                    val missingReminders = SeedDataProvider.buildInitialRemindersSeed(keys)
                        .filter { it.id !in existingReminderIds }
                    if (missingReminders.isNotEmpty()) {
                        database.opsReminderDao().upsertAll(missingReminders)
                    }
                }
            }
        }
    }
}
