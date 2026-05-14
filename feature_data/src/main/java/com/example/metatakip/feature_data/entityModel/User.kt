package com.example.metatakip.feature_data.entityModel

import java.util.UUID

/**
 * 👤 Sisteme giriş yapan kullanıcı
 */
data class User(
    val id: Long,
    val username: String,
    val password: String,
    val fullName: String,
    val role: UserRole,

    // 🌍 KÜRESEL KİMLİK
    val uuid: String = UUID.randomUUID().toString(),

    val updatedAt: Long = System.currentTimeMillis()
)
