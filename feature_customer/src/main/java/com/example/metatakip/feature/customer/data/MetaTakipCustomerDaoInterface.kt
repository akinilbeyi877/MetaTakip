package com.example.metatakip.feature.customer.data

import com.example.metatakip.feature_data.entityModel.Customer

interface MetaTakipCustomerDaoInterface {

    // READ
    fun getAllCustomers(): List<Customer>
    fun getAllCustomersIncludingDeleted(): List<Customer>
    fun getCustomerById(id: Long): Customer?
    fun getCustomersWithFirmaInfo(): List<Customer>
    fun getCustomersByFirmaId(firmaId: Long): List<Customer>
    fun searchCustomersByFirmaName(searchTerm: String): List<Customer>

    // CREATE
    fun addCustomer(customer: Customer): Boolean
    fun addCustomerAndReturnId(customer: Customer): Long

    // UPDATE
    fun updateCustomerById(id: Long, customer: Customer): Boolean
    fun updateCustomerFirmaId(customerId: Long, firmaId: Long?): Boolean

    fun updateCustomerLocationFull(
        customerId: Long,
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        address: String?
    ): Boolean

    // DELETE / RESTORE
    fun deleteCustomerCascade(customerId: Long): Boolean
    fun restoreCustomerCascade(customerId: Long): Boolean

    // PHONE SEARCH
    fun findCustomerByNormalizedPhone(phone: String): Customer?
    fun findCustomersByAnyPhone(phone: String): List<Customer>
    fun findFirmaForCustomer(phone: String): String?

    // STATS
    fun getDeletedCustomerCount(): Int
    fun getActiveCustomersWithDeletedOrdersCount(): Int
    fun getCustomerStatsByFirma(): Map<String, Int>
}