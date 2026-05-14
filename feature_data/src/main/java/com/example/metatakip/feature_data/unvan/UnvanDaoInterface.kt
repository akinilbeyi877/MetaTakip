package com.example.metatakip.feature_data.unvan

import com.example.metatakip.feature_data.entityModel.Unvan

interface UnvanDaoInterface {

    fun addUnvan(unvan: Unvan): Long

    fun updateUnvanById(id: Long, unvan: Unvan): Boolean

    fun deleteUnvanById(id: Long): Boolean

    fun getUnvanById(id: Long): Unvan?

    fun getAllUnvanlar(): List<Unvan>
}