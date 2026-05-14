package com.example.metatakip.feature_data.ui

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * Generic list ekranlarında (Customer, Order, Firma, Personel, EtiketSablon, UrunTipi ...)
 * tek satır UI temsilini taşır.
 *
 * Entity'ler payload içinde saklanır, adapter sadece bu modeli çizer.
 */
data class GenericListRowAdapterUiModel(

    // ---------------------------
    // Kimlik / içerik
    // ---------------------------
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,          // 3. satır istersen (opsiyonel)

    // ---------------------------
    // Badge / etiket alanı
    // ---------------------------
    val badgeText: String? = null,
    @ColorInt val badgeColor: Int? = null,    // arkaplan veya vurgu rengi
    @ColorInt val badgeTextColor: Int? = null,

    // ---------------------------
    // Sol ikon / avatar
    // ---------------------------
    @DrawableRes val iconResId: Int? = null,
    val photoPath: String? = null,           // 📸 Profil/Sipariş fotoğraf yolu

    // ---------------------------
    // Durum / görünürlük
    // ---------------------------
    val isActive: Boolean = true,
    val isEnabled: Boolean = true,
    val isHidden: Boolean = false,

    // ---------------------------
    // Seçim / checkbox
    // ---------------------------
    val showCheckbox: Boolean = false,
    val isChecked: Boolean = false,

    // ---------------------------
    // Aksiyonlar
    // ---------------------------
    val showActions: Boolean = true,
    val actionHint: String? = null,

    // ---------------------------
    // Sıralama / gruplama
    // ---------------------------
    val sortKey: Long? = null,
    val groupKey: String? = null,
    val tags: List<String> = emptyList(),

    // ---------------------------
    // Asıl entity (Customer/Order/...)
    // 🔥 Artık nullable değil
    // ---------------------------
    val payload: Any,

    // ---------------------------
    // Ek alanlar (esnek genişletme)
    // ---------------------------
    val extraFields: Map<String, String> = emptyMap()

) {

    fun extra(key: String): String? = extraFields[key]

    inline fun <reified T> payloadAs(): T? = payload as? T
}