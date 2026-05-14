package com.example.metatakip.feature_backup.data

data class ImportRowResult(
    val rowIndex: Int,
    val status: Status,
    val message: String
) {
    enum class Status {
        OK,
        WARNING,
        ERROR
    }
}