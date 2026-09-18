package com.aiappbuilder.narunaproduksi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The offline scan queue spec section 6 requires explicitly (Room/SQLite,
 * not just relying on Firestore's own offline cache) — every scan (masuk or
 * keluar) is written here FIRST, instantly, online or not. [SyncWorker]
 * drains unsynced rows once connectivity is available. [newTahapSekarang]
 * is only set on a "keluar" row: syncing it also advances the work_group's
 * stage, matching how the web app's hitungUlangPrioritas interprets
 * tahap_sekarang (the urutan_tahap of the last completed stage, see
 * functions/src/naruna.ts).
 */
@Entity(tableName = "pending_scans")
data class PendingScan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workGroupId: String,
    val stasiunId: String,
    val tahap: Int,
    val tipe: String, // "masuk" | "keluar"
    val jumlah: Int?,
    val susut: Int?,
    val newTahapSekarang: Int?,
    val markSelesai: Boolean,
    val userId: String,
    val timestampMillis: Long,
    val wasOffline: Boolean,
    val synced: Boolean = false
)
