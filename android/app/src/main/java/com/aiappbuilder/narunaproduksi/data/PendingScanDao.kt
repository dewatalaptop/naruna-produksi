package com.aiappbuilder.narunaproduksi.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingScanDao {
    @Insert
    suspend fun insert(scan: PendingScan): Long

    @Query("SELECT * FROM pending_scans WHERE synced = 0 ORDER BY id ASC")
    suspend fun getUnsynced(): List<PendingScan>

    @Update
    suspend fun update(scan: PendingScan)

    @Query("SELECT COUNT(*) FROM pending_scans WHERE synced = 0")
    fun watchUnsyncedCount(): Flow<Int>

    /** Has this station already logged a "masuk" for this ticket with no matching "keluar" yet? */
    @Query(
        "SELECT COUNT(*) FROM pending_scans WHERE workGroupId = :workGroupId AND stasiunId = :stasiunId AND tipe = 'masuk'"
    )
    suspend fun countMasuk(workGroupId: String, stasiunId: String): Int

    @Query(
        "SELECT COUNT(*) FROM pending_scans WHERE workGroupId = :workGroupId AND stasiunId = :stasiunId AND tipe = 'keluar'"
    )
    suspend fun countKeluar(workGroupId: String, stasiunId: String): Int
}
