package com.example.metatakip.feature.personel.providers

import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField
import com.example.metatakip.feature_data.unvan.UnvanDaoInterface

object FeaturePersonelTableFormProvider {

    fun canHandle(table: String): Boolean =
        table.equals("personel", ignoreCase = true)

    fun getFormFields(unvanDao: UnvanDaoInterface): List<FormField> {
        val unvanList: List<String> = runCatching {
            unvanDao.getAllUnvanlar().map { it.ad }
        }.getOrDefault(emptyList())

        return listOf(
            FormField(
                label = "Ad Soyad",
                key = "adSoyad",
                type = FieldType.TEXT,
                isRequired = true
            ),
            FormField(
                label = "Ünvan",
                key = "unvan",
                type = FieldType.DROPDOWN,
                options = if (unvanList.isEmpty()) listOf("Ünvan bulunamadı") else unvanList,
                isRequired = true
            )
        )
    }
}