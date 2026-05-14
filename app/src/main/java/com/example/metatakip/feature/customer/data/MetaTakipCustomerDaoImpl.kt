package com.example.metatakip.feature.customer.data

import android.content.Context
import com.example.metatakip.feature_data.entityModel.Customer
import dao.MetaTakipCustomerDao

class MetaTakipCustomerDaoImpl(
    context: Context
) : MetaTakipCustomerDaoInterface {

    private val dao = MetaTakipCustomerDao(context)

    // READ
    override fun getAllCustomers(): List<Customer> = dao.getAllCustomers()
    override fun getAllCustomersIncludingDeleted(): List<Customer> = dao.getAllCustomersIncludingDeleted()
    override fun getCustomerById(id: Long): Customer? = dao.getCustomerById(id)
    override fun getCustomersWithFirmaInfo(): List<Customer> = dao.getCustomersWithFirmaInfo()
    override fun getCustomersByFirmaId(firmaId: Long): List<Customer> = dao.getCustomersByFirmaId(firmaId)
    override fun searchCustomersByFirmaName(searchTerm: String): List<Customer> = dao.searchCustomersByFirmaName(searchTerm)

    // CREATE
    override fun addCustomer(customer: Customer): Boolean = dao.addCustomer(customer)
    override fun addCustomerAndReturnId(customer: Customer): Long = dao.addCustomerAndReturnId(customer)

    // UPDATE
    override fun updateCustomerById(id: Long, customer: Customer): Boolean = dao.updateCustomerById(id, customer)
    override fun updateCustomerFirmaId(customerId: Long, firmaId: Long?): Boolean = dao.updateCustomerFirmaId(customerId, firmaId)

    override fun updateCustomerLocationFull(
        customerId: Long,
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        address: String?
    ): Boolean = dao.updateCustomerLocationFull(customerId, latitude, longitude, timestamp, address)

    // DELETE / RESTORE
    override fun deleteCustomerCascade(customerId: Long): Boolean = dao.deleteCustomerCascade(customerId)
    override fun restoreCustomerCascade(customerId: Long): Boolean = dao.restoreCustomerCascade(customerId)

    // PHONE SEARCH
    override fun findCustomerByNormalizedPhone(phone: String): Customer? = dao.findCustomerByNormalizedPhone(phone)
    override fun findCustomersByAnyPhone(phone: String): List<Customer> = dao.findCustomersByAnyPhone(phone)
    override fun findFirmaForCustomer(phone: String): String? = dao.findFirmaForCustomer(phone)

    // STATS
    override fun getDeletedCustomerCount(): Int = dao.getDeletedCustomerCount()
    override fun getActiveCustomersWithDeletedOrdersCount(): Int = dao.getActiveCustomersWithDeletedOrdersCount()
    override fun getCustomerStatsByFirma(): Map<String, Int> = dao.getCustomerStatsByFirma()
}