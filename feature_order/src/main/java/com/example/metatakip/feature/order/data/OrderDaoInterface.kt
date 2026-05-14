package com.example.metatakip.feature.order.data

import com.example.metatakip.feature_data.entityModel.Order

interface OrderDaoInterface {

    fun addOrder(order: Order): Long

    fun updateOrderById(id: Long, order: Order): Boolean

    fun updateOrderDurumu(
        orderId: Long,
        yeniDurum: String,
        teslimTarihi: String
    ): Boolean

    fun getOrderById(id: Long): Order?

    fun softDeleteOrder(id: Long): Boolean

    fun restoreOrder(id: Long): Boolean

    fun getAllOrder(): List<Order>

    fun getDeletedOrder(): List<Order>

    fun getDeletedOrderCount(): Int

    fun getAllOrderIncludingDeleted(): List<Order>

    fun getOrdersByMusteriUuid(uuid: String, showAll: Boolean = false): List<Order>
    fun getOrdersByMusteriAdiVeTel(adi: String, tel: String, showAll: Boolean = false): List<Order>
    fun getOrdersByTelefon(tel: String, showAll: Boolean = false): List<Order>
    fun getOrdersByMusteriAdi(adi: String, showAll: Boolean = false): List<Order>
    fun getOrdersByDurum(durum: String): List<Order>

    fun updateOrderPhoto(orderId: Long, photoPath: String): Boolean
}