package com.example.metatakip.feature_backup.firebase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.drive.*
import com.example.metatakip.feature_backup.local.CsvImportManager
import com.example.metatakip.feature_backup.local.LocalBackupManager
import com.example.metatakip.feature_backup.sync.SyncStatusStore
import com.example.metatakip.feature_backup.sync.UiLogManager
import com.example.metatakip.feature_backup.util.*
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import javax.net.ssl.SSLHandshakeException

/**
 * 🛰️ METATAKİP SİSTEMİ - CANLI SENKRONİZASYON KÖPRÜSÜ
 * Bu sınıf, cihazlar arasındaki veri trafiğini yöneten ana merkezdir.
 */
object FirebaseRealtimeBridgeManager {

    private const val TAG = "METATAKİP_SİSTEM"
    private const val POLL_DELAY_MS = 3000L
    private const val COOL_DOWN_MS = 15000L

    // 🔥 FIRESTORE BOYUT SINIRI İÇİN (1 MB = 1,048,576 bytes, güvenlik payı ile 850 KB)
    private const val MAX_FIRESTORE_DOC_SIZE = 850_000
    private const val MAX_CHUNK_SIZE = 500

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processedRemoteIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var listenerRegistration: ListenerRegistration? = null

    private val databaseMutex = Mutex()

    @Volatile private var started = false
    @Volatile private var uploadInProgress = false

    fun start(context: Context) {
        if (started) return
        started = true

        val appContext = context.applicationContext

        runCatching { ChangeLogManager.initialize(appContext) }
        runCatching { FirebaseTokenRegistrar.register(appContext) }

        startRemoteListener(appContext)

        scope.launch {
            while (isActive) {
                runCatching {
                    databaseMutex.withLock {
                        syncPendingChanges(appContext)
                    }
                }.onFailure {
                    Log.e(TAG, "⚠️ Senkronizasyon hatası: ${it.message}", it)
                    UiLogManager.log("⚠️ Sistem hatası: ${it.message}")
                }
                delay(POLL_DELAY_MS)
            }
        }

        UiLogManager.log("🚀 Canlı bağlantı sistemi başlatıldı")
        Log.d(TAG, "🚀 Canlı bağlantı aktif. Cihazlar arası takip başladı.")
    }

    /**
     * 🔄 Drive bağlantısı değiştiğinde (bağlan/kes) dinleyiciyi yeni groupId ile yeniden başlat.
     *
     * NEDEN GEREKLİ:
     *   start() uygulama açılışında bir kere çalışır.
     *   O anda Drive e-postası henüz kaydedilmemişse groupId eksik üretilir.
     *   Kullanıcı Drive'a bağlandıktan sonra bu fonksiyon çağrılmazsa
     *   dinleyici yanlış Firestore yolunu izlemeye devam eder —
     *   diğer cihazdan gelen sinyaller asla alınamaz.
     *
     * NEREDEN ÇAĞIRILMALI:
     *   BackupRestoreActivity.signInLauncher  → Drive bağlandıktan HEMEN sonra
     *   btnDisconnectDrive.setOnClickListener → Drive kesildikten HEMEN sonra
     */
    fun onDriveAccountChanged(context: Context) {
        val appContext = context.applicationContext
        val newGroupId = SyncGroupResolver.resolve(appContext)
        Log.d(TAG, "🔄 Drive hesabı değişti → dinleyici yeniden başlatılıyor. Yeni groupId='$newGroupId'")
        UiLogManager.log("🔄 Firebase dinleyici groupId güncellendi: $newGroupId")
        startRemoteListener(appContext)
    }

