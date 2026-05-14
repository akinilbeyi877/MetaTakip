package com.example.metatakip.feature_data.entityModel

import java.util.UUID

/**
 * 🏢 Firma Entity Modeli
 * Hibrit senkronizasyon: uuid her firmaya tekil küresel kimlik verir.
 */
data class Firma(
    var id: Long = 0L,
    var firmaAdi: String = "",
    var adres: String? = null,
    var telefon: String? = null,
    var vergiNo: String? = null,

    // 🌍 KÜRESEL KİMLİK — null değil, her zaman üretilir
    var uuid: String = UUID.randomUUID().toString(),

    // 🗑️ Soft Delete
    var isDeleted: Int = 0,
    var deletedAt: Long? = null,

    // ⏱️ Senkronizasyon
    var updatedAt: Long = System.currentTimeMillis(),
    var createdAt: Long = System.currentTimeMillis()
)
