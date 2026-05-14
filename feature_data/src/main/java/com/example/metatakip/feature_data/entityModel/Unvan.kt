package com.example.metatakip.feature_data.entityModel

import java.util.UUID

data class Unvan(
    val id: Long = 0L,
    val ad: String,
    val aciklama: String? = null,

    // 🌍 KÜRESEL KİMLİK
    val uuid: String = UUID.randomUUID().toString(),

    // 🗑️ Soft Delete
    val isDeleted: Int = 0,
    val deletedAt: Long? = null,

    // ⏱️ Senkronizasyon
    val updatedAt: Long = System.currentTimeMillis()
)