    private fun startRemoteListener(context: Context) {
        val groupId = SyncGroupResolver.resolve(context)
        val myDeviceId = BackupPreferences.getOrCreateDeviceId()

        // 🔍 TANI: Her iki cihazın bu satırı aynı groupId ile basması gerekir
        Log.d(TAG, "🔑 Firebase dinleyici başlatılıyor → groupId='$groupId'  deviceId='$myDeviceId'")

        listenerRegistration?.remove()

        listenerRegistration = Firebase.firestore
            .collection("sync_groups")
            .document(groupId)
            .collection("events")
            .orderBy("changedAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // 🔧 DÜZELTME: Hata artık Logcat'e de yazılıyor (önce sadece UI'daydı)
                    Log.e(TAG, "❌ Firebase listener hatası: ${error.message} [code=${error.code}]", error)
                    UiLogManager.log("❌ Firebase bağlantı hatası: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                Log.d(TAG, "📬 Firebase snapshot geldi → doküman sayısı=${snapshot.documents.size}  groupId='$groupId'")

                snapshot.documents.forEach { doc ->
                    if (!processedRemoteIds.add(doc.id)) {
                        Log.d(TAG, "⏭️ Zaten işlendi, atlandı: ${doc.id}")
                        return@forEach
                    }
                    val senderDeviceId = doc.getString("deviceId").orEmpty()
                    if (senderDeviceId == myDeviceId) {
                        Log.d(TAG, "⏭️ Kendi sinyali, atlandı: ${doc.id}")
                        return@forEach
                    }

                    val senderName = doc.getString("deviceName") ?: "Diğer Cihaz"
                    Log.d(TAG, "📡 Uzak olay işleniyor: docId=${doc.id}  gönderen=$senderName")
                    UiLogManager.log("📡 Uzak olay alındı: $senderName")

                    val event = RealtimeSyncEvent(
                        eventId = doc.id,
                        groupId = groupId,
                        deviceId = senderDeviceId,
                        deviceName = senderName,
                        fileName = doc.getString("fileName").orEmpty(),
                        backupType = doc.getString("backupType") ?: "INSTANT",
                        changedAt = doc.getLong("changedAt") ?: 0L,
                        changeCount = doc.getLong("changeCount")?.toInt() ?: 0
                    )

                    scope.launch {
                        databaseMutex.withLock {
                            applyRemoteEvent(context, event)
                        }
                    }
                }
            }
    }

    private suspend fun applyRemoteEvent(context: Context, event: RealtimeSyncEvent) {
        // 🔥 CSV IMPORT KONTROLÜ
        if (CsvImportManager.isImportInProgress) {
            Log.d(TAG, "⏸️ CSV import devam ediyor, uzak olay ertelendi: ${event.deviceName}")
            UiLogManager.log("⏸️ CSV import devam ediyor, ${event.deviceName} olayı ertelendi")
            return
        }

        UiLogManager.log("📥 Veri indiriliyor: ${event.deviceName}")
        // Başlangıçta kaydet — başarısız olsa bile "nereden geldi" bilgisi görünsün
        BackupPreferences.setLastSyncDevice(event.deviceName)

        withContext(Dispatchers.Main) { SyncStatusStore.showBusy("📥 Veri alınıyor...") }

        runWithDriveLockRealtime(context, "Realtime Pull - ${event.deviceName}") {
            val result = DriveDownloadHelper.downloadLatestBackup(context, BackupFolderType.INSTANT, "realtime_pull")

            if (!result.success || result.file == null) {
                UiLogManager.log("❌ Dosya indirilemedi: ${result.message}")
                withContext(Dispatchers.Main) { SyncStatusStore.showInfo("❌ İndirme başarısız — ${event.deviceName}") }
                return@runWithDriveLockRealtime
            }
            val file = result.file!! // smart-cast fix: cross-module property yerel degiskene atandi

            // Geçersiz/bozuk ZIP koruması — 100 byte altı dosya restore edilmez
            val fileSize = file.length()
            if (fileSize < 100) {
                UiLogManager.log("⚠️ İndirilen ZIP geçersiz (${fileSize} byte) — ${event.deviceName} tarafı boş paket yüklemiş olabilir, restore atlandı.")
                withContext(Dispatchers.Main) { SyncStatusStore.showInfo("⚠️ Boş paket — ${event.deviceName}") }
                return@runWithDriveLockRealtime
            }

            val localUnsyncedChanges = ChangeLogManager.getUnsyncedChanges(context)
            val restored = ZipRestoreHelper.restoreFullZipToActiveDatabase(context, file)

            if (restored) {
                if (localUnsyncedChanges.isNotEmpty()) {
                    UiLogManager.log("♻️ Yerel ${localUnsyncedChanges.size} kayıt geri birleştiriliyor")
                    mergeLocalChangesBack(context, localUnsyncedChanges)
                }

                val lingering = ChangeLogManager.getUnsyncedChanges(context)
                ChangeLogManager.markAsSynced(context, lingering.map { it.id })

                BackupPreferences.setLastBackupTime(System.currentTimeMillis())
                BackupPreferences.setLastSyncDevice(event.deviceName)

                // ✅ Son alınan yedek bilgilerini kaydet (dialog için)
                val restoredFileName = result.latest?.fileName ?: file.name
                if (restoredFileName.isNotBlank()) BackupPreferences.setLastRestoreFile(restoredFileName)
                (BackupPreferences.getActualDriveEmail() ?: BackupPreferences.getDriveEmail())
                    ?.let { BackupPreferences.setLastRestoreEmail(it) }
                BackupPreferences.setLastRestoreFolder(BackupPreferences.getBackupFolderName())

                withContext(Dispatchers.Main) {
                    context.sendBroadcast(android.content.Intent("com.example.metatakip.REFRESH_UI").setPackage(context.packageName))
                    SyncStatusStore.showInfo("✅ Senkronizasyon Tamam")
                }
                UiLogManager.log("✅ Başarıyla güncellendi: ${event.deviceName}")
            }
        }
    }

    // ==================== 🔥 PARÇALI GÖNDERİM (CHUNKED UPLOAD) ====================

    private suspend fun sendChunkedSyncData(
        context: Context,
        groupId: String,
        deviceId: String,
        changes: List<ChangeLog>
    ): Boolean {
        val chunks = chunkChanges(changes)
        var allSuccess = true
        val totalChunks = chunks.size

        Log.d(TAG, "📦 Değişiklikler $totalChunks parçaya bölündü")
        UiLogManager.log("📦 $totalChunks parça halinde gönderiliyor...")

        for ((index, chunk) in chunks.withIndex()) {
            val chunkId = "${deviceId}_${System.currentTimeMillis()}_${index}"
            val chunkData = buildChunkPayload(context, groupId, deviceId, chunk, index, totalChunks, chunkId)

            val chunkSize = chunkData.toString().toByteArray(Charsets.UTF_8).size
            if (chunkSize > MAX_FIRESTORE_DOC_SIZE) {
                Log.e(TAG, "❌ Chunk $index hala çok büyük: ${chunkSize} bytes")
                val subChunks = chunkChanges(chunk, maxChunkSize = MAX_CHUNK_SIZE / 2)
                for ((subIndex, subChunk) in subChunks.withIndex()) {
                    val subChunkId = "${chunkId}_sub_${subIndex}"
                    val success = sendSingleChunk(context, groupId, subChunkId, buildChunkPayload(
                        context, groupId, deviceId, subChunk, subIndex, subChunks.size, subChunkId
                    ))
                    if (!success) allSuccess = false
                    delay(300)
                }
            } else {
                val success = sendSingleChunk(context, groupId, chunkId, chunkData)
                if (!success) allSuccess = false
            }

            if (!allSuccess) break
            delay(500)
        }

        return allSuccess
    }

    private suspend fun sendSingleChunk(
        context: Context,
        groupId: String,
        chunkId: String,
        chunkData: Map<String, Any>
    ): Boolean {
        return try {
            Tasks.await(
                Firebase.firestore
                    .collection("sync_groups")
                    .document(groupId)
                    .collection("chunks")
                    .document(chunkId)
                    .set(chunkData)
            )
            Log.d(TAG, "✅ Chunk gönderildi: $chunkId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Chunk gönderilemedi: ${e.message}")
            false
        }
    }

    private fun chunkChanges(changes: List<ChangeLog>, maxChunkSize: Int = MAX_CHUNK_SIZE): List<List<ChangeLog>> {
        val chunks = mutableListOf<List<ChangeLog>>()
        var currentChunk = mutableListOf<ChangeLog>()

        for (change in changes) {
            if (currentChunk.size >= maxChunkSize) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
            }
            currentChunk.add(change)
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }

        return chunks
    }

