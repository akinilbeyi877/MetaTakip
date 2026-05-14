package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.OrderStatusType

/**
 * 📦 Order = Klasör
 * Altında Product'lar var
 */
data class OrderNode(
    // Sipariş ID
    val orderId: Long,

    // UI'da görünen sipariş numarası
    val orderNumber: String,

    // Sipariş silinmiş mi?
    val isDeleted: Boolean,

    // Sipariş durumu - DEFAULT DEĞER EKLENDİ
    val status: OrderStatusType = OrderStatusType.ActiveWithDeletedProducts,

    // ✅ YENİ: Sipariş durumu metni ("Teslim Alındı", "Hazırlanıyor" vb.)
    val orderStatus: String? = null,

    // Uyarı metni (opsiyonel)
    val warningText: String? = null,

    // Geri Al butonu gösterilsin mi?
    val canRestore: Boolean = false,

    // Ürünler (leaf node)
    val products: List<ProductNode> = emptyList(),

    // YENİ: UI özellikleri
    @DrawableRes
    val iconResId: Int = R.drawable.ic_order_24,        // 📦 sipariş ikonu

    val colorResId: Int = R.color.status_deleted,       // Durum rengi (resource ID)

    val itemCount: String = "0 ürün",     // "2 ürün" veya "1 silinmiş ürün"

    // YENİ: Teslimat durumu (görselde "Teslim Alındı")
    val isDelivered: Boolean = false,

    // YENİ: Ürünlerin genel durumu (görselde "[Aktif]")
    val productAggregateStatus: ProductAggregateStatus = ProductAggregateStatus.ACTIVE,

    // 🔑 UI STATE → açık / kapalı
    var isExpanded: Boolean = false
) {

    // YENİ: Ürünlerin toplu durumu enum'u
    enum class ProductAggregateStatus {
        ACTIVE,         // Tüm ürünler aktif
        MIXED,          // Bazı aktif bazı silinmiş
        ALL_DELETED,    // Tüm ürünler silinmiş
        DELIVERED       // Teslim edilmiş
    }

    // ✅ YENİ: OrderStatus'u gösteren fonksiyon
    fun getOrderStatusText(): String {
        return orderStatus ?: getStatusLabel()
    }

    // ✅ Mevcut getStatusLabel()'ı güncelleyin - orderStatus'u da kullan
    fun getStatusLabel(): String {
        // İsterseniz orderStatus'u öncelikli yapabilirsiniz
        return if (!orderStatus.isNullOrEmpty() &&
            (orderStatus == "Teslim Alındı" ||
                    orderStatus == "Hazırlanıyor" ||
                    orderStatus == "İptal Edildi")) {
            orderStatus
        } else {
            when (status) {
                OrderStatusType.Deleted -> "Silinmiş Sipariş"
                OrderStatusType.ActiveButCustomerDeleted -> "Aktif (Müşteri Silinmiş)"
                OrderStatusType.ActiveWithDeletedProducts -> "Aktif (Silinmiş Ürün Var)"
            }
        }
    }

    // YENİ: Renk kodları için yardımcı fonksiyon
    @ColorInt
    fun getStatusColorInt(): Int {
        return when (status) {
            OrderStatusType.Deleted -> Color.parseColor("#DC3545") // Kırmızı
            OrderStatusType.ActiveButCustomerDeleted -> Color.parseColor("#FD7E14") // Turuncu
            OrderStatusType.ActiveWithDeletedProducts -> Color.parseColor("#FFC107") // Sarı
        }
    }

    // YENİ: Badge arkaplan rengi (resource ID)
    fun getStatusBadgeResId(): Int {
        return when (status) {
            OrderStatusType.Deleted -> R.drawable.badge_deleted
            OrderStatusType.ActiveButCustomerDeleted -> R.drawable.badge_warning
            OrderStatusType.ActiveWithDeletedProducts -> R.drawable.badge_mixed
        }
    }

    // YENİ: Ürün durumu badge rengi
    fun getProductStatusBadgeResId(): Int {
        return when (productAggregateStatus) {
            ProductAggregateStatus.ACTIVE -> R.drawable.badge_active
            ProductAggregateStatus.MIXED -> R.drawable.badge_mixed
            ProductAggregateStatus.ALL_DELETED -> R.drawable.badge_deleted
            ProductAggregateStatus.DELIVERED -> R.drawable.badge_success
        }
    }

    // YENİ: Ürün durumu metni (görseldeki "[Aktif]" için)
    fun getProductStatusText(): String {
        return when (productAggregateStatus) {
            ProductAggregateStatus.ACTIVE -> "AKTİF ÜRÜN"
            ProductAggregateStatus.MIXED -> "KARMA DURUM"
            ProductAggregateStatus.ALL_DELETED -> "SİLİNMİŞ ÜRÜN"
            ProductAggregateStatus.DELIVERED -> "TESLİM EDİLDİ"
        }
    }

    // YENİ: Sipariş durumu badge metni
    fun getOrderStatusBadgeText(): String {
        return when {
            isDeleted -> "SİLİNMİŞ SİPARİŞ"
            status == OrderStatusType.ActiveButCustomerDeleted -> "MÜŞTERİ SİLİNMİŞ"
            status == OrderStatusType.ActiveWithDeletedProducts -> "SİLİNMİŞ İÇERİK VAR"
            else -> "AKTİF SİPARİŞ"
        }
    }

    // YENİ: Kart arkaplan rengi
    fun getCardBackgroundResId(): Int {
        return when {
            isDeleted -> R.drawable.card_deleted
            status == OrderStatusType.ActiveWithDeletedProducts -> R.drawable.card_warning
            else -> R.drawable.card_normal
        }
    }

    // YENİ: İkon rengi resource ID
    fun getIconColorResId(): Int {
        return when {
            isDeleted -> R.color.icon_warning
            status == OrderStatusType.ActiveWithDeletedProducts -> R.color.icon_warning
            else -> R.color.icon_success
        }
    }

    // YENİ: Expand durumuna göre detaylı item count
    fun getDetailedItemCount(): String {
        return if (isExpanded) {
            val activeCount = products.count { !it.isDeleted }
            val deletedCount = products.count { it.isDeleted }

            when {
                activeCount > 0 && deletedCount > 0 -> "$activeCount aktif, $deletedCount silinmiş ürün"
                deletedCount > 0 -> "$deletedCount silinmiş ürün"
                else -> "$activeCount ürün"
            }
        } else {
            itemCount
        }
    }

    // YENİ: Uyarı metnini formatla (görseldeki gibi)
    fun getFormattedWarningText(): String {
        return if (!warningText.isNullOrEmpty()) {
            "⚠️ $warningText"
        } else if (status == OrderStatusType.ActiveWithDeletedProducts) {
            "⚠️ Bu siparişte silinmiş ürün(ler) var"
        } else if (status == OrderStatusType.ActiveButCustomerDeleted) {
            "⚠️ Müşteri silinmiş"
        } else {
            ""
        }
    }

    // YENİ: Teslimat bilgisi metni (görseldeki "Teslim Alındı")
    fun getDeliveryInfoText(): String {
        return if (isDelivered) {
            "Sipariş ✓ Teslim Alındı"
        } else {
            "Sipariş ⏳ Teslim Bekleniyor"
        }
    }

    // YENİ: Geri yükle butonu metni
    fun getRestoreButtonText(): String {
        return when {
            isDeleted -> "SİPARİŞİ GERİ YÜKLE"
            status == OrderStatusType.ActiveWithDeletedProducts -> "SİLİNEN ÜRÜNLERİ GERİ YÜKLE"
            else -> "DETAYLARI GÖRÜNTÜLE"
        }
    }

    // Yardımcı fonksiyon: Expand/collapse durumuna göre ikon değiştir
    fun getExpandIconResId(): Int {
        return if (isExpanded) {
            R.drawable.ic_expand_less_24
        } else {
            R.drawable.ic_expand_more_24
        }
    }

    // Yardımcı fonksiyon: Uyarı metni gösterilmeli mi?
    fun shouldShowWarning(): Boolean {
        return (!warningText.isNullOrEmpty() ||
                status == OrderStatusType.ActiveWithDeletedProducts ||
                status == OrderStatusType.ActiveButCustomerDeleted) &&
                status != OrderStatusType.Deleted
    }

    // Yardımcı fonksiyon: Silinmiş ürün sayısı
    fun getDeletedProductCount(): Int {
        return products.count { it.isDeleted }
    }

    // Yardımcı fonksiyon: Aktif ürün sayısı
    fun getActiveProductCount(): Int {
        return products.count { !it.isDeleted }
    }

    // YENİ: Aktif ürün var mı? (görseldeki [Aktif] için)
    fun hasActiveProducts(): Boolean {
        return products.any { !it.isDeleted }
    }

    // ✅ GÜNCELLENDİ: Kısa durum etiketi
    fun getShortStatusLabel(): String {
        return when {
            !orderStatus.isNullOrEmpty() -> orderStatus
            status == OrderStatusType.Deleted -> "Silinmiş"
            status == OrderStatusType.ActiveButCustomerDeleted -> "Müşteri Silinmiş"
            status == OrderStatusType.ActiveWithDeletedProducts -> "Silinmiş İçerik Var"
            else -> "Aktif"
        }
    }

    // YENİ: Factory metodu - test verisi oluşturma (görseldeki gibi)
    companion object {
        fun createExample(): OrderNode {
            return OrderNode(
                orderId = 12345L,
                orderNumber = "#12345",
                isDeleted = true,
                status = OrderStatusType.Deleted,
                orderStatus = "Teslim Alındı",  // ✅ Örnek orderStatus
                warningText = "Atif – silinmiş içerik var",
                canRestore = true,
                products = listOf(
                    ProductNode.createExample()
                ),
                iconResId = R.drawable.ic_order_24,
                colorResId = R.color.status_deleted,
                itemCount = "1 ürün",
                isDelivered = true,
                productAggregateStatus = ProductAggregateStatus.ACTIVE
            )
        }

        // YENİ: Görseldeki örneğe tam uygun
        fun createFromImageExample(): OrderNode {
            return OrderNode(
                orderId = 1L,
                orderNumber = "1 sipariş",
                isDeleted = true,
                status = OrderStatusType.Deleted,
                orderStatus = "Teslim Alındı",  // ✅ Örnek orderStatus
                warningText = "Atif – silinmiş içerik var",
                canRestore = true,
                products = listOf(
                    ProductNode(
                        productId = 1L,
                        productName = "Halı",
                        isDeleted = false,
                        canRestore = false,
                        iconResId = R.drawable.ic_product_24,
                        colorResId = R.color.status_active
                    )
                ),
                iconResId = R.drawable.ic_order_24,
                colorResId = R.color.status_deleted,
                itemCount = "1 ürün",
                isDelivered = true,
                productAggregateStatus = ProductAggregateStatus.ACTIVE
            )
        }
    }
}