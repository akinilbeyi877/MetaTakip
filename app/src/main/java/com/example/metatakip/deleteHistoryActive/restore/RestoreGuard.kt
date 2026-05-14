package com.example.metatakip.deleteHistoryActive.restore

import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.OrderNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.ProductNode

class RestoreGuard {

    fun canRestoreCustomer(
        customer: CustomerNode
    ): RestoreCheckResult {

        if (!customer.isDeleted) {
            return RestoreCheckResult.Blocked("Müşteri zaten aktif")
        }

        return RestoreCheckResult.Allowed
    }


    fun canRestoreProduct(
        product: ProductNode,
        order: OrderNode
    ): RestoreCheckResult {

        if (!product.canRestore) {
            return RestoreCheckResult.Blocked("Ürün zaten aktif")
        }

        if (order.isDeleted) {
            return RestoreCheckResult.Blocked("Önce siparişi geri alın")
        }

        return RestoreCheckResult.Allowed
    }

    fun canRestoreOrder(
        order: OrderNode,
        customer: CustomerNode
    ): RestoreCheckResult {

        if (!order.canRestore) {
            return RestoreCheckResult.Blocked("Sipariş zaten aktif")
        }

        if (customer.isDeleted) {
            return RestoreCheckResult.Blocked("Önce müşteri geri alınmalı")
        }

        return RestoreCheckResult.Allowed
    }
}
