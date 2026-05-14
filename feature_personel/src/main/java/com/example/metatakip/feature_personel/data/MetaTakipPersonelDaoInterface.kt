package com.example.metatakip.feature_personel.data


import com.example.metatakip.feature_data.entityModel.Personel

interface MetaTakipPersonelDaoInterface {

    fun addPersonel(personel: Personel): Boolean

    fun updatePersonelById(id: Long, personel: Personel): Boolean

    fun deletePersonel(id: Long): Boolean

    fun getAllPersonel(): List<Personel>

    fun getPersonelById(id: Long): Personel?
}