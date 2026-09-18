package com.aiappbuilder.narunaproduksi.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiappbuilder.narunaproduksi.data.ColorInfo
import com.aiappbuilder.narunaproduksi.data.Item
import com.aiappbuilder.narunaproduksi.data.MaterialInfo
import com.aiappbuilder.narunaproduksi.data.WorkGroup
import com.aiappbuilder.narunaproduksi.databinding.ItemWorkGroupBinding
import java.text.SimpleDateFormat
import java.util.Locale

class WorkGroupAdapter(
    private val onClick: (WorkGroup) -> Unit
) : RecyclerView.Adapter<WorkGroupAdapter.ViewHolder>() {

    private var rows: List<WorkGroup> = emptyList()
    private var items: Map<String, Item> = emptyMap()
    private var materials: Map<String, MaterialInfo> = emptyMap()
    private var colors: Map<String, ColorInfo> = emptyMap()
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))

    fun submit(
        rows: List<WorkGroup>,
        items: Map<String, Item>,
        materials: Map<String, MaterialInfo>,
        colors: Map<String, ColorInfo>
    ) {
        this.rows = rows
        this.items = items
        this.materials = materials
        this.colors = colors
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemWorkGroupBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorkGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wg = rows[position]
        val itemName = items[wg.itemId]?.nama ?: "?"
        val materialName = materials[wg.materialId]?.nama ?: "?"
        val colorName = colors[wg.colorId]?.nama ?: "?"
        holder.binding.itemName.text = itemName
        holder.binding.itemMaterialColor.text = "$materialName / $colorName"
        holder.binding.itemDeadline.text = wg.tenggatProyeksi?.let { "Target: ${dateFormat.format(it)}" } ?: ""
        holder.binding.root.setOnClickListener { onClick(wg) }
    }

    override fun getItemCount(): Int = rows.size
}
