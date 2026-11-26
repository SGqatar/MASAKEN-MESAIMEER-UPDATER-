package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        suspend fun seedIfEmpty(database: MasakenDatabase) {
            if (database.keyRegisterDao().getAllOnce().isEmpty()) {
                val keys = SeedDataProvider.buildKeyRegisterSeed()
                database.keyRegisterDao().upsertAll(keys)
                database.visitorDayDao().upsertAll(SeedDataProvider.buildVisitorDaysSeed())
                database.maintenanceRequestDao().upsertAll(SeedDataProvider.buildMaintenanceSeed())
                database.sportsBookingDao().upsertAll(SeedDataProvider.buildSportsBookingsSeed())
                database.opsReminderDao().upsertAll(SeedDataProvider.buildInitialRemindersSeed(keys))
            }
        }
    }
}
