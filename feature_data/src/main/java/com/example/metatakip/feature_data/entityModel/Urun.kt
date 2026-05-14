package com.example.metatakip.feature_data.entityModel

import java.io.Serializable
import java.util.UUID

data class Urun(
    // 🏠 Yerel Anahtar
    val id: Long = 0L,

    // 🌍 KÜRESEL KİMLİK: Ürünün kendi benzersiz kodu
    val uuid: String = UUID.randomUUID().toString(),

    // 🔗 YEREL BAĞ
    val siparisId: Long,

    // 🔗 KÜRESEL BAĞ: Siparişin UUID'si (Senkronizasyonun kalbi)
    var siparisUuid: String = "",

    val ad: String,
    val urunTipi: String = "",
    val adet: Int,
    val m2: Double,
    val fiyat: Double,
    val tutar: Double,

    // 🗑️ SOFT DELETE
    val isDeleted: Int = 0,

    // ⏱️ SENKRONİZASYON ZAMAN DAMGASI
    var updatedAt: Long = System.currentTimeMillis()

) : Serializable