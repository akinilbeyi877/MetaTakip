package com.example.metatakip.deleteHistoryActive.ui.viewholder

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.OrderStatusType
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.OrderNode
import com.example.metatakip.deleteHistoryActive.ui.UiRow

/**
 * =====================================================
 * OrderViewHolder
 *
 * KURALLAR:
 * 1) Sağ badge = GÜNCEL DURUM
 *      - AKTİF
 *      - SİLİNDİ
 *
 * 2) Teslim durumu = SİLİNMEDEN ÖNCEKİ SON DURUM
 *      - Sadece bilgi amaçlı
 *      - Badge’i etkilemez
 *
 * 3) PASİF kavramı BİLİNÇLİ olarak YOK
 * =====================================================
 */
class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    // =====================================================
    // Views (row_order.xml)
    // =====================================================

    private val iconOrder: ImageView =
        itemView.findViewById(R.id.iconOrder)

    private val textOrderNumber: TextView =
        itemView.findViewById(R.id.textOrderNumber)

    /** Sağ üst badge (AKTİF / SİLİNDİ) */
    private val textOrderStatus: TextView =
        itemView.findViewById(R.id.textOrderStatus)

    private val buttonExpandOrder: ImageButton =
        itemView.findViewById(R.id.buttonExpandOrder)

    /** Orta açıklama: geçmiş teslim durumu + ürün sayısı */
    private val textProductCount: TextView =
        itemView.findViewById(R.id.textProductCount)

    private val buttonRestoreOrder: Button =
        itemView.findViewById(R.id.buttonRestoreOrder)

    // =====================================================
    // Bind
    // =====================================================

    fun bind(
        uiRow: UiRow.Order,
        onOrderClick: (OrderNode) -> Unit,
        onRestoreClick: (OrderNode) -> Unit
    ) {
        val order = uiRow.order

        // =====================================================
        // 1️⃣ ÜST BAŞLIK
        // =====================================================
        textOrderNumber.text = order.orderNumber

        // =====================================================
        // 2️⃣ ORTA AÇIKLAMA
        //    Teslim durumu = silinmeden ÖNCEKİ SON DURUM
        // =====================================================
        val previousDeliveryStatus =
            if (order.isDelivered) "Teslim Edildi" else "Teslim Edilmedi"

        textProductCount.text =
            "Silinmeden önceki durumu: $previousDeliveryStatus • ${order.itemCount}"

        // =====================================================
        // 3️⃣ SAĞ BADGE (GÜNCEL DURUM)
        // =====================================================
        val isOrderDeleted =
            order.isDeleted || order.status == OrderStatusType.Deleted

        if (isOrderDeleted) {
            // 🔴 Sipariş artık sistemde yok
            textOrderStatus.text = "SİLİNDİ"
            textOrderStatus.setBackgroundResource(
                R.drawable.bg_status_tag_small_deleted
            )
            iconOrder.setColorFilter(
                ContextCompat.getColor(itemView.context, R.color.icon_warning)
            )
        } else {
            // 🟢 Sipariş sistemde aktif
            textOrderStatus.text = "AKTİF"
            textOrderStatus.setBackgroundResource(
                R.drawable.bg_status_tag_small_active
            )
            iconOrder.setColorFilter(
                ContextCompat.getColor(itemView.context, R.color.icon_success)
            )
        }

        textOrderStatus.setTextColor(
            ContextCompat.getColor(itemView.context, R.color.white)
        )

        // =====================================================
        // 4️⃣ İKON
        // =====================================================
        iconOrder.setImageResource(order.iconResId)

        // =====================================================
        // 5️⃣ RESTORE BUTONU
        // =====================================================
        if (order.canRestore) {
            buttonRestoreOrder.visibility = View.VISIBLE
            buttonRestoreOrder.text = "SİPARİŞİ GERİ YÜKLE"
            buttonRestoreOrder.setOnClickListener {
                onRestoreClick(order)
            }
        } else {
            buttonRestoreOrder.visibility = View.GONE
        }

        // =====================================================
        // 6️⃣ EXPAND / COLLAPSE
        // =====================================================
        buttonExpandOrder.setImageResource(
            if (order.isExpanded)
                R.drawable.ic_expand_less_24
            else
                R.drawable.ic_expand_more_24
        )

        // =====================================================
        // 7️⃣ CLICK
        // =====================================================
        itemView.setOnClickListener { onOrderClick(order) }
        buttonExpandOrder.setOnClickListener { onOrderClick(order) }
    }
}
