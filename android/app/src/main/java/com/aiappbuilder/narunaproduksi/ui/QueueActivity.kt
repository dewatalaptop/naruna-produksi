package com.aiappbuilder.narunaproduksi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiappbuilder.narunaproduksi.R
import com.aiappbuilder.narunaproduksi.data.AppDatabase
import com.aiappbuilder.narunaproduksi.data.ColorInfo
import com.aiappbuilder.narunaproduksi.data.Item
import com.aiappbuilder.narunaproduksi.data.MaterialInfo
import com.aiappbuilder.narunaproduksi.data.StasiunKerja
import com.aiappbuilder.narunaproduksi.data.StationSession
import com.aiappbuilder.narunaproduksi.data.WorkGroup
import com.aiappbuilder.narunaproduksi.data.nextStasiunFor
import com.aiappbuilder.narunaproduksi.data.observeConnectivity
import com.aiappbuilder.narunaproduksi.databinding.ActivityQueueBinding
import com.aiappbuilder.narunaproduksi.sync.SyncScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class QueueActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQueueBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: WorkGroupAdapter

    private var myStasiunId: String? = null
    private var stasiunOrdered: List<StasiunKerja> = emptyList()
    private var itemsMap: Map<String, Item> = emptyMap()
    private var materialsMap: Map<String, MaterialInfo> = emptyMap()
    private var colorsMap: Map<String, ColorInfo> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = WorkGroupAdapter { wg ->
            startActivity(Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_WORK_GROUP_ID, wg.id))
        }
        binding.queueList.layoutManager = LinearLayoutManager(this)
        binding.queueList.adapter = adapter

        binding.scanFab.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        requestNotificationPermissionIfNeeded()
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.syncNow(this)

        observeConnectivity(this).onEach { online ->
            binding.connectivityBar.connectivityDot.background.setTint(
                ContextCompat.getColor(this, if (online) R.color.online_green else R.color.offline_red)
            )
            binding.connectivityBar.connectivityLabel.text = getString(if (online) R.string.online else R.string.offline)
            if (online) SyncScheduler.syncNow(this)
        }.launchIn(lifecycleScope)

        AppDatabase.get(this).pendingScanDao().watchUnsyncedCount().onEach { count ->
            if (count > 0) {
                binding.connectivityBar.unsyncedLabel.visibility = android.view.View.VISIBLE
                binding.connectivityBar.unsyncedLabel.text = getString(R.string.unsynced_format, count)
            } else {
                binding.connectivityBar.unsyncedLabel.visibility = android.view.View.GONE
            }
        }.launchIn(lifecycleScope)

        lifecycleScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val stasiunId = StationSession.currentStasiunId()
        if (stasiunId == null) {
            goToLogin()
            return
        }
        myStasiunId = stasiunId

        try {
            FirebaseMessaging.getInstance().subscribeToTopic("naruna_station_$stasiunId").await()
        } catch (e: Exception) {
            // Non-fatal — the queue itself doesn't depend on push notifications.
        }

        val stasiunSnap = db.collection("stasiun_kerja").get().await()
        stasiunOrdered = stasiunSnap.documents.map { StasiunKerja.fromSnapshot(it) }.sortedBy { it.urutanTahap }
        supportActionBar?.title = stasiunOrdered.firstOrNull { it.id == stasiunId }?.nama ?: getString(R.string.app_name)

        val itemsSnap = db.collection("items").get().await()
        itemsMap = itemsSnap.documents.associate { it.id to Item.fromSnapshot(it) }

        val materialsSnap = db.collection("materials").get().await()
        materialsMap = materialsSnap.documents.associate { it.id to MaterialInfo(it.id, it.getString("nama") ?: "?") }

        val colorsSnap = db.collection("colors").get().await()
        colorsMap = colorsSnap.documents.associate { it.id to ColorInfo(it.id, it.getString("nama") ?: "?") }

        db.collection("work_groups").whereEqualTo("status", "aktif")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val all = snap.documents.mapNotNull { WorkGroup.fromSnapshot(it) }
                val mine = all.filter { wg ->
                    val item = itemsMap[wg.itemId] ?: return@filter false
                    nextStasiunFor(wg, item, stasiunOrdered)?.id == myStasiunId
                }.sortedBy { it.urutanPrioritas }

                binding.queueEmpty.visibility = if (mine.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                adapter.submit(mine, itemsMap, materialsMap, colorsMap)
            }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, getString(R.string.menu_switch_account))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            FirebaseAuth.getInstance().signOut()
            goToLogin()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
