package com.example.metatakip.deleteHistoryActive.ui.viewholder

import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.ProductNode
import com.example.metatakip.deleteHistoryActive.ui.UiRow

/**
 * =====================================================
 * ProductViewHolder
 *
 * KURALLAR:
 * 1) Sağ badge = ÜRÜNÜN GÜNCEL DURUMU
 *      - AKTİF
 *      - SİLİNDİ
 *
 * 2) Orta açıklama = Ürünün durumu / nedeni
 *      - Modelden (getStatusLabel)
 *
 * 3) Hard-code yok, UI sadece gösterir
 * =====================================================
 */
class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    // =====================================================
    // Views (row_product.xml)
    // =====================================================

    private val iconProduct: ImageView =
        itemView.findViewById(R.id.iconProduct)

    private val textProductName: TextView =
        itemView.findViewById(R.id.textProductName)

    /** Orta açıklama (ürün durumu / nedeni) */
    private val textProductStatus: TextView =
        itemView.findViewById(R.id.textProductStatus)

    /** Sağ badge (AKTİF / SİLİNDİ) */
    private val badgeProductStatus: TextView =
        itemView.findViewById(R.id.badgeProductStatus)

    // =====================================================
    // Bind
    // =====================================================

    fun bind(
        uiRow: UiRow.Product,
        onRestoreClick: (ProductNode) -> Unit
    ) {
        val product = uiRow.product

        // =====================================================
        // DEBUG (gerekirse silinebilir)
        // =====================================================
        Log.d(
            "PRODUCT_VIEW",
            "Product='${product.productName}', isDeleted=${product.isDeleted}"
        )

        // =====================================================
        // 1️⃣ ÜRÜN ADI
        // =====================================================
        textProductName.text = "Ürün Cinsi: ${product.productName}"

        // =====================================================
        // 2️⃣ ORTA AÇIKLAMA (GERÇEK DURUM / NEDEN)
        // =====================================================
        // Bu metin modelden gelir, UI karar vermez
        textProductStatus.text = product.getStatusLabel()

        // =====================================================
        // 3️⃣ SAĞ BADGE (GERÇEK GÜNCEL DURUM)
        // =====================================================
        if (product.isDeleted) {
            badgeProductStatus.text = "SİLİNDİ"
            badgeProductStatus.setBackgroundResource(
                R.drawable.bg_status_tag_small_deleted
            )
            iconProduct.setColorFilter(
                ContextCompat.getColor(itemView.context, R.color.icon_warning)
            )
        } else {
            badgeProductStatus.text = "AKTİF"
            badgeProductStatus.setBackgroundResource(
                R.drawable.bg_status_tag_small_active
            )
            iconProduct.setColorFilter(
                ContextCompat.getColor(itemView.context, R.color.icon_success)
            )
        }

        badgeProductStatus.setTextColor(
            ContextCompat.getColor(itemView.context, R.color.white)
        )

        // =====================================================
        // 4️⃣ İKON
        // =====================================================
        iconProduct.setImageResource(product.iconResId)

        // =====================================================
        // 5️⃣ RESTORE AKSİYONU
        // =====================================================
        itemView.setOnClickListener {
            if (product.canRestore) {
                onRestoreClick(product)
            }
        }
    }
}
