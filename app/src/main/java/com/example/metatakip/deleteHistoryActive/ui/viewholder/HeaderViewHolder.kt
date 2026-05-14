package com.example.metatakip.deleteHistoryActive.ui.viewholder

import android.content.res.Resources
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.ui.UiRow

class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    // SADECE layout'ta olan view'ları tanımlayın
    private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
    private val textCount: TextView = itemView.findViewById(R.id.textCount)
    private val buttonExpand: ImageButton = itemView.findViewById(R.id.buttonExpand)

    // badgeHeaderType TAMAMEN KALDIRILDI - layout'ta yok

    fun bind(
        header: UiRow.Header,
        onHeaderClick: (UiRow.Header) -> Unit
    ) {
        // Başlık ve sayı
        textTitle.text = header.title
        textCount.text = "(${header.count})"

        // Header türüne göre başlık rengi (badge yoksa direkt textTitle'da göster)
        val titleWithIcon = getHeaderTitleWithIcon(header.title)
        textTitle.text = titleWithIcon

        // Header türüne göre metin rengi
        val titleColor = getHeaderColor(header.title)
        textTitle.setTextColor(ContextCompat.getColor(itemView.context, titleColor))

        // Count rengi
        textCount.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))

        // Expand ikonu
        val expandIcon = if (header.isExpanded) {
            R.drawable.ic_expand_less_24
        } else {
            R.drawable.ic_expand_more_24
        }

        try {
            buttonExpand.setImageResource(expandIcon)
        } catch (e: Resources.NotFoundException) {
            // Sistem ikonlarına fallback
            buttonExpand.setImageResource(
                if (header.isExpanded) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )
        }

        // Expand ikonu rengi
        buttonExpand.setColorFilter(
            ContextCompat.getColor(itemView.context, R.color.icon_primary)
        )

        // Tıklama olayı
        itemView.setOnClickListener { onHeaderClick(header) }
        buttonExpand.setOnClickListener { onHeaderClick(header) }
    }

    private fun getHeaderTitleWithIcon(title: String): String {
        return when {
            title.contains("Silinen", ignoreCase = true) -> "🗑️ $title"
            title.contains("Aktif", ignoreCase = true) -> "✓ $title"
            title.contains("Geçmiş", ignoreCase = true) -> "📅 $title"
            title.contains("Deleted", ignoreCase = true) -> "🗑️ $title"
            title.contains("Active", ignoreCase = true) -> "✓ $title"
            else -> title
        }
    }

    private fun getHeaderColor(title: String): Int {
        return when {
            title.contains("Silinen", ignoreCase = true) ||
                    title.contains("Deleted", ignoreCase = true) -> R.color.status_deleted

            title.contains("Aktif", ignoreCase = true) ||
                    title.contains("Active", ignoreCase = true) -> R.color.status_active

            title.contains("Geçmiş", ignoreCase = true) ||
                    title.contains("History", ignoreCase = true) -> R.color.status_warning

            else -> R.color.text_primary
        }
    }
}