package com.example.metatakip.deleteHistoryActive.restore

import android.content.Context
import dao.MetaTakipCustomerDao
import dao.MetaTakipUrunDao
import com.example.metatakip.feature.order.data.OrderDaoImpl

class RestoreRepositoryImpl(
    context: Context
) : RestoreRepository {

    private val customerDao = MetaTakipCustomerDao(context)
    private val siparisDao = OrderDaoImpl(context)
    private val urunDao = MetaTakipUrunDao(context)

    override fun restoreCustomer(customerId: Long) {
        customerDao.restoreCustomerCascade(customerId)
    }

    override fun restoreOrdersByCustomer(customerId: Long) {
        // cascade içinde
    }

    override fun restoreProductsByCustomer(customerId: Long) {
        // cascade içinde
    }

    override fun restoreOrder(orderId: Long) {
        siparisDao.restoreSiparis(orderId)
    }

    override fun restoreProductsByOrder(orderId: Long) {
        urunDao.restoreUrunlerBySiparisId(orderId)
    }

    override fun restoreProduct(productId: Long) {
        urunDao.restoreUrun(productId)
    }
}
