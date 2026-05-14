package com.example.metatakip.deleteHistoryActive.restore

interface RestoreRepository {

    // 👤 CUSTOMER
    fun restoreCustomer(customerId: Long)
    fun restoreOrdersByCustomer(customerId: Long)
    fun restoreProductsByCustomer(customerId: Long)

    // 📦 ORDER
    fun restoreOrder(orderId: Long)
    fun restoreProductsByOrder(orderId: Long)

    // 🧾 PRODUCT
    fun restoreProduct(productId: Long)
}
