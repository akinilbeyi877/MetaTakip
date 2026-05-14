package com.example.metatakip.data.metaTakipDb.model

import java.util.Date

data class DeletedSiparis(
    val id: Int,
    val siparisId: Int, // Orijinal sipariş ID'si
    val musteriAdi: String,
    val urunTipi: String,
    val miktar: Int,
    val birimFiyat: Double,
    val toplamTutar: Double,
    val siparisTarihi: Date?,
    val teslimTarihi: Date?,
    val durum: String,
    val silmeNedeni: String?,
    val deletedByUserId: Int, // Silen kullanıcı ID'si
    val deletedByUsername: String?, // Silen kullanıcı adı
    val deletedAt: Date?, // Silinme tarihi
    val isPermanent: Boolean = false // Kalıcı olarak silinmiş mi?
)