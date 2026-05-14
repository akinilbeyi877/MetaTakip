package com.example.metatakip.deleteHistoryActive.ui.deleteHistoryFragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.OrderNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.ProductNode
import com.example.metatakip.deleteHistoryActive.ui.UiRow
import com.example.metatakip.deleteHistoryActive.ui.viewholder.CustomerViewHolder
import com.example.metatakip.deleteHistoryActive.ui.viewholder.HeaderViewHolder
import com.example.metatakip.deleteHistoryActive.ui.viewholder.OrderViewHolder
import com.example.metatakip.deleteHistoryActive.ui.viewholder.ProductViewHolder
import java.util.concurrent.atomic.AtomicBoolean

class DeleteHistoryAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // =====================================================
    // DATA
    // =====================================================

    private val items = mutableListOf<UiRow>()

    private var deletedCustomers: List<CustomerNode> = emptyList()
    private var activeCustomers: List<CustomerNode> = emptyList()

    private var allDeletedCustomers: List<CustomerNode> = emptyList()
    private var allActiveCustomers: List<CustomerNode> = emptyList()

    private val headerState = mutableMapOf(
        UiRow.BlockType.DELETED_CUSTOMERS to true,
        UiRow.BlockType.ACTIVE_CUSTOMERS_WITH_ISSUES to true
    )

    private val isRestoring = AtomicBoolean(false)
    private var restoringItemId: String? = null

    // =====================================================
    // CALLBACKS
    // =====================================================

    var onRestoreCustomer: ((CustomerNode) -> Unit)? = null
    var onRestoreOrder: ((OrderNode, CustomerNode) -> Unit)? = null
    var onRestoreProduct: ((ProductNode, OrderNode) -> Unit)? = null

    // =====================================================
    // RESTORE STATE
    // =====================================================

    fun setRestoring(restoring: Boolean, itemId: String? = null) {
        isRestoring.set(restoring)
        restoringItemId = itemId
        if (!restoring) notifyDataSetChanged()
    }

    private fun isItemRestoring(itemId: String?): Boolean =
        isRestoring.get() && restoringItemId == itemId

    // =====================================================
    // ADAPTER CORE
    // =====================================================

    override fun getItemCount(): Int = items.size
    override fun getItemViewType(position: Int): Int = items[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            UiRow.TYPE_HEADER ->
                HeaderViewHolder(inflater.inflate(R.layout.row_header, parent, false))
            UiRow.TYPE_CUSTOMER ->
                CustomerViewHolder(inflater.inflate(R.layout.row_customer, parent, false))
            UiRow.TYPE_ORDER ->
                OrderViewHolder(inflater.inflate(R.layout.row_order, parent, false))
            UiRow.TYPE_PRODUCT ->
                ProductViewHolder(inflater.inflate(R.layout.row_product, parent, false))
            else -> error("Unknown viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {

            is UiRow.Header ->
                (holder as HeaderViewHolder).bind(item) {
                    if (!isRestoring.get()) onHeaderClicked(it)
                }

            is UiRow.Customer -> {
                val restoringThis = isItemRestoring(item.customer.customerId)
                (holder as CustomerViewHolder).bind(
                    customer = item.customer,
                    onCustomerClick = ::onCustomerClicked,
                    onRestoreClick = {
                        if (!isRestoring.get() && !restoringThis) {
                            setRestoring(true, it.customerId)
                            onRestoreCustomer?.invoke(it)
                        }
                    },
                    isRestoring = restoringThis
                )
            }

            is UiRow.Order -> {
                val customer = findCustomerOfOrder(item.order)
                val restoringThis = isItemRestoring(item.order.orderId.toString())
                (holder as OrderViewHolder).bind(
                    uiRow = item,
                    onOrderClick = ::onOrderClicked,
                    onRestoreClick = {
                        if (!isRestoring.get() && !restoringThis && customer != null) {
                            setRestoring(true, it.orderId.toString())
                            onRestoreOrder?.invoke(it, customer)
                        }
                    }
                )
            }

            is UiRow.Product -> {
                val order = findOrderOfProduct(item.product)
                val restoringThis = isItemRestoring(item.product.productId.toString())
                (holder as ProductViewHolder).bind(
                    uiRow = item,
                    onRestoreClick = {
                        if (!isRestoring.get() && !restoringThis && order != null) {
                            setRestoring(true, it.productId.toString())
                            onRestoreProduct?.invoke(it, order)
                        }
                    }
                )
            }
        }
    }

    // =====================================================
    // PUBLIC DATA
    // =====================================================

    fun submitData(deleted: List<CustomerNode>, active: List<CustomerNode>) {
        allDeletedCustomers = deleted
        allActiveCustomers = active
        deletedCustomers = deleted
        activeCustomers = active
        rebuildItems()
    }

    // =====================================================
    // 🔍 SEARCH – %100 ÇALIŞAN HAL
    // =====================================================

    fun filter(query: String?) {

        // 🔁 Temizle
        if (query.isNullOrBlank()) {
            deletedCustomers = allDeletedCustomers
            activeCustomers = allActiveCustomers

            headerState.keys.forEach { headerState[it] = true }
            resetExpandState()

            rebuildItems()
            return
        }

        val q = query.lowercase()

        headerState.keys.forEach { headerState[it] = true }

        fun productMatch(product: ProductNode): Boolean =
            product.productName.lowercase().contains(q)

        fun orderMatch(order: OrderNode): Boolean =
            order.orderNumber.lowercase().contains(q) ||
                    order.products.any { productMatch(it) }

        fun customerMatch(customer: CustomerNode): Boolean =
            customer.customerName.lowercase().contains(q) ||
                    customer.phone?.contains(q) == true ||
                    customer.orders.any { orderMatch(it) }

        deletedCustomers = allDeletedCustomers
            .filter { customerMatch(it) }
            .onEach { it.expandForSearch(q) }

        activeCustomers = allActiveCustomers
            .filter { customerMatch(it) }
            .onEach { it.expandForSearch(q) }

        rebuildItems()
    }

    private fun CustomerNode.expandForSearch(q: String) {
        isExpanded = true
        orders.forEach { order ->
            val match =
                order.orderNumber.lowercase().contains(q) ||
                        order.products.any { it.productName.lowercase().contains(q) }

            order.isExpanded = match
        }
    }

    private fun resetExpandState() {
        (allDeletedCustomers + allActiveCustomers).forEach { customer ->
            customer.isExpanded = false
            customer.orders.forEach { it.isExpanded = false }
        }
    }

    // =====================================================
    // CLICK HANDLERS
    // =====================================================

    private fun onHeaderClicked(header: UiRow.Header) {
        headerState[header.blockType] =
            !(headerState[header.blockType] ?: true)
        rebuildItems()
    }

    private fun onCustomerClicked(customer: CustomerNode) {
        if (isRestoring.get()) return

        (deletedCustomers + activeCustomers).forEach {
            it.isExpanded = it.customerId == customer.customerId && !customer.isExpanded
            it.orders.forEach { o -> o.isExpanded = false }
        }

        rebuildItems()
    }

    private fun onOrderClicked(order: OrderNode) {
        if (isRestoring.get()) return

        (deletedCustomers + activeCustomers)
            .flatMap { it.orders }
            .forEach {
                it.isExpanded = it.orderId == order.orderId && !order.isExpanded
            }

        rebuildItems()
    }

    // =====================================================
    // FINDERS
    // =====================================================

    private fun findCustomerOfOrder(order: OrderNode): CustomerNode? =
        (deletedCustomers + activeCustomers)
            .firstOrNull { it.orders.any { o -> o.orderId == order.orderId } }

    private fun findOrderOfProduct(product: ProductNode): OrderNode? =
        (deletedCustomers + activeCustomers)
            .flatMap { it.orders }
            .firstOrNull { it.products.any { p -> p.productId == product.productId } }

    // =====================================================
    // REBUILD
    // =====================================================
