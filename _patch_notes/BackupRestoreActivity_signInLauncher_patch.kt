// ============================================================
// BackupRestoreActivity.kt İÇİNDE YAPILACAK 2 KÜÇÜK DEĞİŞİKLİK
// Bu dosya tam dosya değil, sadece değişen iki bloğu gösteriyor.
// ============================================================

// ─────────────────────────────────────────────────────────────
// DEĞİŞİKLİK 1: signInLauncher (Drive BAĞLANINCA)
// Satır ~234 civarında bulun, aşağıdaki gibi değiştirin:
// ─────────────────────────────────────────────────────────────

private val signInLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            BackupPreferences.setDriveConnected(true)
            BackupPreferences.setDriveEmail(account.email)

            // 🔧 KRİTİK DÜZELTME:
            // Email kaydedildikten HEMEN SONRA Firebase dinleyicisini yeniden başlat.
            // Uygulama açılışında Drive henüz bağlı değilse dinleyici emailsiz groupId
            // ile başlar (örn: "metatakip_yedek01"). Email kaydedilince groupId değişir
            // (örn: "metatakip_yedek01_kullanici_gmail_com") ama dinleyici güncellenmez.
            // Bu fonksiyon dinleyiciyi doğru yola taşır — senkronizasyon ancak böyle çalışır.
            FirebaseRealtimeBridgeManager.onDriveAccountChanged(this)

            updateAccountInfo()
            lifecycleScope.launch { refreshDevicePanels() }
            log("✅ Drive bağlandı: ${account.email}")
            toast("Drive bağlantısı başarılı")
        } catch (e: Exception) {
            log("❌ Drive bağlantı hatası: ${e.message}")
            toast("Drive bağlantısı başarısız")
        }
    }


// ─────────────────────────────────────────────────────────────
// DEĞİŞİKLİK 2: btnDisconnectDrive (Drive KESİLİNCE)
// Satır ~390 civarında bulun, aşağıdaki gibi değiştirin:
// ─────────────────────────────────────────────────────────────

btnDisconnectDrive.setOnClickListener {
    driveAuthManager.signOut(this) {
        BackupPreferences.setDriveConnected(false)
        BackupPreferences.setDriveEmail(null)
        // NOT: setDriveConnected(false) zaten clearDriveRootFolderId() çağırıyor (BackupPreferences'te)

        BackupPreferences.setAutoBackupEnabled(false)
        AutoBackupScheduler.cancelAll(this)

        // 🔧 KRİTİK DÜZELTME: Drive kesildikten sonra dinleyiciyi güncelle
        FirebaseRealtimeBridgeManager.onDriveAccountChanged(this)

        updateAccountInfo()
        updateAutoBackupButtonText()
        lifecycleScope.launch { refreshDevicePanels() }
        log("🔌 Drive bağlantısı kaldırıldı")
        toast("Bağlantı kaldırıldı")
    }
}
