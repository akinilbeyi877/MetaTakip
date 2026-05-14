package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.metatakip.deleteHistoryActive.data.DeleteHistoryQueryRepository
import com.example.metatakip.deleteHistoryActive.restore.RestoreUseCase

class DeleteHistoryViewModelFactory(
    private val queryRepository: DeleteHistoryQueryRepository,
    private val restoreUseCase: RestoreUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeleteHistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeleteHistoryViewModel(
                queryRepository,
                restoreUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
