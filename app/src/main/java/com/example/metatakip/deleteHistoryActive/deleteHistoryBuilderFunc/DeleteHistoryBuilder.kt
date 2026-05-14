package com.example.metatakip.deleteHistoryActive.deleteHistoryBuilderFunc

import android.util.Log
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.CustomerStatusTypeEnumUI
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.OrderDecisionEnum
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.OrderStatusType
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum.ProductStatusTypeEnumUI
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.DeleteHistoryResult
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel.DbCustomerDto
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel.DbOrderDto
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveDbDtoModel.DbProductDto
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.OrderNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.ProductNode

class DeleteHistoryBuilder {

    fun build(
        customers: List<DbCustomerDto>,
        orders: List<DbOrderDto>,
        products: List<DbProductDto>
    ): DeleteHistoryResult {

        val deletedCustomers = mutableListOf<CustomerNode>()
        val activeCustomersWithIssues = mutableListOf<CustomerNode>()

        customers.forEach { customer ->

            val customerOrders = orders.filter { it.customerId == customer.id }

            // 🔴 DEBUG: Müşteriye ait siparişleri kontrol et
            customerOrders.forEach { order ->
                Log.e("BUILDER_CUSTOMER_ORDERS",
                    "Customer: ${customer.name}, " +
                            "Order ID: ${order.id}, " +
                            "Order Number: '${order.orderNumber}', " +
                            "Order Status: '${order.orderStatus}', " +
                            "isDeleted: ${order.isDeleted}")
            }

            val (customerIcon, customerColor) =
                getCustomerIconAndColor(customer, customerOrders, products)

            val orderCountText =
                if (customerOrders.isNotEmpty()) "${customerOrders.size} sipariş"
                else "sipariş yok"

            // =====================================================
            // Silinmiş müşteri
            // =====================================================
            if (customer.isDeleted) {
                deletedCustomers.add(
                    CustomerNode(
                        customerId = customer.id.toString(),
                        customerName = customer.name,
                        isDeleted = true,
                        status = CustomerStatusTypeEnumUI.DELETED,
                        statusLabel = "Müşteri Silinmiş",
                        canRestore = true,
                        orders = buildOrderNodes(
                            customerOrders,
                            products,
                            isCustomerDeleted = true
                        ),
                        iconResId = customerIcon,
                        colorResId = customerColor,
                        itemCount = orderCountText
                    )
                )
                return@forEach
            }

            // =====================================================
            // Aktif müşteri ama silinmiş içerik var mı?
            // =====================================================
            val orderIds = customerOrders.map { it.id }
            val hasDeletedOrder = customerOrders.any { it.isDeleted }
            val hasDeletedProduct =
                products.any { it.isDeleted && it.orderId in orderIds }

            if (!hasDeletedOrder && !hasDeletedProduct) return@forEach

            activeCustomersWithIssues.add(
                CustomerNode(
                    customerId = customer.id.toString(),
                    customerName = customer.name,
                    isDeleted = false,
                    status = CustomerStatusTypeEnumUI.ACTIVE_WITH_DELETED_ORDERS,
                    statusLabel = "Aktif – silinmiş içerik var",
                    canRestore = false,
                    orders = buildOrderNodes(
                        customerOrders,
                        products,
                        isCustomerDeleted = false
                    ),
                    iconResId = customerIcon,
                    colorResId = customerColor,
                    itemCount = orderCountText
                )
            )
        }

        return DeleteHistoryResult(
            deletedCustomers = deletedCustomers,
            activeCustomers = activeCustomersWithIssues
        )
    }

