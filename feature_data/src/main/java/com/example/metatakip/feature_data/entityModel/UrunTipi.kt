package com.example.metatakip.feature_data.entityModel

import java.util.UUID

data class UrunTipi(
    val id: Long = 0,
    val ad: String,
    val birimFiyat: Double,
    val hesapTipi: String,
    val aktif: Int,
    val aciklama: String? = null,

    // 🌍 KÜRESEL KİMLİK
    val uuid: String = UUID.randomUUID().toString(),

    val isDeleted: Int = 0,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) {
    fun toDisplayString(): String = ad
    fun isActive(): Boolean = aktif == 1

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "ad" to ad,
        "birimFiyat" to birimFiyat,
        "hesapTipi" to hesapTipi,
        "aktif" to aktif,
        "aciklama" to (aciklama ?: ""),
        "uuid" to uuid,
        "isDeleted" to isDeleted,
        "createdAt" to (createdAt ?: ""),
        "updatedAt" to (updatedAt ?: "")
    )

    fun toCsvRow(): String = listOf(
        ad, birimFiyat.toString(), hesapTipi,
        if (aktif == 1) "Aktif" else "Pasif",
        aciklama ?: ""
    ).joinToString(",")
}
