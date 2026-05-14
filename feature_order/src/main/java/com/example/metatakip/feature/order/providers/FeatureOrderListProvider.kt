package com.example.metatakip.feature.order.providers

import android.content.Context
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature_data.entityModel.Order
import java.util.Locale

class FeatureOrderListProvider(private val context: Context) {

    fun canHandle(listType: String): Boolean {
        val t = normalize(listType)
        return isOrderListType(t) || isDeletedOrderListType(t)
    }

    /**
     * @param durumFilter  GenericListActivity'den gelir (örn: "Yeni Sipariş", "TÜMÜ")
     * @param includeDeleted true ise sadece silinenleri döndürür
     */
    fun load(
        durumFilter: String = "TÜMÜ",
        includeDeleted: Boolean = false
    ): MutableList<Any> {
        val dao = OrderDaoImpl(context)

        val orders: List<Order> = if (includeDeleted) {
            // ✅ silinen sipariş listesi
            dao.safeGetDeletedOrders()
        } else {
            // ✅ aktif sipariş listesi (durum filtreli)
            val all = dao.safeGetAllOrders()
            filterByDurum(all, durumFilter)
        }

        return orders.map { it as Any }.toMutableList()
    }

    private fun filterByDurum(all: List<Order>, durumFilter: String): List<Order> {
        val f = durumFilter.trim()
        return when {
            f.isBlank() -> all
            f.equals("TÜMÜ", ignoreCase = true) -> all
            f.equals("Yeni Sipariş", ignoreCase = true) ->
                all.filter { it.durum in listOf("Yeni Sipariş", "Tekrar İşleme Alındı") }
            else -> all.filter {
                val dbDurum = it.durum?.trim().orEmpty()
                dbDurum.equals(f, ignoreCase = true)
            }
        }
    }

    private fun isOrderListType(t: String): Boolean {
        return t == "siparis" ||
                t == "siparisler" ||
                t == "order" ||
                t == "orders"
    }

    private fun isDeletedOrderListType(t: String): Boolean {
        return t == "siparis_silinen" ||
                t == "siparisler_silinen" ||
                t == "deleted_orders" ||
                t == "orders_deleted"
    }

    private fun normalize(s: String): String {
        return s.trim()
            .lowercase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_")
    }

    /**
     * Projede isimler karışık olabiliyor:
     * - yeni interface: getAllOrder / getDeletedOrder
     * - eski: getAllSiparis / getDeletedSiparis
     *
     * Burada ikisini de destekleyip derleme riskini düşürüyoruz.
     */
    private fun OrderDaoImpl.safeGetAllOrders(): List<Order> {
        return try {
            // varsa yeni isim
            this.getAllOrder()
        } catch (_: Throwable) {
            // fallback eski isim
            this.getAllSiparis()
        }
    }

    private fun OrderDaoImpl.safeGetDeletedOrders(): List<Order> {
        return try {
            this.getDeletedOrder()
        } catch (_: Throwable) {
            this.getDeletedSiparis()
        }
    }
}