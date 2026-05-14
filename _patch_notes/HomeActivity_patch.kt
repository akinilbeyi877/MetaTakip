// ============================================================
// HomeActivity PATCH v23 — observeSyncStatus + showSyncDetailDialog
// ============================================================
//
// UYGULAMA TALİMATI:
//   1. HomeActivity sınıfına iki yeni class değişkeni ekleyin (aşağıda işaretli yer)
//   2. Mevcut observeSyncStatus() metodunu aşağıdaki ile DEĞİŞTİRİN
//   3. showSyncDetailDialog() metodunu sınıf içine EKLEYIN
//   4. Gerekli import'ları ekleyin (aşağıda listeli)
//
// IMPORT'LAR (HomeActivity.kt başına ekleyin — yoksa):
//   import com.example.metatakip.feature_backup.util.BackupPreferences
//   import kotlinx.coroutines.flow.collectLatest
// ============================================================

// ── ADIM 1: Sınıf içine değişken tanımları ekleyin ──────────
// HomeActivity sınıfının başına (örn. layoutSyncBanner tanımı yanına) ekleyin:
//
//   private var lastSyncMsg: String = ""
//   private var lastSyncIsBusy: Boolean = false

// ── ADIM 2: Bu metodu observeSyncStatus() ile DEĞİŞTİRİN ─────

/*
    private fun observeSyncStatus() {
        lifecycleScope.launch {
            SyncStatusStore.state.collectLatest { state ->
                runOnUiThread {
                    // Durumu kaydet — dialog açıldığında okuyacağız
                    lastSyncMsg    = state.message
                    lastSyncIsBusy = state.isBusy

                    if (!state.isVisible) {
                        layoutSyncBanner.visibility = View.GONE
                        return@runOnUiThread
                    }

                    layoutSyncBanner.visibility = View.VISIBLE

                    val pos = state.queuePosition
                    val text = buildString {
                        append(state.message)
                        if (pos != null && pos > 0) append(" (Sıra: $pos)")
                        append("  ›")   // tıklanabilir olduğunu göster
                    }
                    tvSyncBanner.text = text

                    layoutSyncBanner.setBackgroundColor(
                        when {
                            state.message.contains("❌") || state.message.contains("başarısız") ->
                                android.graphics.Color.parseColor("#FFCDD2")   // kırmızı — hata
                            state.isBusy ->
                                android.graphics.Color.parseColor("#FFF3CD")   // sarı — işlem var
                            else ->
                                android.graphics.Color.parseColor("#D1FAE5")   // yeşil — tamam
                        }
                    )
                }
            }
        }

        // Banner'a tıklayınca detay dialogu göster
        layoutSyncBanner.setOnClickListener { showSyncDetailDialog() }
    }
*/

// ── ADIM 3: Bu yeni metodu sınıf içine EKLEYIN ────────────────
// observeSyncStatus() metodunun hemen altına ekleyin.

/*
    private fun showSyncDetailDialog() {
        val BP = com.example.metatakip.feature_backup.util.BackupPreferences

        val email        = BP.getDriveEmail() ?: "—"
        val folder       = BP.getBackupFolderName().ifBlank { "—" }
        val device       = BP.getDeviceName().ifBlank { "—" }
        val lastSyncDev  = BP.getLastSyncDevice().ifBlank { "—" }
        val connected    = BP.isDriveConnected()
        val autoOn       = BP.isAutoBackupEnabled()
        val lastBackupMs = BP.getLastBackupTime()
        val lastBackupStr = if (lastBackupMs > 0)
            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastBackupMs))
        else "—"
        val times = listOf(
            BP.getAutoBackupTime1(), BP.getAutoBackupTime2(), BP.getAutoBackupTime3()
        ).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" }

        // Durum ikonu
        val durumSatır = when {
            lastSyncMsg.contains("❌") || lastSyncMsg.contains("başarısız") ->
                "🔴 HATA — $lastSyncMsg"
            lastSyncMsg.contains("✅") || lastSyncMsg.contains("Tamam") ->
                "🟢 TAMAM — $lastSyncMsg"
            lastSyncMsg.contains("📥") || lastSyncMsg.contains("alın") ->
                "🟡 İŞLEM VAR — $lastSyncMsg"
            lastSyncMsg.isNotBlank() -> "⚪ $lastSyncMsg"
            else -> "⚪ Bekleniyor"
        }

        val msg = buildString {
            // ─── Şu Anki Durum ───
            appendLine("┌─ ŞU ANKİ DURUM ─────────────────")
            appendLine("│  $durumSatır")
            appendLine("│")
            appendLine("│  📲 Gönderen Cihaz : $lastSyncDev")
            appendLine("│  📧 Gmail           : $email")
            appendLine("│  📁 Drive Klasörü  : $folder")
            appendLine("└──────────────────────────────────")
            appendLine()
            // ─── Bu Cihaz ───
            appendLine("┌─ BU CİHAZ ──────────────────────")
            appendLine("│  📱 Cihaz Adı      : $device")
            appendLine("│  ☁️  Drive Bağlantı : ${if (connected) "✅ BAĞLI" else "❌ BAĞLI DEĞİL"}")
            appendLine("│  🕐 Son Yedek      : $lastBackupStr")
            appendLine("│  ⏰ Oto. Yedek     : ${if (autoOn) "AÇIK ($times)" else "KAPALI"}")
            appendLine("└──────────────────────────────────")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔍 Senkronizasyon Detayı")
            .setMessage(msg)
            .setPositiveButton("Tamam", null)
            .show()
    }
*/
