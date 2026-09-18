package com.aiappbuilder.narunaproduksi.data

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * Firestore fields are read manually (not via toObject<T>() POJO mapping) —
 * more verbose, but avoids reflection-based mapping mismatches with the web
 * app's exact field names (see src/data.js / master-data.html in the
 * naruna-produksi web app for the schema these mirror).
 */
data class WorkGroup(
    val id: String,
    val poId: String,
    val itemId: String,
    val materialId: String,
    val colorId: String,
    val jumlah: Long,
    val tahapSekarang: Long,
    val urutanPrioritas: Long,
    val tenggatProyeksi: Date?,
    val status: String
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): WorkGroup? {
            val poId = doc.getString("po_id") ?: return null
            val itemId = doc.getString("item_id") ?: return null
            return WorkGroup(
                id = doc.id,
                poId = poId,
                itemId = itemId,
                materialId = doc.getString("material_id") ?: "",
                colorId = doc.getString("color_id") ?: "",
                jumlah = doc.getLong("jumlah") ?: 0,
                tahapSekarang = doc.getLong("tahap_sekarang") ?: 0,
                urutanPrioritas = doc.getLong("urutan_prioritas") ?: 9999,
                tenggatProyeksi = doc.getTimestamp("tenggat_proyeksi")?.toDate(),
                status = doc.getString("status") ?: "aktif"
            )
        }
    }
}

data class Item(
    val id: String,
    val nama: String,
    val tahapKerja: List<String>
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): Item {
            @Suppress("UNCHECKED_CAST")
            val stages = (doc.get("tahap_kerja") as? List<String>) ?: emptyList()
            return Item(id = doc.id, nama = doc.getString("nama") ?: "?", tahapKerja = stages)
        }
    }
}

data class StasiunKerja(
    val id: String,
    val nama: String,
    val urutanTahap: Long
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): StasiunKerja =
            StasiunKerja(
                id = doc.id,
                nama = doc.getString("nama") ?: "?",
                urutanTahap = doc.getLong("urutan_tahap") ?: 0
            )
    }
}

data class MaterialInfo(val id: String, val nama: String)
data class ColorInfo(val id: String, val nama: String)

/**
 * The next station a work group is due at, mirroring
 * functions/src/naruna.ts's stagesRemainingAfter logic exactly: the first
 * stage (by urutan_tahap) greater than tahap_sekarang that this item's own
 * tahap_kerja list includes.
 */
fun nextStasiunFor(workGroup: WorkGroup, item: Item, stasiunOrdered: List<StasiunKerja>): StasiunKerja? {
    val itemStages = item.tahapKerja.toSet()
    return stasiunOrdered.firstOrNull { it.urutanTahap > workGroup.tahapSekarang && itemStages.contains(it.id) }
}
