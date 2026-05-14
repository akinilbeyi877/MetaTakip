package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel

import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode

data class DeleteHistoryResult(
    val deletedCustomers: List<CustomerNode>,
    val activeCustomers: List<CustomerNode>
)