package com.example.metatakip.feature.customer.ui


import com.example.metatakip.feature_data.entityModel.Firma
import com.example.metatakip.feature_data.ui.GenericListRowAdapterUiModel

object FirmaListUiMapper {

    fun map(item: Any): GenericListRowAdapterUiModel {
        val firma = item as Firma

        val subtitleParts = listOfNotNull(
            firma.telefon?.takeIf { it.isNotBlank() },
            firma.vergiNo?.takeIf { it.isNotBlank() }
        )

        return GenericListRowAdapterUiModel(
            id = firma.id,
            title = firma.firmaAdi ?: "İsimsiz Firma",
            subtitle = subtitleParts.joinToString(" • ").ifBlank { null },
            payload = firma
        )
    }
}