package com.example.metatakip.deleteHistoryActive

interface DeleteHistoryRepository {

    fun restoreCustomer(customerId: Long)

    fun restoreOrdersByCustomer(customerId: Long)

    fun restoreProductsByCustomer(customerId: Long)

    fun restoreOrder(orderId: Long)

    fun restoreProductsByOrder(orderId: Long)

    fun restoreProduct(productId: Long)
}
