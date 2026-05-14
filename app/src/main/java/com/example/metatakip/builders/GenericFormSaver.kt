package com.example.metatakip.builders

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.metatakip.feature.admin.builders.FeatureAdminFormProvider
import com.example.metatakip.feature.customer.savers.FeatureCustomerSaver
import com.example.metatakip.feature.firma.savers.FeatureFirmaSaver
import com.example.metatakip.feature.label.savers.FeatureEtiketSablonSaver
import com.example.metatakip.feature.order.savers.FeatureOrderBilgiEkleSaver
import com.example.metatakip.feature.order.savers.FeatureOrderSaver
import com.example.metatakip.feature.unvan.savers.FeatureUnvanSaver
import com.example.metatakip.feature.uruntipi.data.UrunTipiDaoImpl
import com.example.metatakip.feature_data.entityModel.UrunTipi
import com.example.metatakip.feature_personel.savers.FeaturePersonelSaver
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.ChangeLogManager
import com.example.metatakip.feature_backup.worker.BackupWorker
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * GenericFormSaver
 * Uygulama ici tum kayit sureclerini yonetir.
 */
class GenericFormSaver(
    private val context: Context
) {

    fun save(
        table: String,
        data: Map<String, Any?>,
        editMode: Boolean,
        recordId: Long,
        intent: Intent? = null,
        messageProviderCustomer: ((firmaId: Long, customerId: Long, editMode: Boolean) -> String?)? = null,
        messageProvider: ((firmaId: Long, siparisId: Long) -> String?)? = null,
        messageProviderBilgiEkle: ((firmaId: Long, customerId: Long, siparisId: Long, urunAdi: String, adet: Int) -> String?)? = null,
        onFlowFinished: (() -> Unit)? = null
    ): Boolean {

        val normalizedTable = normalizeTable(table)
        val mutableData = data.toMutableMap()

        var finalEditMode = editMode
        var finalRecordId = recordId

        // 0. ADIM: Firma Ciftlemesini Onleme
        if (normalizedTable == "firma" && !finalEditMode) {
            val firmaAdi = mutableData["firmaAdi"]?.toString() ?: ""
            val mevcutFirmaId = findFirmaIdByName(firmaAdi)
            if (mevcutFirmaId != -1L) {
                finalEditMode = true
                finalRecordId = mevcutFirmaId
                val mevcutUuid = findFirmaUuidById(mevcutFirmaId)
                if (mevcutUuid != null) mutableData["uuid"] = mevcutUuid
            }
        }

        // 1. ADIM: Hibrit Kimlik (UUID) Atama
        if (!finalEditMode && (mutableData["uuid"] == null || mutableData["uuid"].toString().isEmpty())) {
            mutableData["uuid"] = UUID.randomUUID().toString()
        }

        // 2. ADIM: Zaman Damgasi
        mutableData["updatedAt"] = System.currentTimeMillis()

        // 3. ADIM: Iliskisel Veri Kontrolu
        if (normalizedTable == "siparis" && intent != null) {
            if (mutableData["musteri_uuid"] == null) {
                val intentMusteriUuid = intent.getStringExtra("musteri_uuid")
                if (!intentMusteriUuid.isNullOrEmpty()) mutableData["musteri_uuid"] = intentMusteriUuid
            }
            if (mutableData["musteriId"] == null) {
                val intentMusteriId = intent.getLongExtra("musteriId", -1L)
                if (intentMusteriId != -1L) mutableData["musteriId"] = intentMusteriId
            }
        }

        // 4. ADIM: Veriyi Kaydet
        val savedId: Long = when {
            FeatureCustomerSaver.canHandle(table) -> {
                val provider = messageProviderCustomer ?: { _, _, _ -> null }
                FeatureCustomerSaver.save(context, table, mutableData, finalEditMode, finalRecordId, intent, { f, c -> provider(f, c, finalEditMode) }, onFlowFinished)
            }

            FeatureFirmaSaver.canHandle(table) -> FeatureFirmaSaver.save(context, table, mutableData, finalEditMode, finalRecordId)

            FeaturePersonelSaver.canHandle(table) -> FeaturePersonelSaver.save(context, table, mutableData, finalEditMode, finalRecordId)

            FeatureOrderSaver.canHandle(table) || normalizedTable == "siparis" -> {
                FeatureOrderSaver.save(context, "siparis", mutableData, finalEditMode, finalRecordId, intent, messageProvider ?: { _, _ -> null }, onFlowFinished)
            }

            FeatureOrderBilgiEkleSaver.canHandle(table) || normalizedTable == "urun" -> {
                FeatureOrderBilgiEkleSaver.save(context, "urun", mutableData, finalEditMode, finalRecordId, intent, messageProviderBilgiEkle ?: { _, _, _, _, _ -> null }, onFlowFinished)
            }

            FeatureUnvanSaver.canHandle(table) -> FeatureUnvanSaver.save(context, table, mutableData, finalEditMode, finalRecordId)

            normalizedTable == "urun_tipi" -> if (saveUrunTipiData(mutableData, finalEditMode, finalRecordId)) 1L else -1L

            // ✅ ETİKET ŞABLON — eksikti, şimdi eklendi
            FeatureEtiketSablonSaver(context).canHandle(table) -> FeatureEtiketSablonSaver(context).save(table, mutableData, finalEditMode, finalRecordId)

            else -> {
                val adminSaver = FeatureAdminFormProvider(context)
                if (adminSaver.canHandle(table)) {
                    adminSaver.save(table, mutableData, finalEditMode, finalRecordId)
                } else {
                    -1L
                }
            }
        }

        // 5. ADIM: Loglama ve Worker Tetikleme
        if (savedId > 0L) {
            writeDetailLogIfNeeded(normalizedTable, mutableData, finalEditMode, savedId)
            return true
        }

        return false
    }

    private fun findFirmaIdByName(name: String): Long {
        if (name.isBlank()) return -1L
        var db: android.database.sqlite.SQLiteDatabase? = null
        return try {
            val dbPath = context.getDatabasePath("MetaTakip.db").absolutePath
            db = android.database.sqlite.SQLiteDatabase.openDatabase(dbPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT id FROM firma WHERE firmaAdi = ? LIMIT 1", arrayOf(name))
            val id = if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            cursor.close()
            id
        } catch (e: Exception) { -1L } finally {
            db?.close()
        }
    }

    private fun findFirmaUuidById(id: Long): String? {
        var db: android.database.sqlite.SQLiteDatabase? = null
        return try {
            val dbPath = context.getDatabasePath("MetaTakip.db").absolutePath
            db = android.database.sqlite.SQLiteDatabase.openDatabase(dbPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT uuid FROM firma WHERE id = ?", arrayOf(id.toString()))
            val uuid = if (cursor.moveToFirst()) cursor.getString(0) else null
            cursor.close()
            uuid
        } catch (e: Exception) { null } finally {
            db?.close()
        }
    }

    private fun writeDetailLogIfNeeded(table: String, data: Map<String, Any?>, editMode: Boolean, savedId: Long) {
        writeDetailLog(table, data, editMode, savedId)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            triggerInstantBackup()
        }, 300)
        android.util.Log.d("GenericFormSaver", "change_log'a yazildi [$table], BackupWorker 300ms sonra tetiklenecek.")
    }

    private fun triggerInstantBackup() {
        try {
            val networkConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val backupRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .addTag("instant_backup")
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "instant_backup_job",
                ExistingWorkPolicy.REPLACE,
                backupRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("GenericFormSaver", "Worker hatasi: ${e.message}")
        }
    }

    private fun writeDetailLog(table: String, data: Map<String, Any?>, editMode: Boolean, savedId: Long) {
        try {
            val json = JSONObject()
            val uuid = data["uuid"]?.toString() ?: UUID.randomUUID().toString()
            val updatedAt = data["updatedAt"] ?: System.currentTimeMillis()
            json.put("id", savedId); json.put("uuid", uuid)
            json.put("table", table); json.put("updatedAt", updatedAt)
            json.put("deviceId", BackupPreferences.getOrCreateDeviceId())
            data.forEach { (key, value) ->
                if (key !in setOf("id", "uuid", "table", "updatedAt", "deviceId")) {
                    when (value) {
                        null -> json.put(key, JSONObject.NULL)
                        is Number, is Boolean -> json.put(key, value)
                        else -> json.put(key, value.toString().replace("\"", "\\\""))
                    }
                }
            }
            ChangeLogManager.logChange(
                context = context,
                tableName = table,
                action = if (editMode) ChangeLog.ActionType.UPDATE else ChangeLog.ActionType.INSERT,
                recId = savedId,
                details = json.toString()
            )
        } catch (e: Exception) {
            android.util.Log.e("GenericFormSaver", "Manuel log hatasi: ${e.message}")
        }
    }

    private fun saveUrunTipiData(data: Map<String, Any?>, editMode: Boolean, recordId: Long): Boolean {
        return try {
            val urunTipi = UrunTipi(
                id = if (editMode) recordId else 0,
                ad = data["ad"]?.toString() ?: "",
                birimFiyat = data["birimFiyat"]?.toString()?.toDoubleOrNull() ?: 0.0,
                hesapTipi = data["hesapTipi"]?.toString() ?: "M2",
                aktif = if (data["aktif"]?.toString() in listOf("true", "1", "on")) 1 else 0
            )
            val dao = UrunTipiDaoImpl(context)
            if (editMode) dao.update(recordId, urunTipi) else dao.insert(urunTipi)
            true
        } catch (e: Exception) { false }
    }

    private fun normalizeTable(table: String): String {
        return table.trim().lowercase(Locale.ROOT).replace("-", "_").replace(" ", "_")
            .let {
                when (it) {
                    "order", "orders", "siparis" -> "siparis"
                    "customer", "customers", "musteri" -> "musteri"
                    "urun_tipi", "product_type" -> "urun_tipi"
                    "siparis_bilgi_ekle", "urun" -> "urun"
                    else -> it
                }
            }
    }
}
