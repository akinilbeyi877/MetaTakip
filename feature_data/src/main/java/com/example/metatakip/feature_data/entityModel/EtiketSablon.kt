package com.example.metatakip.feature_data.entityModel

data class EtiketSablon(
    val id: Long = 0L,
    val userId: Int,
    val adi: String,
    val firmaId: Long = 0L,
    val firmaUuid: String = "",
    val varsayilan: Boolean = false,
    val createdAt: Long = 0L,
    val firmaAdi: String = ""
)
