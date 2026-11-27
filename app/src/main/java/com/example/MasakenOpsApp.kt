package com.example

import android.app.Application
import com.example.data.OperationsRepository
import com.example.data.ai.BookingParserService
import com.example.data.ai.UniversalPhotoIngestService
import com.example.data.cloud.FirestoreSyncRepository
import com.example.data.db.MasakenDatabase
import com.example.data.sync.WorkbookSyncManager
import com.example.notifications.OpsNotificationHelper
import com.example.notifications.OpsReminderScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MasakenOpsApp : Application() {
    val database: MasakenDatabase by lazy { MasakenDatabase.getInstance(this) }
    val reminderScheduler: OpsReminderScheduler by lazy { OpsReminderScheduler(this) }

    val firebaseAuth: FirebaseAuth by lazy { Firebase.auth }
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }
    val firestoreSyncRepository: FirestoreSyncRepository by lazy {
        FirestoreSyncRepository(firestore, firebaseAuth, database)
    }

    val repository: OperationsRepository by lazy {
        OperationsRepository(database, reminderScheduler, firestoreSyncRepository)
    }
    val workbookSyncManager: WorkbookSyncManager by lazy { WorkbookSyncManager(this, database) }
    val bookingParserService: BookingParserService by lazy { BookingParserService(this) }
    val universalPhotoIngestService: UniversalPhotoIngestService by lazy { UniversalPhotoIngestService(this) }

    override fun onCreate() {
        super.onCreate()
        OpsNotificationHelper.ensureChannels(this)
    }
}
