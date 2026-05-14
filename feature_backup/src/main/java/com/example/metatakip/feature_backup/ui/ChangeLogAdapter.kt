package com.example.metatakip.feature_backup.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.feature_backup.data.ChangeLog

/**
 * 📝 Değişiklik Günlüğü Adaptörü
 * Veritabanındaki yerel veya uzak değişimleri görselleştirir.
 */
class ChangeLogAdapter : RecyclerView.Adapter<ChangeLogAdapter.ViewHolder>() {

    private var changes: List<ChangeLog> = emptyList()

    /**
     * Listeyi günceller. DiffUtil ile performans ve animasyon iyileştirmesi.
     */
    fun submitList(newChanges: List<ChangeLog>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = changes.size
            override fun getNewListSize() = newChanges.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                changes[oldPos].id == newChanges[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                changes[oldPos] == newChanges[newPos]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        changes = newChanges
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // ✅ DÜZELTİLDİ: Layout kaynağı kullan (android.R.layout.simple_list_item_1)
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)

        // TextView'i layout içinden bul (Android'in varsayılan ID'si text1)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        // Özel padding ve text boyutu (isteğe bağlı)
        textView.setPadding(32, 24, 32, 24)
        textView.textSize = 14f

        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val change = changes[position]

        // Gösterilecek mesajı oluştur
        val displayMessage = buildString {
            append(change.getDisplayMessage())
            // UUID varsa son 8 hanesini göster (debug amaçlı)
            val uuid = change.getUuidFromDetails()
            if (uuid != null) {
                append("\n🔑 UUID: ...${uuid.takeLast(8)}")
            }
        }

        holder.textView.text = displayMessage

        // Aksiyon tipine göre arka plan ve yazı rengi
        val (bgColor, textColor) = when (change.actionType) {
            ChangeLog.ActionType.INSERT -> Color.parseColor("#E8F5E9") to Color.parseColor("#2E7D32")
            ChangeLog.ActionType.UPDATE -> Color.parseColor("#FFF3E0") to Color.parseColor("#EF6C00")
            ChangeLog.ActionType.DELETE -> Color.parseColor("#FFEBEE") to Color.parseColor("#C62828")
        }

        holder.textView.setBackgroundColor(bgColor)
        holder.textView.setTextColor(textColor)
    }

    override fun getItemCount(): Int = changes.size

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}