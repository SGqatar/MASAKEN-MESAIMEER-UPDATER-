package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyRegisterDao {
    @Query("SELECT * FROM key_register ORDER BY si DESC")
    fun observeAll(): Flow<List<KeyRegisterEntity>>

    @Query("SELECT * FROM key_register ORDER BY si DESC")
    suspend fun getAllOnce(): List<KeyRegisterEntity>

    @Query("SELECT COALESCE(MAX(si), 1645) FROM key_register")
    suspend fun getMaxSi(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: KeyRegisterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<KeyRegisterEntity>)

    @Query("DELETE FROM key_register WHERE si = :si")
    suspend fun deleteBySi(si: Int)

    @Query("DELETE FROM key_register")
    suspend fun deleteAll()
}

@Dao
interface VisitorDayDao {
    @Query("SELECT * FROM visitor_days ORDER BY dateIso DESC")
    fun observeAll(): Flow<List<VisitorDayEntity>>

    @Query("SELECT * FROM visitor_days ORDER BY dateIso DESC")
    suspend fun getAllOnce(): List<VisitorDayEntity>

    @Query("SELECT * FROM visitor_days WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getByDateIso(dateIso: String): VisitorDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: VisitorDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<VisitorDayEntity>)

    @Query("DELETE FROM visitor_days WHERE dateIso = :dateIso")
    suspend fun deleteByDateIso(dateIso: String)

    @Query("DELETE FROM visitor_days")
    suspend fun deleteAll()
}

@Dao
interface MaintenanceRequestDao {
    @Query("SELECT * FROM maintenance_requests ORDER BY sn DESC")
    fun observeAll(): Flow<List<MaintenanceRequestEntity>>

    @Query("SELECT * FROM maintenance_requests ORDER BY sn DESC")
    suspend fun getAllOnce(): List<MaintenanceRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MaintenanceRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MaintenanceRequestEntity>)

    @Query("DELETE FROM maintenance_requests WHERE sn = :sn")
    suspend fun deleteBySn(sn: Int)

    @Query("DELETE FROM maintenance_requests")
    suspend fun deleteAll()
}

@Dao
interface SportsBookingDao {
    @Query("SELECT * FROM sports_bookings ORDER BY sn DESC")
    fun observeAll(): Flow<List<SportsBookingEntity>>

    @Query("SELECT * FROM sports_bookings ORDER BY sn ASC")
    suspend fun getAllAscendingOnce(): List<SportsBookingEntity>

    @Query("SELECT COALESCE(MAX(sn), 0) FROM sports_bookings")
    suspend fun getMaxSn(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(booking: SportsBookingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bookings: List<SportsBookingEntity>)

    @Query("DELETE FROM sports_bookings WHERE sn = :sn")
    suspend fun deleteBySn(sn: Int)

    @Query("DELETE FROM sports_bookings")
    suspend fun deleteAll()
}

@Dao
interface OpsReminderDao {
    @Query("SELECT * FROM ops_reminders ORDER BY isDone ASC, scheduledAtMillis ASC")
    fun observeAll(): Flow<List<OpsReminderEntity>>

    @Query("SELECT * FROM ops_reminders")
    suspend fun getAllOnce(): List<OpsReminderEntity>

    @Query("SELECT * FROM ops_reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): OpsReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: OpsReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reminders: List<OpsReminderEntity>)

    @Update
    suspend fun update(reminder: OpsReminderEntity)

    @Query("DELETE FROM ops_reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}