    private fun buildChunkPayload(
        context: Context,
        groupId: String,
        deviceId: String,
        chunk: List<ChangeLog>,
        chunkIndex: Int,
        totalChunks: Int,
        chunkId: String
    ): HashMap<String, Any> {
        val changesArray = JSONArray()
        chunk.forEach { change ->
            changesArray.put(JSONObject().apply {
                put("tableName", change.tableName)
                put("actionType", change.actionType.name)
                put("recordId", change.recordId)
                put("changedAt", change.changedAt)
                put("details", JSONObject(change.details ?: "{}"))
            })
        }

        return hashMapOf(
            "groupId" to groupId,
            "deviceId" to deviceId,
            "deviceName" to BackupPreferences.getDeviceName(),
            "chunkIndex" to chunkIndex,
            "totalChunks" to totalChunks,
            "chunkId" to chunkId,
            "changes" to changesArray.toString(),
            "changeCount" to chunk.size,
            "timestamp" to System.currentTimeMillis(),
            "isRealtimeChunk" to true
        )
    }

    private suspend fun sendSinglePayload(
        context: Context,
        groupId: String,
        deviceId: String,
        fileName: String,
        unsynced: List<ChangeLog>
    ): Boolean {
        return try {
            val payload = buildFirebasePayload(context, fileName, unsynced)

            val payloadSize = payload.toString().toByteArray(Charsets.UTF_8).size
            if (payloadSize > MAX_FIRESTORE_DOC_SIZE) {
                Log.w(TAG, "⚠️ Payload çok büyük (${payloadSize} bytes), sadece tetik sinyali gönderiliyor")
                val minimalPayload = buildMinimalFirebasePayload(context, fileName, unsynced.size)
                Tasks.await(
                    Firebase.firestore
                        .collection("sync_groups")
                        .document(groupId)
                        .collection("events")
                        .document("${deviceId}_${System.currentTimeMillis()}")
                        .set(minimalPayload)
                )
                Log.d(TAG, "✅ Firebase tetik sinyali gönderildi (minimal)")
                return true
            }

            Tasks.await(
                Firebase.firestore
                    .collection("sync_groups")
                    .document(groupId)
                    .collection("events")
                    .document("${deviceId}_${System.currentTimeMillis()}")
                    .set(payload)
            )
            Log.d(TAG, "✅ Firebase sinyali gönderildi")
            true
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Tek parça gönderim başarısız: ${e.message}")
            false
        }
    }

