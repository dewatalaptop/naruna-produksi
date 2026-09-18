package com.aiappbuilder.narunaproduksi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aiappbuilder.narunaproduksi.R
import com.aiappbuilder.narunaproduksi.data.AppDatabase
import com.aiappbuilder.narunaproduksi.data.Item
import com.aiappbuilder.narunaproduksi.data.PendingScan
import com.aiappbuilder.narunaproduksi.data.StasiunKerja
import com.aiappbuilder.narunaproduksi.data.StationSession
import com.aiappbuilder.narunaproduksi.data.WorkGroup
import com.aiappbuilder.narunaproduksi.data.isOnline
import com.aiappbuilder.narunaproduksi.data.nextStasiunFor
import com.aiappbuilder.narunaproduksi.databinding.ActivityScanBinding
import com.aiappbuilder.narunaproduksi.sync.SyncScheduler
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.camera.core.ExperimentalGetImage
class ScanActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_WORK_GROUP_ID = "work_group_id"
    }

    private lateinit var binding: ActivityScanBinding
    private val db = FirebaseFirestore.getInstance()
    private val barcodeScanner = BarcodeScanning.getClient()
    private var cameraExecutor: ExecutorService? = null
    private val decodeLocked = AtomicBoolean(false)

    private var myStasiunId: String? = null
    private var stasiunOrdered: List<StasiunKerja> = emptyList()

    // State for the ticket currently shown on the confirm card.
    private var currentWorkGroup: WorkGroup? = null
    private var currentItem: Item? = null
    private var currentTipe: String? = null // "masuk" | "keluar"

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.manualSearchButton.setOnClickListener {
            val id = binding.manualIdInput.text.toString().trim()
            if (id.isNotEmpty()) {
                decodeLocked.set(true)
                lifecycleScope.launch { resolveWorkGroup(id) }
            }
        }

        binding.confirmButton.setOnClickListener { onConfirmClicked() }

        // Tapping the camera preview while a (possibly wrong) ticket is on
        // the confirm card cancels it and lets the operator rescan —
        // otherwise there'd be no way back once a wrong QR is decoded.
        binding.cameraPreview.setOnClickListener {
            if (binding.confirmCard.visibility == android.view.View.VISIBLE) resetForNextScan()
        }

        lifecycleScope.launch {
            myStasiunId = StationSession.currentStasiunId()
            val stasiunSnap = db.collection("stasiun_kerja").get().await()
            stasiunOrdered = stasiunSnap.documents.map { StasiunKerja.fromSnapshot(it) }.sortedBy { it.urutanTahap }

            val presetId = intent.getStringExtra(EXTRA_WORK_GROUP_ID)
            if (presetId != null) {
                resolveWorkGroup(presetId)
            } else {
                ensureCameraPermission()
            }
        }
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            val executor = Executors.newSingleThreadExecutor().also { cameraExecutor = it }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, ::analyzeFrame) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // Camera bind can fail on devices without the expected camera
                // hardware/lifecycle state — the manual-input bar remains a
                // full fallback either way, so this is non-fatal.
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeFrame(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || decodeLocked.get()) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull()?.rawValue
                if (value != null && decodeLocked.compareAndSet(false, true)) {
                    runOnUiThread { lifecycleScope.launch { resolveWorkGroup(value) } }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private suspend fun resolveWorkGroup(workGroupId: String) {
        val wg = try {
            db.collection("work_groups").document(workGroupId).get().await()
                .let { WorkGroup.fromSnapshot(it) }
        } catch (e: Exception) {
            null
        }
        if (wg == null) {
            showTransientWarning(getString(R.string.scan_not_found))
            resetForNextScan()
            return
        }

        val item = try {
            Item.fromSnapshot(db.collection("items").document(wg.itemId).get().await())
        } catch (e: Exception) {
            null
        }
        if (item == null) {
            showTransientWarning(getString(R.string.scan_not_found))
            resetForNextScan()
            return
        }

        currentWorkGroup = wg
        currentItem = item
        showConfirmCard(wg, item)
    }

    private suspend fun showConfirmCard(wg: WorkGroup, item: Item) {
        val stasiunId = myStasiunId
        val nextStasiun = nextStasiunFor(wg, item, stasiunOrdered)
        binding.confirmWarning.visibility = android.view.View.GONE

        if (nextStasiun == null) {
            showTransientWarning(getString(R.string.scan_already_done))
            resetForNextScan()
            return
        }
        if (nextStasiun.id != stasiunId) {
            binding.confirmWarning.visibility = android.view.View.VISIBLE
            binding.confirmWarning.text = getString(R.string.scan_wrong_station)
        }

        val dao = AppDatabase.get(this).pendingScanDao()
        val hasMasuk = stasiunId != null && dao.countMasuk(wg.id, stasiunId) > dao.countKeluar(wg.id, stasiunId)
        currentTipe = if (hasMasuk) "keluar" else "masuk"

        binding.confirmTicketInfo.text = "Tiket: ${item.nama}"
        binding.confirmAction.text = if (currentTipe == "keluar") {
            getString(R.string.scan_confirm_keluar)
        } else {
            getString(R.string.scan_confirm_masuk)
        }
        binding.keluarFieldsRow.visibility = if (currentTipe == "keluar") android.view.View.VISIBLE else android.view.View.GONE
        binding.confirmCard.visibility = android.view.View.VISIBLE
    }

    private fun onConfirmClicked() {
        val wg = currentWorkGroup ?: return
        val item = currentItem ?: return
        val stasiunId = myStasiunId ?: return
        val myStasiun = stasiunOrdered.firstOrNull { it.id == stasiunId } ?: return
        val tipe = currentTipe ?: return
        val userId = StationSession.currentUid() ?: return

        var jumlah: Int? = null
        var susut: Int? = null
        if (tipe == "keluar") {
            jumlah = binding.jumlahInput.text.toString().toIntOrNull()
            if (jumlah == null) {
                android.widget.Toast.makeText(this, R.string.scan_jumlah_required, android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            susut = binding.susutInput.text.toString().toIntOrNull() ?: 0
        }

        val stagesAfterMine = stasiunOrdered.filter { it.urutanTahap > myStasiun.urutanTahap && item.tahapKerja.contains(it.id) }
        val markSelesai = tipe == "keluar" && stagesAfterMine.isEmpty()

        lifecycleScope.launch {
            AppDatabase.get(this@ScanActivity).pendingScanDao().insert(
                PendingScan(
                    workGroupId = wg.id,
                    stasiunId = stasiunId,
                    tahap = myStasiun.urutanTahap.toInt(),
                    tipe = tipe,
                    jumlah = jumlah,
                    susut = susut,
                    newTahapSekarang = if (tipe == "keluar") myStasiun.urutanTahap.toInt() else null,
                    markSelesai = markSelesai,
                    userId = userId,
                    timestampMillis = System.currentTimeMillis(),
                    wasOffline = !isOnline(this@ScanActivity)
                )
            )
            SyncScheduler.syncNow(this@ScanActivity)
            android.widget.Toast.makeText(this@ScanActivity, R.string.scan_saved, android.widget.Toast.LENGTH_SHORT).show()
            resetForNextScan()
        }
    }

    private fun resetForNextScan() {
        currentWorkGroup = null
        currentItem = null
        currentTipe = null
        binding.confirmCard.visibility = android.view.View.GONE
        binding.manualIdInput.setText("")
        decodeLocked.set(false)
        if (intent.getStringExtra(EXTRA_WORK_GROUP_ID) != null) finish()
    }

    private fun showTransientWarning(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
    }
}
