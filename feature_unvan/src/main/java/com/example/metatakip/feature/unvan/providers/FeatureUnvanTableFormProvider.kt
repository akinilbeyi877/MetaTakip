// File: FeatureUnvanTableFormProvider.kt
package com.example.metatakip.feature.unvan.providers

import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField

/**
 * 🏷️ FeatureUnvanTableFormProvider
 * GenericBuildFormForTable için unvan form alanlarını sağlar.
 */
object FeatureUnvanTableFormProvider {

    fun canHandle(table: String): Boolean {
        return table.equals("unvan", ignoreCase = true) ||
                table.equals("title", ignoreCase = true) ||
                table.equals("titles", ignoreCase = true)
    }

    fun getFormFields(): List<FormField> {
        return mutableListOf<FormField>().apply {
            add(
                FormField(
                    label = "Ad",
                    key = "ad",
                    type = FieldType.TEXT,
                    isRequired = true,
                    placeholder = "Ünvan adı"
                )
            )

            add(
                FormField(
                    label = "Açıklama",
                    key = "aciklama",
                    type = FieldType.TEXTAREA,
                    isRequired = false,
                    placeholder = "Ünvan açıklaması"
                )
            )
        }
    }
}