    // ==================== ANA SENKRONİZASYON FONKSİYONU ====================

    internal suspend fun syncPendingChanges(context: Context) {
        Log.d(TAG, "🔍 syncPendingChanges çağrıldı")

        if (CsvImportManager.isImportInProgress) {
            Log.d(TAG, "⏸️ CSV import devam ediyor, senkronizasyon beklemeye alındı")
            UiLogManager.log("⏸️ CSV import devam ediyor, senkronizasyon ertelendi")
            return
        }

        Log.d(TAG, "   uploadInProgress = $uploadInProgress")
        Log.d(TAG, "   isDriveConnected = ${BackupPreferences.isDriveConnected()}")

        if (uploadInProgress || !BackupPreferences.isDriveConnected()) {
            Log.d(TAG, "⚠️ ENGEL 1: uploadInProgress=$uploadInProgress veya Drive bağlı değil")
            return
        }

        val unsynced = ChangeLogManager.getUnsyncedChanges(context)
        Log.d(TAG, "   unsynced size = ${unsynced.size}")

        if (unsynced.isEmpty()) {
            Log.d(TAG, "⚠️ ENGEL 2: unsynced boş")
            return
        }

        val lastBackupTime = BackupPreferences.getLastBackupTime()
        val timeSinceLastBackup = System.currentTimeMillis() - lastBackupTime
        Log.d(TAG, "   lastBackupTime = $lastBackupTime")
        Log.d(TAG, "   timeSinceLastBackup = $timeSinceLastBackup ms")
        Log.d(TAG, "   COOL_DOWN_MS = $COOL_DOWN_MS ms")

        if (timeSinceLastBackup < COOL_DOWN_MS) {
            Log.d(TAG, "⚠️ ENGEL 3: Soğuma süresinde")
            return
        }

        Log.d(TAG, "✅ Tüm kontroller geçildi, senkronizasyon başlatılıyor...")
        uploadInProgress = true

        try {
            UiLogManager.log("🔐 Kilit aranıyor...")
            val lock = DriveLockManager.acquireLock(context, 10_000L, "firebase_sync")

            if (!lock.acquired) {
                UiLogManager.log("⚠️ Kilit meşgul, ertelendi")
                return
            }

            Log.d(TAG, "✅ Kilit alındı, yükleme başlıyor...")

            try {
                var success = false
                val groupId = SyncGroupResolver.resolve(context)
                val deviceId = BackupPreferences.getOrCreateDeviceId()

                val useChunkedUpload = unsynced.size > 200

                if (useChunkedUpload) {
                    // ================================================================
                    // 🔴 ESKİ HATA: Önce sendChunkedSyncData (/chunks) çağrılıyordu,
                    //    sonra Drive'a yükleniyordu ve events/ sinyali HİÇ gönderilmiyordu.
                    //    Dinleyici sadece events/ koleksiyonunu izlediği için diğer cihazlar
                    //    büyük yedeklerden HABERDAR OLMUYORDU.
                    //
                    // ✅ DÜZELTME:
                    //    1) Önce Drive'a ZIP yükle (alıcı bunu indirecek)
                    //    2) Sonra events/ koleksiyonuna tetik sinyali at
                    // ================================================================

                    Log.d(TAG, "📦 ${unsynced.size} değişiklik için parçalı gönderim (DÜZELTİLMİŞ SIRALAMA)...")
                    UiLogManager.log("📦 ${unsynced.size} değişiklik — önce Drive, sonra Firebase tetik...")

                    // ADIM 1: Önce Drive'a tam ZIP yükle
                    val tempZip = FullBackupManager(context).createBackupZip()
                    val fileName = "realtime_${TimeUtils.timestamp()}.zip"
                    val savedFile = LocalBackupManager.saveToTodayFolder(
                        context, tempZip, fileName, BackupFolderType.INSTANT
                    )

                    // 🛡️ Geçersiz ZIP koruması — 1 KB'dan küçük dosya yüklenmiyor
                    val savedSizeBytes = savedFile.length()
                    Log.d(TAG, "📏 ZIP boyutu: $savedSizeBytes byte (${savedFile.name})")
                    UiLogManager.log("📏 ZIP boyutu: $savedSizeBytes byte")
                    if (savedSizeBytes < 1024) {
                        throw IOException("❌ Oluşturulan ZIP geçersiz ($savedSizeBytes byte < 1 KB) — WAL checkpoint sorunu olabilir, yükleme iptal edildi")
                    }

                    val uploaded = DriveUploadHelper.uploadToDrive(
                        context, savedFile, BackupFolderType.INSTANT
                    )
                    if (!uploaded) throw IOException("Drive upload başarısız")

                    DriveHistoryManager.recordBackup(context, "realtime", savedFile.name)
                    UiLogManager.log("☁️ ZIP Drive'a yüklendi: ${savedFile.name}")

                    // ADIM 2: events/ koleksiyonuna tetik sinyali at
                    //         DİĞER CİHAZLAR SADECE events/ DİNLİYOR — BU OLMADAN GÖRMEZLER
                    success = sendSinglePayload(context, groupId, deviceId, savedFile.name, unsynced)

                    if (success) {
                        ChangeLogManager.markAsSynced(context, unsynced.map { it.id })
                        UiLogManager.log("✅ Parçalı gönderim tamamlandı, diğer cihazlar bildirildi!")
                        Log.d(TAG, "✅ Parçalı yol: Drive yüklendi + events/ sinyali gönderildi")
                    } else {
                        Log.e(TAG, "❌ Firebase tetik sinyali gönderilemedi!")
                        UiLogManager.log("❌ Firebase tetik sinyali gönderilemedi, diğer cihazlar haberdar olmayabilir!")
                    }

                } else {
                    Log.d(TAG, "📦 Paket hazırlanıyor...")
                    UiLogManager.log("📦 Paket hazırlanıyor...")

                    val tempZip = FullBackupManager(context).createBackupZip()
                    val fileName = "realtime_${TimeUtils.timestamp()}.zip"
                    val savedFile = LocalBackupManager.saveToTodayFolder(context, tempZip, fileName, BackupFolderType.INSTANT)

                    // 🛡️ Geçersiz ZIP koruması — 1 KB'dan küçük dosya yüklenmiyor
                    val savedSizeBytes = savedFile.length()
                    Log.d(TAG, "📏 ZIP boyutu: $savedSizeBytes byte (${savedFile.name})")
                    UiLogManager.log("📏 ZIP boyutu: $savedSizeBytes byte")
                    if (savedSizeBytes < 1024) {
                        throw IOException("❌ Oluşturulan ZIP geçersiz ($savedSizeBytes byte < 1 KB) — WAL checkpoint sorunu olabilir, yükleme iptal edildi")
                    }

                    val uploaded = DriveUploadHelper.uploadToDrive(context, savedFile, BackupFolderType.INSTANT)
                    if (!uploaded) throw IOException("Drive upload başarısız")

                    DriveHistoryManager.recordBackup(context, "realtime", savedFile.name)

                    success = sendSinglePayload(context, groupId, deviceId, savedFile.name, unsynced)

                    if (success) {
                        ChangeLogManager.markAsSynced(context, unsynced.map { it.id })
                        UiLogManager.log("📤 Gönderim başarılı!")
                    } else {
                        Log.w(TAG, "⚠️ Tek parça gönderim başarısız, parçalı gönderime geçiliyor...")
                        success = sendChunkedSyncData(context, groupId, deviceId, unsynced)
                        if (success) {
                            ChangeLogManager.markAsSynced(context, unsynced.map { it.id })
                            UiLogManager.log("✅ Parçalı gönderim başarılı!")
                        }
                    }
                }

                if (!success) {
                    Log.e(TAG, "❌ Tüm yükleme denemeleri başarısız oldu!")
                    UiLogManager.log("❌ Yükleme başarısız oldu!")
                }
            } finally {
                DriveLockManager.releaseLock(context)
                DriveQueueManager.removeCurrentDevice(context)
                Log.d(TAG, "🔓 Kilit serbest bırakıldı")
            }
        } finally {
            uploadInProgress = false
            Log.d(TAG, "🏁 syncPendingChanges tamamlandı")
        }
    }

