package com.example.metatakip.feature_data.entityModel

import java.util.UUID

data class MesajSablon(
    val id: Long,
    val baslik: String,
    val firmaid: Long?,
    val firmaAdi: String?,
    val tip: String = "",

    // 🌍 KÜRESEL KİMLİK
    val uuid: String = UUID.randomUUID().toString(),

    val musteriOlustuMesaj: String = "",
    val musteriGuncellendiMesaj: String = "",
    val siparisOlustuMesaj: String = "",
    val siparisUrunEklendiMesaj: String = "",
    val smsOnayMesaj: String = "",
    val whatsappOnayMesaj: String = "",
    val varsayilan: Boolean = false,
    val isDeleted: Boolean = false,
    val olusturulmaTarihi: Long? = null,
    val guncellemeTarihi: Long? = null
)
