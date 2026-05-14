package com.example.metatakip.feature_data.entityModel

import java.util.UUID

data class Personel(
    val id: Long = 0L,
    val adSoyad: String,
    val unvan: String,

    // 🌍 KÜRESEL KİMLİK
    val uuid: String = UUID.randomUUID().toString(),

    // 🗑️ Soft Delete
    val isDeleted: Int = 0,
    val deletedAt: Long? = null,

    // ⏱️ Senkronizasyon
    val updatedAt: Long = System.currentTimeMillis()
)