    private suspend fun mergeLocalChangesBack(context: Context, changes: List<ChangeLog>): Boolean = withContext(Dispatchers.IO) {
        synchronized(MetaTakipDbLock.lock) {
            val db = MetaTakipDb.getInstance(context).writableDatabase
            db.beginTransaction()
            try {
                val partialManager = PartialBackupManager(context)
                changes.forEach { change ->
                    if (!change.details.isNullOrBlank()) {
                        partialManager.applySingleChangeLogToDb(db, change)
                    }
                }
                db.setTransactionSuccessful()
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Merge hatası: ${e.message}")
                false
            } finally {
                db.endTransaction()
            }
        }
    }

    private suspend fun <T> runWithDriveLockRealtime(
        context: Context,
        operationName: String,
        block: suspend () -> T
    ): T? {
        DriveQueueManager.enqueue(context, operationName)
        val acquire = DriveLockManager.acquireLock(context, waitTimeoutMs = 30_000L, action = operationName)

        if (!acquire.acquired) {
            DriveQueueManager.removeCurrentDevice(context)
            return null
        }

        return try {
            block()
        } finally {
            DriveLockManager.releaseLock(context)
            DriveQueueManager.removeCurrentDevice(context)
        }
    }

    private fun buildFirebasePayload(context: Context, fileName: String, logs: List<ChangeLog>): HashMap<String, Any> {
        val changesList = logs.map {
            mapOf(
                "t" to it.tableName,
                "a" to it.actionType.name,
                "r" to it.recordId,
                "c" to it.changedAt
            )
        }

        return hashMapOf(
            "groupId" to SyncGroupResolver.resolve(context),
            "deviceId" to BackupPreferences.getOrCreateDeviceId(),
            "deviceName" to BackupPreferences.getDeviceName(),
            "fileName" to fileName,
            "backupType" to BackupFolderType.INSTANT.name,
            "changedAt" to System.currentTimeMillis(),
            "changeCount" to logs.size,
            "tableNames" to logs.map { it.tableName }.distinct(),
            "changes" to changesList
        )
    }

