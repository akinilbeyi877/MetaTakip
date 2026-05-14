// ============================================================
// FirebaseRealtimeBridgeManager.kt — DÜZELTME
// syncPendingChanges içindeki "if (useChunkedUpload) { ... }"
// bloğunu AŞAĞIDAKİ ile DEĞİŞTİRİN.
// Tek parça (else) yol zaten doğru çalışıyor, ona dokunmayın.
// ============================================================

if (useChunkedUpload) {
    Log.d(TAG, "📦 ${unsynced.size} değişiklik için parçalı gönderim...")
    UiLogManager.log("📦 ${unsynced.size} değişiklik parçalara bölünüyor...")

    // 1) ÖNCE Drive'a tam ZIP yedeği yükle (alıcı cihaz bunu indirecek)
    val tempZip = FullBackupManager(context).createBackupZip()
    val fileName = "realtime_${TimeUtils.timestamp()}.zip"
    val savedFile = LocalBackupManager.saveToTodayFolder(
        context, tempZip, fileName, BackupFolderType.INSTANT
    )

    val uploaded = DriveUploadHelper.uploadToDrive(
        context, savedFile, BackupFolderType.INSTANT
    )
    if (!uploaded) {
        UiLogManager.log("❌ Drive yükleme başarısız, parçalı senk iptal")
        throw IOException("Drive upload başarısız")
    }
    DriveHistoryManager.recordBackup(context, "realtime", savedFile.name)
    UiLogManager.log("☁️ ZIP yedek Drive'a yüklendi: ${savedFile.name}")

    // 2) (Opsiyonel) Chunk'ları gönder — detay log için, alıcı bunları KULLANMIYOR
    val chunksOk = sendChunkedSyncData(context, groupId, deviceId, unsynced)
    if (!chunksOk) {
        Log.w(TAG, "⚠️ Chunk gönderimi tam başarılı değil, yine de event sinyali atılacak")
    }

    // 3) ⭐ KRİTİK: events/ koleksiyonuna TETİK SİNYALİ at
    //    Diğer cihazların startRemoteListener'ı SADECE events/'ı dinliyor.
    //    Bu sinyal atılmazsa hiçbir client yedeği indirmez.
    success = sendSinglePayload(context, groupId, deviceId, savedFile.name, unsynced)

    if (success) {
        ChangeLogManager.markAsSynced(context, unsynced.map { it.id })
        UiLogManager.log("✅ Parçalı gönderim başarılı, diğer cihazlar bildirildi!")
    } else {
        UiLogManager.log("❌ Firebase tetik sinyali gönderilemedi, alıcılar haberdar olmayabilir")
    }
}
