package com.example.metatakip.deleteHistoryActive.deleteHistoryFragment

// 🔹 Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// 🔹 DOĞRU ViewModel importu (EN KRİTİK SATIR)

// 🔹 Repository & UseCase
import com.example.metatakip.deleteHistoryActive.data.DeleteHistoryQueryRepository
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.ui.DeleteHistoryViewModel
import com.example.metatakip.deleteHistoryActive.restore.RestoreUseCase

class DeleteHistoryViewModelFactory(
    private val deleteHistoryQueryRepository: DeleteHistoryQueryRepository,
    private val restoreUseCase: RestoreUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeleteHistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeleteHistoryViewModel(
                deleteHistoryQueryRepository,
                restoreUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
