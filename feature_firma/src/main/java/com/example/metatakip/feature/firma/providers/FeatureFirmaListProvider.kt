package com.example.metatakip.feature.firma.providers

import android.content.Context
import com.example.metatakip.feature.firma.data.MetaTakipFirmaDaoImpl
import java.util.Locale

class FeatureFirmaListProvider(private val context: Context) {

    fun canHandle(listType: String): Boolean {
        val t = listType.lowercase(Locale.ROOT)
        return t == "firma" || t == "company" || t == "companies"
    }

    fun load(): MutableList<Any> {
        return MetaTakipFirmaDaoImpl(context)
            .getAllFirmalar()
            .map { it as Any }
            .toMutableList()
    }
}