    private fun buildMinimalFirebasePayload(context: Context, fileName: String, changeCount: Int): HashMap<String, Any> {
        return hashMapOf(
            "groupId" to SyncGroupResolver.resolve(context),
            "deviceId" to BackupPreferences.getOrCreateDeviceId(),
            "deviceName" to BackupPreferences.getDeviceName(),
            "fileName" to fileName,
            "backupType" to BackupFolderType.INSTANT.name,
            "changedAt" to System.currentTimeMillis(),
            "changeCount" to changeCount
        )
    }

    /**
     * 🔥 ZORLA SENKRONİZASYON (CSV Import sonrası için)
     */
    suspend fun forceSyncNow(context: Context): Boolean {
        if (CsvImportManager.isImportInProgress) {
            Log.d(TAG, "CSV import devam ediyor, forceSyncNow ertelendi")
            UiLogManager.log("CSV import devam ediyor, manuel senkronizasyon ertelendi")
            return false
        }

        Log.d(TAG, "Zorla senkronizasyon baslatiliyor...")
        UiLogManager.log("Manuel senkronizasyon tetiklendi")

        // Cooldown'u sifirla: kullanici butona bastiginda bekleme olmadan aninda gondersin.
        // Normal otomatik senkronda 15 saniye cooldown uygulanir, ama manuel tetiklemede uygulanmaz.
        BackupPreferences.setLastBackupTime(0L)

        return try {
            databaseMutex.withLock {
                syncPendingChanges(context)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Zorla senkronizasyon hatasi: ${e.message}")
            false
        }
    }

    @Volatile
    var isSyncEnabled = true
        private set

    fun enableSync() {
        isSyncEnabled = true
        Log.d(TAG, "✅ Senkronizasyon açıldı")
    }

    fun disableSync() {
        isSyncEnabled = false
        Log.d(TAG, "⏸️ Senkronizasyon kapatıldı")
    }

    private fun shouldRetry(e: Throwable): Boolean = e is IOException || e is SocketTimeoutException || e is UnknownHostException
}
