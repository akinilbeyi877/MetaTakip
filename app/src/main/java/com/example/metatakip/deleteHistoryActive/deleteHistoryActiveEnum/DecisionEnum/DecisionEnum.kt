// DecisionEnum.kt - TOP-LEVEL ENUM'LAR
package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveEnum

// Object'i KALDIRIN, direkt top-level enum'lar
enum class OrderStatusType {
    Deleted,
    ActiveButCustomerDeleted,
    ActiveWithDeletedProducts
}

enum class OrderDecisionEnum {
    Deleted,
    ActiveButCustomerDeleted,
    ActiveWithDeletedProducts,
    Ignore
}

enum class CustomerDecisionEnum {
    Deleted,
    ActiveWithIssues,
    ActiveClean
}

enum class ProductDecisionEnum {
    Deleted,
    Active,
    Ignore
}