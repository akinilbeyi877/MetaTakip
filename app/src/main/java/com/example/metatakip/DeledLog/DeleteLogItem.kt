package com.example.metatakip.DeledLog
data class DeleteLogItem(
    val entityType: String,   // "musteri" | "siparis"
    val entityId: Long,
    val deletedBy: Long,
    val deletedAt: Long,
    val reason: String?
)
