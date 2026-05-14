package com.example.metatakip.feature.order.providers

import com.example.metatakip.feature_data.entityModel.FieldType
import com.example.metatakip.feature_data.entityModel.FormField

/**
 * 🏗️ FeatureOrderBilgiEkleTableFormProvider
 * GenericBuildFormForTable için siparis_bilgi_ekle form alanlarını sağlar
 */
object FeatureOrderBilgiEkleTableFormProvider {

    fun canHandle(table: String): Boolean {
        return table.equals("siparis_bilgi_ekle", ignoreCase = true) ||
                table.equals("order_item_add", ignoreCase = true) ||
                table.equals("order_detail_add", ignoreCase = true)
    }

    fun getFormFields(): List<FormField> {
        return mutableListOf<FormField>().apply {

            add(
                FormField(
                    label = "Ürün Adı",
                    key = "urunAdi",
                    type = FieldType.TEXT,
                    isRequired = true,
                    placeholder = "Örn: Halı, Koltuk, Perde",
                    icon = "inventory"
                )
            )

            add(
                FormField(
                    label = "Adet",
                    key = "adet",
                    type = FieldType.NUMBER,
                    value = "1",
                    placeholder = "1, 2, 3...",
                    minValue = 0.0,
                    step = 1.0,
                    icon = "numbers"
                )
            )

            add(
                FormField(
                    label = "Fiyat (₺)",
                    key = "fiyat",
                    type = FieldType.NUMBER,
                    value = "0",
                    placeholder = "0.00",
                    minValue = 0.0,
                    step = 0.01,
                    icon = "payments"
                )
            )

            add(
                FormField(
                    label = "En (cm)",
                    key = "en",
                    type = FieldType.NUMBER,
                    value = "0",
                    placeholder = "Örn: 120",
                    minValue = 0.0,
                    step = 1.0,
                    icon = "straighten"
                )
            )

            add(
                FormField(
                    label = "Boy (cm)",
                    key = "boy",
                    type = FieldType.NUMBER,
                    value = "0",
                    placeholder = "Örn: 180",
                    minValue = 0.0,
                    step = 1.0,
                    icon = "straighten"
                )
            )

            add(
                FormField(
                    label = "Metrekare",
                    key = "metrekare",
                    type = FieldType.NUMBER,
                    value = "0",
                    placeholder = "Örn: 2.16",
                    minValue = 0.0,
                    step = 0.01,
                    icon = "square_foot"
                )
            )

            add(
                FormField(
                    label = "Ek Not",
                    key = "ekNot",
                    type = FieldType.TEXTAREA,
                    placeholder = "Ürünle ilgili not...",
                    rows = 3,
                    icon = "notes"
                )
            )
        }
    }
}