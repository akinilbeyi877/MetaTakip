package com.example.metatakip.feature_backup.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_backup.data.ChangeLog
import com.example.metatakip.feature_backup.db.MigrationHelper
import com.example.metatakip.feature_data.db.MetaTakipDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🚦 ChangeLog Manager
 * Veritabanındaki 'change_log' tablosunu yöneten merkezi birimdir.
 * Değişiklikleri izler, listeler ve senkronizasyon durumlarını günceller.
 *
 * 🔧 Veritabanı kilitlenmelerini önlemek için tüm erişimler MetaTakipDbLock.lock ile senkronize edilmiştir.
 */
object ChangeLogManager {
    private const val TAG = "ChangeLogManager"

    // UI'da gösterilecek son değişiklikler akışı
    private val _recentChanges = MutableStateFlow<List<ChangeLog>>(emptyList())
    val recentChanges: StateFlow<List<ChangeLog>> = _recentChanges.asStateFlow()

    private var lastCheckTime: Long = System.currentTimeMillis()
    private val listeners = mutableListOf<OnChangeListener>()
    private var onChangesSavedListener: ((List<ChangeLog>) -> Unit)? = null

    interface OnChangeListener {
        fun onNewChange(change: ChangeLog)
        fun onChangesUpdated(changes: List<ChangeLog>)
    }

    fun initialize(context: Context) {
        try {
            // 🔥 KRİTİK: MigrationHelper çağrılmalı - Trigger'lar ve UUID'ler için
            MigrationHelper.checkAndMigrate(context)
            Log.d(TAG, "✅ ChangeLogManager altyapısı hazır.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Başlatma hatası: ${e.message}")
        }
    }

    /**
     * 🔎 Yeni değişiklikleri kontrol eder ve dinleyicileri tetikler.
     * Tüm veritabanı erişimi MetaTakipDbLock.lock ile korunur.
     */
    fun checkForNewChanges(context: Context): List<ChangeLog> {
        val newChanges = mutableListOf<ChangeLog>()
        var db: SQLiteDatabase? = null
        var cursor: Cursor? = null

        try {
            db = openDbReadable(context)

            // change_log tablosu yoksa oluştur
            if (!isChangeLogTableExists(db)) {
                Log.w(TAG, "⚠️ change_log tablosu bulunamadı, oluşturuluyor...")
                createChangeLogTable(db)
            }

            val lastCheckSeconds = lastCheckTime / 1000

            cursor = db.rawQuery(
                """
                SELECT id, table_name, action_type, record_id, changed_at, user_id, details, synced
                FROM change_log
                WHERE changed_at > ?
                ORDER BY changed_at ASC
                """.trimIndent(),
                arrayOf(lastCheckSeconds.toString())
            )

            if (cursor.moveToFirst()) {
                do {
                    newChanges.add(cursorToChangeLog(cursor))
                } while (cursor.moveToNext())
            }

            if (newChanges.isNotEmpty()) {
                lastCheckTime = (newChanges.last().changedAt / 1000 + 1) * 1000

                val currentList = _recentChanges.value.toMutableList()
                currentList.addAll(0, newChanges.reversed())
                _recentChanges.value = currentList.take(150)

                newChanges.forEach { change -> listeners.forEach { it.onNewChange(change) } }
                listeners.forEach { it.onChangesUpdated(_recentChanges.value) }
                onChangesSavedListener?.invoke(newChanges)

                Log.d(TAG, "📝 ${newChanges.size} yeni log işlendi.")
            }
        } catch (e: Exception) {
            handleDbError(context, e)
        } finally {
            closeQuietly(cursor, db)
        }
        return newChanges
    }

    /**
     * 🔥 YENİ: change_log tablosunun var olup olmadığını kontrol eder
     */
    private fun isChangeLogTableExists(db: SQLiteDatabase): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='change_log'",
            null
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    /**
     * 🔥 YENİ: change_log tablosunu oluşturur
     */
    private fun createChangeLogTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS change_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name TEXT NOT NULL,
                action_type TEXT NOT NULL,
                record_id INTEGER,
                changed_at INTEGER NOT NULL,
                user_id INTEGER DEFAULT 0,
                details TEXT,
                synced INTEGER DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_change_log_changed_at ON change_log(changed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_change_log_synced ON change_log(synced)")
        Log.d(TAG, "✅ change_log tablosu oluşturuldu.")
    }

