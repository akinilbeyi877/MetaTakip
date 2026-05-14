package com.example.metatakip.feature.firma.data



import com.example.metatakip.feature_data.entityModel.Firma

interface MetaTakipFirmaDaoInterface {

    fun addFirma(firma: Firma): Long

    fun updateFirmaById(id: Long, firma: Firma): Boolean

    fun deleteFirma(id: Long): Boolean

    fun getAllFirmalar(): List<Firma>

    fun getFirmaById(id: Long): Firma?
}