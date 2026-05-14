package com.example.metatakip.feature_data.entityModel

data class EtiketDataContext(
    val siparis: Order? = null,
    val musteri: Customer? = null,
    val firma: Firma? = null,
    val personel: Personel? = null,
    val user: User? = null,
    val urunler: List<Urun> = emptyList()
)
