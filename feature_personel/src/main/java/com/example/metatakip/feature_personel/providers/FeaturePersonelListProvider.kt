package com.example.metatakip.feature_personel.providers


import android.content.Context
import com.example.metatakip.feature_personel.data.MetaTakipPersonelDaoImpl
import java.util.Locale

class FeaturePersonelListProvider(
    private val context: Context
) {

    fun canHandle(table: String): Boolean {
        return table.lowercase(Locale.ROOT) == "personel"
    }

    fun load(): MutableList<Any> {
        return MetaTakipPersonelDaoImpl(context)
            .getAllPersonel()
            .map { it as Any }
            .toMutableList()
    }
}