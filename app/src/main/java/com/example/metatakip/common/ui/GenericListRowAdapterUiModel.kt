package com.example.metatakip.common.ui


import android.graphics.Color

//Entity → UI Model dönüşümü


data class GenericListRowAdapterUiModel(

    // 🔹 Zorunlu Alanlar
    val id: Long,
    val title: String,

    // 🔹 Opsiyonel Metinler
    val subtitle: String? = null,
    val badgeText: String? = null,

    // 🔹 Badge Görünümü
    val badgeColor: Int? = null,
    val badgeTextColor: Int = Color.WHITE,

    // 🔹 Opsiyonel UI Özellikleri
    val iconResId: Int? = null,
    val showCheckbox: Boolean = false,
    val isChecked: Boolean = false,
    val showActions: Boolean = true,
    val isActive: Boolean = true,

    // 🔥 Gerçek Entity (Customer, Order, Firma vs.)
    val payload: Any? = null,

    // 🔹 Extra alanlar (ileride genişletme için)
    val extraFields: Map<String, String> = emptyMap()
) {

    fun getExtraField(key: String): String? =
        extraFields[key]

    inline fun <reified T> getPayloadAs(): T? =
        payload as? T
}