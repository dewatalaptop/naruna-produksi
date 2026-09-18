package com.aiappbuilder.narunaproduksi.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiappbuilder.narunaproduksi.data.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Drains [PendingScan] rows into Firestore. Each row is one atomic unit of
 * work: create the scan_events doc, and — only for a "keluar" row — also
 * advance the work_group's tahap_sekarang (and flip status to "selesai"
 * once every stage is done). Rows are processed one at a time and only
 * marked synced after both writes succeed, so a mid-sync failure just
 * leaves the row for the next run rather than losing or duplicating data.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).pendingScanDao()
        val db = FirebaseFirestore.getInstance()
        val unsynced = dao.getUnsynced()
        if (unsynced.isEmpty()) return Result.success()

        var anyFailure = false
        for (scan in unsynced) {
            try {
                val scanEvent = hashMapOf(
                    "work_group_id" to scan.workGroupId,
                    "stasiun_id" to scan.stasiunId,
                    "tahap" to scan.tahap,
                    "tipe" to scan.tipe,
                    "jumlah" to scan.jumlah,
                    "susut" to scan.susut,
                    "user_id" to scan.userId,
                    "timestamp" to Timestamp(Date(scan.timestampMillis)),
                    "synced_offline" to scan.wasOffline
                )
                db.collection("scan_events").add(scanEvent).await()

                if (scan.tipe == "keluar" && scan.newTahapSekarang != null) {
                    val updates = mutableMapOf<String, Any>("tahap_sekarang" to scan.newTahapSekarang)
                    if (scan.markSelesai) updates["status"] = "selesai"
                    db.collection("work_groups").document(scan.workGroupId).update(updates).await()
                }

                dao.update(scan.copy(synced = true))
            } catch (e: Exception) {
                anyFailure = true
            }
        }
        return if (anyFailure) Result.retry() else Result.success()
    }
}