// =====================================================
// 🔙 BACK PRESS SUPPORT (ZORUNLU)
// =====================================================
    fun collapseExpandedItems(): Boolean {
        var collapsed = false

        // Header'ları kapat
        headerState.keys.forEach {
            if (headerState[it] == true) {
                headerState[it] = false
                collapsed = true
            }
        }

        // Customer + Order collapse
        (deletedCustomers + activeCustomers).forEach { customer ->
            if (customer.isExpanded) {
                customer.isExpanded = false
                collapsed = true
            }

            customer.orders.forEach { order ->
                if (order.isExpanded) {
                    order.isExpanded = false
                    collapsed = true
                }
            }
        }

        if (collapsed) {
            rebuildItems()
        }

        return collapsed
    }

    private fun rebuildItems() {
        items.clear()

        fun build(customers: List<CustomerNode>, header: UiRow.Header) {
            items.add(header)
            if (!header.isExpanded) return

            customers.forEach { customer ->
                items.add(UiRow.Customer(customer))
                if (customer.isExpanded) {
                    customer.orders.forEachIndexed { oi, order ->
                        items.add(UiRow.Order(order, oi == customer.orders.lastIndex))
                        if (order.isExpanded) {
                            order.products.forEachIndexed { pi, product ->
                                items.add(
                                    UiRow.Product(product, pi == order.products.lastIndex)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (deletedCustomers.isNotEmpty()) {
            build(
                deletedCustomers,
                UiRow.Header(
                    "🗑️ Silinmiş Müşteriler",
                    deletedCustomers.size,
                    UiRow.BlockType.DELETED_CUSTOMERS,
                    headerState[UiRow.BlockType.DELETED_CUSTOMERS] ?: true
                )
            )
        }


        if (activeCustomers.isNotEmpty()) {
            build(
                activeCustomers,
                UiRow.Header(
                    "👥 Aktif Müşterilerde Silinenler",
                    activeCustomers.size,
                    UiRow.BlockType.ACTIVE_CUSTOMERS_WITH_ISSUES,
                    headerState[UiRow.BlockType.ACTIVE_CUSTOMERS_WITH_ISSUES] ?: true
                )
            )
        }

        notifyDataSetChanged()
    }
}