    fun getUnsyncedChanges(context: Context): List<ChangeLog> {
        val changes = mutableListOf<ChangeLog>()
        var db: SQLiteDatabase? = null
        var cursor: Cursor? = null

        try {
            db = openDbReadable(context)

            // change_log tablosu yoksa boş liste dön
            if (!isChangeLogTableExists(db)) {
                Log.w(TAG, "⚠️ change_log tablosu yok, boş liste dönülüyor")
                return emptyList()
            }

            cursor = db.rawQuery(
                "SELECT * FROM change_log WHERE synced = 0 ORDER BY changed_at ASC",
                null
            )

            if (cursor.moveToFirst()) {
                do { changes.add(cursorToChangeLog(cursor)) } while (cursor.moveToNext())
            }
            Log.d(TAG, "📊 Senkronize edilmemiş ${changes.size} değişiklik var")
        } catch (e: Exception) {
            handleDbError(context, e)
        } finally {
            closeQuietly(cursor, db)
        }
        return changes
    }

    fun markAsSynced(context: Context, changeIds: List<Long>) {
        val safeIds = changeIds.filter { it > 0L }.distinct()
        if (safeIds.isEmpty()) return

        var db: SQLiteDatabase? = null
        try {
            db = openDbWritable(context)
            db.beginTransaction()

            val ids = safeIds.joinToString(",")
            db.execSQL("UPDATE change_log SET synced = 1 WHERE id IN ($ids)")

            db.setTransactionSuccessful()
            Log.d(TAG, "✅ ${safeIds.size} kayıt mühürlendi (synced=1).")
        } catch (e: Exception) {
            handleDbError(context, e)
        } finally {
            if (db?.inTransaction() == true) db.endTransaction()
            closeQuietly(null, db)
        }
    }

    fun cleanOldChanges(context: Context, olderThanDays: Int = 30) {
        var db: SQLiteDatabase? = null
        try {
            db = openDbWritable(context)

            if (!isChangeLogTableExists(db)) return

            val cutoffTime = (System.currentTimeMillis() / 1000) - (olderThanDays * 24 * 60 * 60)
            db.execSQL("DELETE FROM change_log WHERE changed_at < ? AND synced = 1", arrayOf(cutoffTime.toString()))
            Log.d(TAG, "🧹 Eski loglar temizlendi.")
        } catch (e: Exception) {
            handleDbError(context, e)
        } finally {
            closeQuietly(null, db)
        }
    }

    fun logChange(context: Context, tableName: String, action: ChangeLog.ActionType, recId: Long, details: String?) {
        var db: SQLiteDatabase? = null
        try {
            db = openDbWritable(context)

            // change_log tablosu yoksa oluştur
            if (!isChangeLogTableExists(db)) {
                createChangeLogTable(db)
            }

            val values = ContentValues().apply {
                put("table_name", tableName)
                put("action_type", action.name)
                put("record_id", recId)
                put("changed_at", System.currentTimeMillis() / 1000)
                put("user_id", 0)
                put("details", details)
                put("synced", 0)
            }
            val result = db.insert("change_log", null, values)
            if (result != -1L) {
                Log.d(TAG, "📝 Log kaydedildi: $tableName ${action.name} (ID:$recId)")
            } else {
                Log.e(TAG, "❌ Log kaydedilemedi: $tableName ${action.name}")
            }
        } catch (e: Exception) {
            handleDbError(context, e)
        } finally {
            closeQuietly(null, db)
        }
    }

    // --- YARDIMCI METOTLAR ---

