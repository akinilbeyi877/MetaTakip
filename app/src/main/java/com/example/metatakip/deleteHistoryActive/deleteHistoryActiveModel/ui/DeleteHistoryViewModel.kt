package com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metatakip.deleteHistoryActive.data.DeleteHistoryQueryRepository
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.DeleteHistoryResult
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.CustomerNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.OrderNode
import com.example.metatakip.deleteHistoryActive.deleteHistoryActiveModel.deleteHistoryActiveNode.ProductNode
import com.example.metatakip.deleteHistoryActive.restore.RestoreUseCase
import kotlinx.coroutines.launch

class DeleteHistoryViewModel(
    private val queryRepository: DeleteHistoryQueryRepository,
    private val restoreUseCase: RestoreUseCase
) : ViewModel() {

    private val _deleteHistoryResult = MutableLiveData<DeleteHistoryResult>()
    val deleteHistoryResult: LiveData<DeleteHistoryResult> = _deleteHistoryResult

    // Hata ve yükleme durumları için LiveData'lar
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    init {
        refresh()
    }

    // ===============================
    // REFRESH
    // ===============================
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _deleteHistoryResult.value = queryRepository.getDeleteHistory()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Veriler yüklenemedi: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===============================
    // 👤 CUSTOMER RESTORE
    // ===============================
    fun restoreCustomer(customer: CustomerNode) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                restoreUseCase.restoreCustomer(customer)
                _successMessage.value = "Müşteri başarıyla geri yüklendi"
                refresh() // Listeyi güncelle
            } catch (e: IllegalStateException) {
                // Müşteri zaten aktifse buraya düşer
                when {
                    e.message?.contains("zaten aktif") == true -> {
                        _errorMessage.value = "Bu müşteri zaten aktif durumda"
                        // Listeyi yenile (veriler güncellensin)
                        refresh()
                    }
                    else -> {
                        _errorMessage.value = "Geri yükleme hatası: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Beklenmeyen hata: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===============================
    // 📦 ORDER RESTORE
    // ===============================
    fun restoreOrder(order: OrderNode, customer: CustomerNode) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                restoreUseCase.restoreOrder(order, customer)
                _successMessage.value = "Sipariş başarıyla geri yüklendi"
                refresh()
            } catch (e: IllegalStateException) {
                when {
                    e.message?.contains("zaten aktif") == true -> {
                        _errorMessage.value = "Bu sipariş zaten aktif durumda"
                        refresh()
                    }
                    e.message?.contains("müşteri geri alınmalı") == true -> {
                        _errorMessage.value = "Önce müşteriyi geri yükleyin"
                    }
                    else -> {
                        _errorMessage.value = "Geri yükleme hatası: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Beklenmeyen hata: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===============================
    // 🧾 PRODUCT RESTORE
    // ===============================
    fun restoreProduct(product: ProductNode, order: OrderNode) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                restoreUseCase.restoreProduct(product, order)
                _successMessage.value = "Ürün başarıyla geri yüklendi"
                refresh()
            } catch (e: IllegalStateException) {
                when {
                    e.message?.contains("zaten aktif") == true -> {
                        _errorMessage.value = "Bu ürün zaten aktif durumda"
                        refresh()
                    }
                    e.message?.contains("siparişi geri alın") == true -> {
                        _errorMessage.value = "Önce siparişi geri yükleyin"
                    }
                    else -> {
                        _errorMessage.value = "Geri yükleme hatası: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Beklenmeyen hata: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Mesajları temizleme metodları
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }
}