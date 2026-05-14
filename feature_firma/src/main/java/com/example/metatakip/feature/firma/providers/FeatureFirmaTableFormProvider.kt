package com.example.metatakip.feature.firma.providers


import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField

/**
 * 🏢 FeatureFirmaTableFormProvider
 * GenericBuildFormForTable için firma form alanlarını sağlar.
 */
object FeatureFirmaTableFormProvider {

    fun canHandle(table: String): Boolean {
        return table.equals("firma", ignoreCase = true) ||
                table.equals("company", ignoreCase = true) ||
                table.equals("companies", ignoreCase = true)
    }

    fun getFormFields(): List<FormField> {
        return mutableListOf<FormField>().apply {

            add(
                FormField(
                    label = "Firma Adı",
                    key = "firmaAdi",
                    type = FieldType.TEXT,
                    isRequired = true,
                    placeholder = "Firma adı"
                )
            )

            add(
                FormField(
                    label = "Adres",
                    key = "adres",
                    type = FieldType.TEXT,
                    isRequired = false,
                    placeholder = "Firma adresi"
                )
            )

            add(
                FormField(
                    label = "Telefon",
                    key = "telefon",
                    type = FieldType.PHONE,
                    isRequired = false,
                    placeholder = "0(5xx) xxx xx xx"
                )
            )

            add(
                FormField(
                    label = "Vergi No",
                    key = "vergiNo",
                    type = FieldType.TEXT,
                    isRequired = false,
                    placeholder = "Vergi numarası"
                )
            )
        }
    }
}