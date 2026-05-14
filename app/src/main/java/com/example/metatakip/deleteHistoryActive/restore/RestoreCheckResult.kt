package com.example.metatakip.deleteHistoryActive.restore

sealed class RestoreCheckResult {
    object Allowed : RestoreCheckResult()
    data class Blocked(val reason: String) : RestoreCheckResult()
}
