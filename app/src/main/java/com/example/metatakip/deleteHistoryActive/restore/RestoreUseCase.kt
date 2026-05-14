package com.example.metatakip.deleteHistoryActive.restore

import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.OrderNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.ProductNode

class RestoreUseCase(
    private val repository: RestoreRepository,
    private val guard: RestoreGuard
) {

    // ===============================
    // 🔄 MÜŞTERİ RESTORE (CASCADE)
    // ===============================
    fun restoreCustomer(customer: CustomerNode) {

        val check = guard.canRestoreCustomer(customer)
        if (check is RestoreCheckResult.Blocked) {
            throw IllegalStateException(check.reason)
        }

        // 🔑 String → Long dönüşümü
        val customerId = customer.customerId.toLong()

        // 1️⃣ müşteri
        repository.restoreCustomer(customerId)

        // 2️⃣ bağlı silinmiş siparişler
        repository.restoreOrdersByCustomer(customerId)

        // 3️⃣ bağlı silinmiş ürünler
        repository.restoreProductsByCustomer(customerId)
    }

    // ===============================
    // 🔄 SİPARİŞ RESTORE
    // ===============================
    fun restoreOrder(
        order: OrderNode,
        customer: CustomerNode
    ) {

        val check = guard.canRestoreOrder(order, customer)
        if (check is RestoreCheckResult.Blocked) {
            throw IllegalStateException(check.reason)
        }

        // orderId zaten Long ✅
        repository.restoreOrder(order.orderId)

        // alt ürünler
        repository.restoreProductsByOrder(order.orderId)
    }

    // ===============================
    // 🔄 ÜRÜN RESTORE
    // ===============================
    fun restoreProduct(
        product: ProductNode,
        order: OrderNode
    ) {

        val check = guard.canRestoreProduct(product, order)
        if (check is RestoreCheckResult.Blocked) {
            throw IllegalStateException(check.reason)
        }

        // productId zaten Long ✅
        repository.restoreProduct(product.productId)
    }
}