    private fun cursorToChangeLog(cursor: Cursor): ChangeLog {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        val table = cursor.getString(cursor.getColumnIndexOrThrow("table_name"))
        val action = cursor.getString(cursor.getColumnIndexOrThrow("action_type"))
        val recId = cursor.getLong(cursor.getColumnIndexOrThrow("record_id"))
        val timeSec = cursor.getLong(cursor.getColumnIndexOrThrow("changed_at"))
        val timeMillis = timeSec * 1000
        val det = cursor.getString(cursor.getColumnIndexOrThrow("details"))
        val syn = cursor.getInt(cursor.getColumnIndexOrThrow("synced")) == 1

        return ChangeLog(
            id = id, tableName = table,
            actionType = ChangeLog.ActionType.valueOf(action),
            recordId = recId, changedAt = timeMillis,
            details = det, synced = syn
        )
    }

    private fun handleDbError(context: Context, e: Exception) {
        Log.e(TAG, "💥 Veritabanı hatası: ${e.message}")
        val msg = e.message.orEmpty().lowercase()
        if (msg.contains("corrupt") || msg.contains("malformed")) {
            recoverChangeLogTable(context)
        }
    }

    private fun recoverChangeLogTable(context: Context) {
        Log.w(TAG, "⚠️ Bozuk tablo onarılıyor...")
        var db: SQLiteDatabase? = null
        try {
            db = openDbWritable(context)
            db.execSQL("DROP TABLE IF EXISTS change_log")
            MigrationHelper.checkAndMigrate(context)
        } catch (_: Exception) {
        } finally {
            closeQuietly(null, db)
        }
    }

    private fun closeQuietly(cursor: Cursor?, db: SQLiteDatabase?) {
        try { cursor?.close() } catch (_: Exception) {}
        // SADECE cursor kapatılır, db asla kapatılmaz (havuz yönetir)
    }

    /**
     * 🔐 Tüm veritabanı erişimleri MetaTakipDbLock.lock ile senkronize edilir.
     * Bu sayede CsvImportManager, FullBackupManager ve ChangeLogManager aynı anda
     * database'e erişmeye çalıştığında kilitlenme olmaz.
     */
    private fun openDbReadable(context: Context): SQLiteDatabase {
        return synchronized(MetaTakipDbLock.lock) {
            MetaTakipDb.getInstance(context).readableDatabase
        }
    }

    private fun openDbWritable(context: Context): SQLiteDatabase {
        return synchronized(MetaTakipDbLock.lock) {
            MetaTakipDb.getInstance(context).writableDatabase
        }
    }

    fun addListener(l: OnChangeListener) {
        listeners.add(l)
        Log.d(TAG, "➕ Listener eklendi, toplam: ${listeners.size}")
    }

    fun removeListener(l: OnChangeListener) {
        listeners.remove(l)
        Log.d(TAG, "➖ Listener çıkarıldı, toplam: ${listeners.size}")
    }

    fun refreshRecentChanges(context: Context) {
        checkForNewChanges(context)
    }

    /**
     * 🔥 YENİ: change_log tablosundaki toplam kayıt sayısını döner (debug için)
     */
    fun getTotalChangeCount(context: Context): Int {
        var db: SQLiteDatabase? = null
        var cursor: Cursor? = null
        return try {
            db = openDbReadable(context)
            if (!isChangeLogTableExists(db)) return 0
            cursor = db.rawQuery("SELECT COUNT(*) FROM change_log", null)
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } catch (e: Exception) {
            0
        } finally {
            closeQuietly(cursor, db)
        }
    }

    /**
     * 🔥 YENİ: Son değişiklikleri detaylı loglar (debug için)
     */
    fun logRecentChanges(context: Context, limit: Int = 5) {
        var db: SQLiteDatabase? = null
        var cursor: Cursor? = null
        try {
            db = openDbReadable(context)
            if (!isChangeLogTableExists(db)) {
                Log.d(TAG, "📋 change_log tablosu yok")
                return
            }
            cursor = db.rawQuery(
                "SELECT id, table_name, action_type, synced FROM change_log ORDER BY id DESC LIMIT $limit",
                null
            )
            Log.d(TAG, "📋 Son $limit değişiklik:")
            while (cursor.moveToNext()) {
                Log.d(TAG, "   ID:${cursor.getLong(0)} Tablo:${cursor.getString(1)} Tip:${cursor.getString(2)} Senk:${cursor.getInt(3)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "logRecentChanges hatası: ${e.message}")
        } finally {
            closeQuietly(cursor, db)
        }
    }
}