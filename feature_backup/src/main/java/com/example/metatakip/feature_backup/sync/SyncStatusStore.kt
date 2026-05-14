package com.example.metatakip.feature_backup.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 📊 Senkronizasyon UI Durum Modeli
 */
data class SyncUiState(
    val isVisible: Boolean = false,
    val isBusy: Boolean = false,
    val message: String = "",
    val queuePosition: Int? = null,
    val timestamp: Long = System.currentTimeMillis() // UI'ın güncelliğini takip etmek için
)

/**
 * 🏪 Senkronizasyon Durum Deposu
 * Arka plan işlemlerinin (BridgeManager, DriveManager) UI'a bilgi göndermesini sağlar.
 */
object SyncStatusStore {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    /**
     * İşlem devam ediyor (Loading) durumunu gösterir.
     */
    fun showBusy(message: String, queuePosition: Int? = null) {
        // 'update' kullanmak Thread-Safety (paralel işlemler) için '.value =' atamasından daha güvenlidir.
        _state.update {
            it.copy(
                isVisible = true,
                isBusy = true,
                message = message,
                queuePosition = queuePosition,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Bilgi mesajı gösterir (Tamamlandı, Hata oluştu vb.).
     */
    fun showInfo(message: String, queuePosition: Int? = null) {
        _state.update {
            it.copy(
                isVisible = true,
                isBusy = false, // Loading animasyonunu durdurur
                message = message,
                queuePosition = queuePosition,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Senkronizasyon panelini gizler.
     */
    fun hide() {
        _state.value = SyncUiState(isVisible = false, isBusy = false)
    }

    /**
     * Şu an aktif bir senkronizasyon işlemi olup olmadığını kontrol eder.
     */
    val isSyncing: Boolean
        get() = _state.value.isBusy

    /**
     * Mevcut mesajı döner.
     */
    val currentMessage: String
        get() = _state.value.message
}