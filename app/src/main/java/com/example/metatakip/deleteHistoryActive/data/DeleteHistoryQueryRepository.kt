package com.example.metatakip.deleteHistoryActive.data

import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.DeleteHistoryResult

interface DeleteHistoryQueryRepository {
    fun getDeleteHistory(): DeleteHistoryResult
}