    private fun buildOrderNodes(
        orders: List<DbOrderDto>,
        products: List<DbProductDto>,
        isCustomerDeleted: Boolean
    ): List<OrderNode> {

        val nodes = mutableListOf<OrderNode>()

        orders.forEach { order ->
            // 🔴 DEBUG: DbOrderDto'dan gelen veriler
            Log.e("BUILD_ORDER_DEBUG",
                "Order ID: ${order.id}, " +
                        "Original orderNumber: '${order.orderNumber}', " +
                        "orderStatus: '${order.orderStatus}', " +  // ✅ Burada görünüyor
                        "isDeleted: ${order.isDeleted}")

            val orderProducts = products.filter { it.orderId == order.id }

            val actualOrderNumber = order.orderNumber ?: "SIP-${order.id}"

            Log.e("ORDER_NUMBER_FINAL",
                "Final orderNumber: '$actualOrderNumber'")

            val decision = when {
                order.isDeleted -> OrderDecisionEnum.Deleted
                isCustomerDeleted -> OrderDecisionEnum.ActiveButCustomerDeleted
                orderProducts.any { it.isDeleted } ->
                    OrderDecisionEnum.ActiveWithDeletedProducts
                else -> OrderDecisionEnum.Ignore
            }

            if (decision == OrderDecisionEnum.Ignore) return@forEach

            val deletedProductCount = orderProducts.count { it.isDeleted }
            val activeProductCount = orderProducts.count { !it.isDeleted }
            val productCountText =
                if (deletedProductCount > 0)
                    "$deletedProductCount silinmiş ürün"
                else
                    "${orderProducts.size} ürün"

            val (orderIcon, orderColor) = getOrderIconAndColor(decision)

            val productNodes = orderProducts.map { product ->
                val (productIcon, productColor) =
                    getProductIconAndColor(product.isDeleted)

                ProductNode(
                    productId = product.id,
                    productName = product.name,
                    isDeleted = product.isDeleted,
                    status = if (product.isDeleted)
                        ProductStatusTypeEnumUI.DELETED
                    else
                        ProductStatusTypeEnumUI.ACTIVE,
                    canRestore = product.isDeleted,
                    iconResId = productIcon,
                    colorResId = productColor
                )
            }

            // Ürün durumunu belirle
            val productAggregateStatus = when {
                orderProducts.isEmpty() -> OrderNode.ProductAggregateStatus.ACTIVE
                orderProducts.all { it.isDeleted } -> OrderNode.ProductAggregateStatus.ALL_DELETED
                orderProducts.any { it.isDeleted } && orderProducts.any { !it.isDeleted } ->
                    OrderNode.ProductAggregateStatus.MIXED
                else -> OrderNode.ProductAggregateStatus.ACTIVE
            }

            // Teslim durumunu orderStatus'tan kontrol et
            val isDelivered = order.orderStatus?.contains("Teslim", ignoreCase = true) == true

            Log.e("ORDER_FINAL_DETAILS",
                "Order: $actualOrderNumber, " +
                        "Status: ${order.orderStatus}, " +  // ✅ Burada görünüyor
                        "isDelivered: $isDelivered, " +
                        "Product Status: $productAggregateStatus")

            // ✅✅✅ BURAYI DÜZELTİN: OrderNode constructor'ına orderStatus ekleyin
            nodes.add(
                OrderNode(
                    orderId = order.id,
                    orderNumber = actualOrderNumber,
                    isDeleted = order.isDeleted,
                    status = when (decision) {
                        OrderDecisionEnum.Deleted ->
                            OrderStatusType.Deleted
                        OrderDecisionEnum.ActiveButCustomerDeleted ->
                            OrderStatusType.ActiveButCustomerDeleted
                        OrderDecisionEnum.ActiveWithDeletedProducts ->
                            OrderStatusType.ActiveWithDeletedProducts
                        else -> error("Impossible")
                    },
                    // ✅✅✅ BU SATIRI EKLEYİN: orderStatus'u OrderNode'a gönder
                    orderStatus = order.orderStatus,  // ⬅️ BU ÇOK ÖNEMLİ!
                    warningText = when (decision) {
                        OrderDecisionEnum.Deleted -> "Silinmiş Sipariş"
                        OrderDecisionEnum.ActiveButCustomerDeleted ->
                            "Müşteri silinmiş ama sipariş aktif"
                        OrderDecisionEnum.ActiveWithDeletedProducts ->
                            "Bu siparişte silinmiş ürün var"
                        else -> null
                    },
                    canRestore = decision == OrderDecisionEnum.Deleted,
                    products = productNodes,
                    iconResId = orderIcon,
                    colorResId = orderColor,
                    itemCount = productCountText,
                    // ✅ YENİ ALANLAR
                    isDelivered = isDelivered,
                    productAggregateStatus = productAggregateStatus
                )
            )
        }

        return nodes
    }

    private fun getCustomerIconAndColor(
        customer: DbCustomerDto,
        orders: List<DbOrderDto>,
        products: List<DbProductDto>
    ): Pair<Int, Int> {

        val orderIds = orders.map { it.id }
        val hasDeletedProduct =
            products.any { it.isDeleted && it.orderId in orderIds }

        return when {
            customer.isDeleted ->
                R.drawable.ic_customer_24 to R.color.status_deleted
            orders.any { it.isDeleted } || hasDeletedProduct ->
                R.drawable.ic_customer_24 to R.color.status_warning
            else ->
                R.drawable.ic_customer_24 to R.color.status_active
        }
    }

    private fun getOrderIconAndColor(
        decision: OrderDecisionEnum
    ): Pair<Int, Int> =
        when (decision) {
            OrderDecisionEnum.Deleted ->
                R.drawable.ic_order_24 to R.color.status_deleted
            OrderDecisionEnum.ActiveButCustomerDeleted,
            OrderDecisionEnum.ActiveWithDeletedProducts ->
                R.drawable.ic_order_24 to R.color.status_warning
            OrderDecisionEnum.Ignore ->
                R.drawable.ic_order_24 to R.color.status_active
        }

    private fun getProductIconAndColor(isDeleted: Boolean): Pair<Int, Int> =
        if (isDeleted)
            R.drawable.ic_product_24 to R.color.status_deleted
        else
            R.drawable.ic_product_24 to R.color.status_active
}