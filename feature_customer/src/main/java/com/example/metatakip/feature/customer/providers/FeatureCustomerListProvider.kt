package com.example.metatakip.feature.customer.providers


import android.content.Context
import com.example.metatakip.feature.customer.data.MetaTakipCustomerDaoImpl
import java.util.Locale

class FeatureCustomerListProvider(private val context: Context) {

    fun canHandle(listType: String): Boolean {
        val t = listType.lowercase(Locale.ROOT)
        return t == "customer" ||
                t == "customers" ||
                t == "musteri" ||
                t == "musteriler" ||
                t == "musteri_silinen" ||
                t == "deleted_customers"
    }

    fun load(includeDeleted: Boolean = false): MutableList<Any> {
        val dao = MetaTakipCustomerDaoImpl(context)

        val list = if (includeDeleted) {
            dao.getAllCustomersIncludingDeleted()
        } else {
            dao.getAllCustomers()
        }

        return list.map { it as Any }.toMutableList()
    }
}