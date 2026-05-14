package com.example.metatakip.feature_data.label

import com.example.metatakip.feature_data.entityModel.EtiketSablon
import com.example.metatakip.feature_data.entityModel.EtiketSayfaAyar

interface EtiketSablonDaoInterface {

    fun createSablon(
        userId: Int,
        adi: String,
        firmaId: Long = 0L,
        firmaUuid: String = "",
        varsayilan: Boolean = false
    ): Long

    fun getAllSablonlar(): List<EtiketSablon>
    fun getAllSablonlar(userId: Int): List<EtiketSablon>

    fun getSablonById(id: Long): EtiketSablon?

    fun setVarsayilanSablon(userId: Int, sablonId: Long): Boolean

    fun deleteSablon(sablonId: Long)

    fun saveBilesenler(
        sablonId: Long,
        bilesenler: List<EtiketManager.EtiketBileseni>
    )

    fun loadBilesenSecimleri(
        sablonId: Long,
        all: MutableList<EtiketManager.EtiketBileseni>
    )

    fun saveSayfaAyar(sablonId: Long, ayar: EtiketSayfaAyar)

    fun loadSayfaAyar(sablonId: Long): EtiketSayfaAyar?
}
