package com.example.metatakip.feature.admin.builders

import android.content.Context
import com.example.metatakip.feature.admin.data.MesajSablonDaoImpl
import java.util.Locale

class FeatureAdminListProvider(private val context: Context) {

    fun canHandle(table: String): Boolean {
        return when (table.lowercase(Locale.ROOT)) {
            "mesaj_sablon",
            "admin_firma" -> true
            else -> false
        }
    }

    fun load(table: String): MutableList<Any> {
        return when (table.lowercase(Locale.ROOT)) {

            "mesaj_sablon" -> MesajSablonDaoImpl(context)
                .getAll()
                .map { it as Any }
                .toMutableList()

            // "admin_firma" -> AdminFirmaDaoImpl(context)
            //     .getAll()
            //     .map { it as Any }
            //     .toMutableList()

            else -> mutableListOf()
        }
    }
}