package com.example.metatakip.feature.label.providers

import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField
import dao.MetaTakipFirmaDao

object FeatureLabelTableFormProvider {

    fun canHandle(table: String): Boolean {
        return table.equals("etiket_sablon", ignoreCase = true)
    }

    fun getFormFields(firmaDao: MetaTakipFirmaDao? = null): List<FormField> {
        val fields = mutableListOf<FormField>()

        val firmaList = runCatching { firmaDao?.getAllFirmalar() }.getOrDefault(emptyList()) ?: emptyList()

        // id → displayName  (dropdown için)
        val firmaOptionMap = firmaList.associate {
            (it.firmaAdi ?: "Firma #${it.id}") to it.id.toString()
        }

        // id → uuid  (kayıt sırasında lookup'ı önlemek için)
        val firmaUuidMap = firmaList.associate {
            it.id.toString() to (it.uuid ?: "")
        }

        // Dropdown: firma_id
        fields += FormField(
            label     = "Firma",
            key       = "firma_id",
            type      = FieldType.DROPDOWN,
            optionMap = if (firmaOptionMap.isEmpty()) mapOf("Firma bulunamadı" to "0") else firmaOptionMap,
            value     = if (firmaOptionMap.isNotEmpty()) firmaOptionMap.values.first() else "0",
            isRequired = false
        )

        // Hidden: firma_uuid — seçilen firmanın uuid'si
        val firstUuid = if (firmaList.isNotEmpty()) firmaUuidMap[firmaList.first().id.toString()].orEmpty() else ""
        fields += FormField(
            label = "Firma UUID",
            key   = "firma_uuid",
            type  = FieldType.HIDDEN,
            value = firstUuid
        )

        // Şablon adı
        fields += FormField(
            label      = "Şablon Adı",
            key        = "adi",
            type       = FieldType.TEXT,
            isRequired = true
        )

        return fields
    }
}
