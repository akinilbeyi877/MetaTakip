package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.ProductStatusTypeEnumUI

data class ProductNode(
    // Ürünün benzersiz ID'si
    val productId: Long,

    // Ürün adı (UI'da gösterim için)
    val productName: String,

    // Ürün gerçekten silinmiş mi?
    val isDeleted: Boolean = false,

    // Ürünün durumu (Deleted | Active)
    val status: ProductStatusTypeEnumUI = ProductStatusTypeEnumUI.ACTIVE,

    // Restore butonu gösterilsin mi?
    // SADECE ürün silinmişse true olur
    val canRestore: Boolean = false,

    // YENİ: UI özellikleri
    @DrawableRes
    val iconResId: Int = R.drawable.ic_product_24,        // 🧺 ürün ikonu

    val colorResId: Int = R.color.status_active,        // Durum rengi

    // YENİ: Teslimat durumu
    val isDelivered: Boolean = false,

    // YENİ: Ürün fiyatı (opsiyonel)
    val price: String? = null,

    // YENİ: Ürün açıklaması (property adını değiştirdim)
    val productDescription: String? = null,  // DÜZELTME: description -> productDescription

    // YENİ: Ürün kategorisi
    val category: String? = null
) {

    // YENİ: Badge metni
    fun getBadgeText(): String {
        return when {
            isDeleted && canRestore -> "SİLİNMİŞ"
            isDeleted && !canRestore -> "KALICI SİLİNDİ"
            isDelivered -> "TESLİM EDİLDİ"
            else -> "AKTİF"
        }
    }

    // YENİ: Badge rengi resource ID
    fun getBadgeResId(): Int {
        return when {
            isDeleted && canRestore -> R.drawable.badge_deleted
            isDeleted && !canRestore -> R.drawable.badge_inactive
            isDelivered -> R.drawable.badge_success
            else -> R.drawable.badge_active
        }
    }

    // YENİ: İkon rengi resource ID
    fun getIconColorResId(): Int {
        return when {
            isDeleted && canRestore -> R.color.icon_warning
            isDeleted && !productCanRestore() -> R.color.status_inactive  // DÜZELTME
            isDelivered -> R.color.icon_success
            else -> R.color.icon_success
        }
    }

    // YENİ: Yardımcı fonksiyon
    private fun productCanRestore(): Boolean {
        return isDeleted && canRestore
    }

    // YENİ: Kart arkaplan rengi
    fun getCardBackgroundResId(): Int {
        return when {
            isDeleted && canRestore -> R.drawable.card_deleted_light
            isDeleted && !canRestore -> R.drawable.card_inactive
            isDelivered -> R.drawable.card_success_light
            else -> R.drawable.card_normal
        }
    }

    // YENİ: Renk kodları için yardımcı fonksiyon
    @ColorInt
    fun getStatusColorInt(): Int {
        return when (status) {
            ProductStatusTypeEnumUI.DELETED -> Color.parseColor("#DC3545")
            ProductStatusTypeEnumUI.ACTIVE -> Color.parseColor("#28A745")
            ProductStatusTypeEnumUI.INACTIVE -> Color.parseColor("#6C757D")
            ProductStatusTypeEnumUI.PENDING -> Color.parseColor("#FFC107")
            ProductStatusTypeEnumUI.DELIVERED -> Color.parseColor("#198754")
        }
    }

    // YENİ: Geri yükle butonu metni
    fun getRestoreButtonText(): String {
        return if (isDeleted && canRestore) {
            "ÜRÜNÜ GERİ YÜKLE"
        } else if (isDeleted && !canRestore) {
            "KALICI SİLİNDİ"
        } else {
            "ÜRÜN DETAYLARI"
        }
    }

    // YENİ: Teslimat bilgisi metni
    fun getDeliveryInfoText(): String {
        return if (isDelivered) {
            "✓ Teslim Alındı"
        } else {
            "⏳ Teslim Bekleniyor"
        }
    }

    // YENİ: Fiyat bilgisi formatlı
    fun getFormattedPrice(): String {
        return price?.let { "₺$it" } ?: "Fiyat bilgisi yok"
    }

    // YENİ: Detaylı açıklama (fonksiyon adı değişmedi)
    fun getDetailedDescription(): String {
        return productDescription ?: "$productName ürünü"
    }

    // Yardımcı fonksiyon: Durum etiketi metni
    fun getStatusLabel(): String {
        return if (isDeleted) "Silinmiş Ürün" else "Aktif Ürün"
    }

    // Yardımcı fonksiyon: Ürün durumuna göre açıklama (fonksiyon adı değiştirildi)
    fun getStatusDescription(): String {
        return if (isDeleted) {
            if (canRestore) {
                "Bu ürün silinmiş. Geri yüklemek için butona tıklayın."
            } else {
                "Bu ürün kalıcı olarak silinmiş."
            }
        } else {
            "Bu ürün aktif durumda."
        }
    }

    // YENİ: Kısa özet
    fun getSummary(): String {
        val statusText = if (isDeleted) "Silinmiş" else "Aktif"
        val deliveryText = if (isDelivered) ", Teslim Edildi" else ", Teslim Bekleniyor"
        val priceText = price?.let { ", $it TL" } ?: ""

        return "$statusText$deliveryText$priceText"
    }

    // ✅ YENİ: "Ürün Cinsi:" formatlı ürün adı
    fun getFormattedProductName(): String {
        return "Ürün Cinsi: $productName"
    }

    // ✅ YENİ: Detaylı formatlı ürün adı (kategoriye göre)
    fun getDetailedProductName(): String {
        return buildString {
            append("Ürün Cinsi: ")
            append(productName)

            // Kategoriye göre ek bilgi
            when {
                category?.contains("Halı", ignoreCase = true) == true ->
                    append(" (Yer Kaplaması)")
                category?.contains("Kilim", ignoreCase = true) == true ->
                    append(" (El Dokuması)")
                category?.contains("Battaniye", ignoreCase = true) == true ->
                    append(" (Isı Yalıtım)")
                category?.contains("Mobilya", ignoreCase = true) == true ->
                    append(" (Ev Eşyası)")
                category?.contains("Tekstil", ignoreCase = true) == true ->
                    append(" (Kumaş Ürünü)")
                category?.contains("Aksesuar", ignoreCase = true) == true ->
                    append(" (Süs Eşyası)")
                // Diğer kategoriler...
            }
        }
    }

    // ✅ YENİ: Fiyatlı formatlı ürün adı
    fun getProductNameWithPrice(): String {
        return buildString {
            append("Ürün Cinsi: $productName")
            price?.let {
                append(" - ₺$it")
            }
        }
    }

    // ✅ YENİ: Duruma göre formatlı ürün adı
    fun getProductNameWithStatus(): String {
        return buildString {
            append("Ürün Cinsi: $productName")
            if (isDeleted) {
                append(" [Silinmiş]")
            } else if (isDelivered) {
                append(" [Teslim Edildi]")
            }
        }
    }

    // YENİ: Factory metodu - test verisi oluşturma
    companion object {
        fun createExample(): ProductNode {
            return ProductNode(
                productId = 1L,
                productName = "Halı",
                isDeleted = false,
                status = ProductStatusTypeEnumUI.ACTIVE,
                canRestore = false,
                iconResId = R.drawable.ic_product_24,
                colorResId = R.color.status_active,
                isDelivered = true,
                price = "1299.99",
                productDescription = "Kaliteli yün halı, 200x300 cm",
                category = "Ev Dekorasyon"
            )
        }

        // YENİ: Silinmiş ürün örneği
        fun createDeletedExample(): ProductNode {
            return ProductNode(
                productId = 2L,
                productName = "Masa",
                isDeleted = true,
                status = ProductStatusTypeEnumUI.DELETED,
                canRestore = true,
                iconResId = R.drawable.ic_product_24,
                colorResId = R.color.status_deleted,
                isDelivered = false,
                price = "899.99",
                productDescription = "Ahşap çalışma masası",
                category = "Mobilya"
            )
        }

        // YENİ: Kalıcı silinmiş ürün örneği
        fun createPermanentlyDeletedExample(): ProductNode {
            return ProductNode(
                productId = 3L,
                productName = "Sandalye",
                isDeleted = true,
                status = ProductStatusTypeEnumUI.DELETED,
                canRestore = false,  // Kalıcı silinmiş
                iconResId = R.drawable.ic_product_24,
                colorResId = R.color.status_inactive,
                isDelivered = false,
                price = "299.99",
                productDescription = "Ofis sandalyesi",
                category = "Mobilya"
            )
        }

        // YENİ: Görseldeki örnek için
        fun createFromImageExample(): ProductNode {
            return ProductNode(
                productId = 1L,
                productName = "Halı",
                isDeleted = false,
                status = ProductStatusTypeEnumUI.ACTIVE,
                canRestore = false,
                iconResId = R.drawable.ic_product_24,
                colorResId = R.color.status_active,
                isDelivered = true
            )
        }
    }
}