package dao

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.metatakip.feature_data.common.PhoneUtils
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Customer
import com.example.metatakip.feature_data.entityModel.SessionManager
import org.json.JSONObject
import java.util.UUID

class MetaTakipCustomerDao(private val context: Context) {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val sessionManager = SessionManager(context)
    private val TAG = "MetaTakipCustomerDao"

    // ================================================================
    // 📞 TELEFON NORMALIZE
    // ================================================================
    private fun normalizePhone(number: String): String =
        PhoneUtils.normalizeKeyTR(number)

    private fun cleanExpr(col: String): String =
        "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE($col,' ',''),'-',''),'(',''),')',''),'+','')"

    private fun last10Expr(col: String): String =
        "substr(${cleanExpr(col)}, -10)"

    // ================================================================
    // 🔔 CHANGE LOG YARDIMCI — her write sonrası çağrılır
    // ================================================================
    private fun writeChangeLog(
        db: SQLiteDatabase,
        tableName: String,
        action: String,       // "INSERT" | "UPDATE" | "DELETE"
        recordId: Long,
        details: String = ""
    ) {
        try {
            val values = ContentValues().apply {
                put("table_name", tableName)
                put("action_type", action)
                put("record_id", recordId)
                put("changed_at", System.currentTimeMillis() / 1000)
                put("details", details)
                put("synced", 0)
                put("user_id", sessionManager.userId.toLong())
            }
            val logId = db.insert("change_log", null, values)
            Log.d(TAG, "📋 change_log[$action] → $tableName(id=$recordId) logId=$logId")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ change_log yazma hatası: ${e.message}")
        }
    }

    /** Müşteri kaydını JSON details olarak döndürür */
    private fun customerToJson(customer: Customer, extraId: Long = -1): String {
        return try {
            val j = JSONObject()
            j.put("id", if (extraId >= 0) extraId else customer.id)
            j.put("uuid", customer.uuid)
            j.put("adSoyad", customer.adSoyad ?: "")
            j.put("ceptel", customer.ceptel ?: "")
            j.put("ceptel2", customer.ceptel2 ?: "")
            j.put("bolge", customer.bolge ?: "")
            j.put("adres", customer.adres ?: "")
            j.put("musteriNotu", customer.musteriNotu ?: "")
            j.put("firmaAdi", customer.firmaAdi ?: "")
            j.put("firmaid", customer.firmaid ?: 0L)
            j.put("isDeleted", customer.isDeleted)
            j.put("updatedAt", customer.updatedAt)
            j.toString()
        } catch (e: Exception) { "{}" }
    }

    // ================================================================
    // 📜 TÜM MÜŞTERİLER (AKTİF + SİLİNMİŞ)
    // ================================================================
    fun getAllCustomersIncludingDeleted(): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m
            LEFT JOIN firma f ON m.firmaid = f.id
            ORDER BY m.isDeleted DESC, m.deletedAt DESC, m.id DESC
            """, null
        )
        while (cursor.moveToNext()) list.add(mapCustomer(cursor))
        cursor.close()
        return list
    }

    fun getDeletedCustomerCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM musteri WHERE isDeleted = 1", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    fun getActiveCustomersWithDeletedOrdersCount(): Int {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT COUNT(DISTINCT m.id) 
            FROM musteri m
            INNER JOIN siparis s ON m.id = s.musteriId
            WHERE m.isDeleted = 0 AND s.isDeleted = 1
        """
        val cursor = db.rawQuery(query, null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    // ================================================================
    // 🔍 TELEFONA GÖRE BUL
    // ================================================================
    fun findCustomerByNormalizedPhone(phone: String): Customer? {
        val normalized10 = normalizePhone(phone)
        if (normalized10.isBlank()) return null
        val db = dbHelper.readableDatabase
        val tel1 = last10Expr("m.ceptel")
        val tel2 = last10Expr("m.ceptel2")
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.isDeleted = 0 AND ($tel1 = ? OR $tel2 = ?)
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalized10, normalized10)
        )
        val customer = if (cursor.moveToFirst()) mapCustomer(cursor) else null
        cursor.close()
        return customer
    }

    // ================================================================
    // 🔍 FIRMAYA GÖRE MÜŞTERİLER
    // ================================================================
    fun getCustomersByFirmaId(firmaId: Long): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.firmaid = ? AND m.isDeleted = 0
            ORDER BY m.id DESC
            """,
            arrayOf(firmaId.toString())
        )
        while (cursor.moveToNext()) list.add(mapCustomer(cursor))
        cursor.close()
        return list
    }

    // ================================================================
    // 🔍 FIRMA ADINA GÖRE MÜŞTERİLER
    // ================================================================
    fun searchCustomersByFirmaName(searchTerm: String): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE (f.firmaAdi LIKE ? OR m.firmaAdi LIKE ?) AND m.isDeleted = 0
            ORDER BY m.id DESC
            """,
            arrayOf("%$searchTerm%", "%$searchTerm%")
        )
        while (cursor.moveToNext()) list.add(mapCustomer(cursor))
        cursor.close()
        return list
    }

    // ================================================================
    // ➕ CREATE — change_log yazılır
    // ================================================================
    fun addCustomer(customer: Customer): Boolean =
        addCustomerAndReturnId(customer) != -1L

    fun addCustomerAndReturnId(customer: Customer): Long {
        val db = dbHelper.writableDatabase
        return try {
            val values = buildCustomerContentValues(customer)
            values.put("isDeleted", 0)
            // uuid yoksa üret
            if (!values.containsKey("uuid") || values.getAsString("uuid").isNullOrBlank()) {
                values.put("uuid", UUID.randomUUID().toString())
            }
            values.put("updatedAt", System.currentTimeMillis())

            val newId = db.insert("musteri", null, values)
            if (newId > 0) {
                // ✅ ANLIK SENKRONIZASYON: change_log kaydı
                val details = customerToJson(customer, extraId = newId)
                writeChangeLog(db, "musteri", "INSERT", newId, details)
                Log.d(TAG, "✅ Müşteri eklendi + change_log: ID=$newId")
            }
            newId
        } catch (e: Exception) {
            Log.e(TAG, "❌ addCustomerAndReturnId", e)
            -1L
        }
    }

    fun addCustomerAndGetId(customer: Customer): Long = addCustomerAndReturnId(customer)

    // ================================================================
    // 📞 HER İKİ TELEFONDA ARA
    // ================================================================
    fun findCustomersByAnyPhone(phone: String): List<Customer> {
        val normalized10 = normalizePhone(phone)
        if (normalized10.isBlank()) return emptyList()
        val list = mutableListOf<Customer>()
        val db = dbHelper.readableDatabase
        val tel1 = last10Expr("m.ceptel")
        val tel2 = last10Expr("m.ceptel2")
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.isDeleted = 0 AND ($tel1 = ? OR $tel2 = ?)
            ORDER BY m.id DESC
            """.trimIndent(),
            arrayOf(normalized10, normalized10)
        )
        while (cursor.moveToNext()) list.add(mapCustomer(cursor))
        cursor.close()
        return list
    }

    // ================================================================
    // 🏢 MÜŞTERİNİN FİRMASINI BUL
    // ================================================================
    fun findFirmaForCustomer(phone: String): String? {
        val normalized10 = normalizePhone(phone)
        if (normalized10.isBlank()) return null
        val db = dbHelper.readableDatabase
        val tel1 = last10Expr("m.ceptel")
        val tel2 = last10Expr("m.ceptel2")
        val cursor = db.rawQuery(
            """
            SELECT f.firmaAdi FROM musteri m
            INNER JOIN firma f ON m.firmaid = f.id
            WHERE m.isDeleted = 0 AND ($tel1 = ? OR $tel2 = ?)
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalized10, normalized10)
        )
        val firma = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        return firma
    }

    // ================================================================
    // ✏️ UPDATE — change_log yazılır
    // ================================================================
    fun updateCustomerById(id: Long, customer: Customer): Boolean {
        val db = dbHelper.writableDatabase
        val values = buildCustomerContentValues(customer)
        values.put("updatedAt", System.currentTimeMillis())

        Log.d("CUSTOMER_DAO", "📝 UPDATE: id=$id, adSoyad=${customer.adSoyad}, firmaid=${customer.firmaid}")

        val result = db.update("musteri", values, "id=? AND isDeleted=0", arrayOf(id.toString()))

        Log.d("CUSTOMER_DAO", "📊 UPDATE sonucu: $result satır etkilendi")

        if (result > 0) {
            // ✅ ANLIK SENKRONIZASYON: change_log kaydı
            val details = customerToJson(customer, extraId = id)
            writeChangeLog(db, "musteri", "UPDATE", id, details)
            Log.d(TAG, "✅ Müşteri güncellendi + change_log: ID=$id")
        }
        return result > 0
    }

    // ================================================================
    // 🏢 FIRMA ID GÜNCELLE — change_log yazılır
    // ================================================================
    fun updateCustomerFirmaId(customerId: Long, firmaId: Long?): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            if (firmaId == null) putNull("firmaid") else put("firmaid", firmaId)
            put("updatedAt", System.currentTimeMillis())
        }
        val result = db.update("musteri", values, "id=? AND isDeleted=0", arrayOf(customerId.toString())) > 0
        if (result) {
            val details = JSONObject().apply {
                put("id", customerId)
                put("firmaid", firmaId ?: JSONObject.NULL)
            }.toString()
            writeChangeLog(db, "musteri", "UPDATE", customerId, details)
        }
        return result
    }

    fun updateCustomerPhoto(customerId: Long, photoPath: String): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("photoPath", photoPath)
            put("updatedAt", System.currentTimeMillis())
        }
        val result = db.update("musteri", values, "id=? AND isDeleted=0", arrayOf(customerId.toString())) > 0
        if (result) {
            val details = JSONObject().apply {
                put("id", customerId)
                put("photoPath", photoPath)
            }.toString()
            writeChangeLog(db, "musteri", "UPDATE", customerId, details)
        }
        return result
    }

    // ================================================================
    // 📍 KONUM GÜNCELLE — change_log yazılır
    // ================================================================
    fun updateCustomerLocationFull(
        customerId: Long,
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        address: String?
    ): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("locationTimestamp", timestamp)
            put("locationAddress", address)
            put("updatedAt", System.currentTimeMillis())
        }
        val result = db.update("musteri", values, "id=? AND isDeleted=0", arrayOf(customerId.toString())) > 0
        if (result) {
            val details = JSONObject().apply {
                put("id", customerId)
                put("latitude", latitude)
                put("longitude", longitude)
                put("locationTimestamp", timestamp)
                put("locationAddress", address ?: JSONObject.NULL)
            }.toString()
            writeChangeLog(db, "musteri", "UPDATE", customerId, details)
        }
        return result
    }

    // ================================================================
    // 🗑️ DELETE CUSTOMER + CASCADE — change_log yazılır
    // ================================================================
    fun deleteCustomerCascade(customerId: Long): Boolean {
        if (!sessionManager.isAdmin) return false

        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()

        return try {
            db.beginTransaction()

            val affected = db.update(
                "musteri",
                ContentValues().apply {
                    put("isDeleted", 1)
                    put("deletedAt", now)
                    put("deletedBy", sessionManager.userId)
                    put("deleteReason", "Müşteri silindi (cascade)")
                    put("updatedAt", now)
                },
                "id=? AND isDeleted=0",
                arrayOf(customerId.toString())
            )

            if (affected == 0) return false

            // ✅ Müşteri silme logu
            writeChangeLog(
                db, "musteri", "DELETE", customerId,
                JSONObject().apply {
                    put("id", customerId)
                    put("isDeleted", 1)
                    put("deletedAt", now)
                    put("deletedBy", sessionManager.userId)
                }.toString()
            )

            // Bağlı siparişleri sil
            db.execSQL(
                """
                UPDATE siparis
                SET isDeleted=1, deletedAt=?, deletedBy=?, deleteReason='Müşteri silindi', updatedAt=?
                WHERE musteriId=? AND isDeleted=0
                """,
                arrayOf(now, sessionManager.userId, now, customerId)
            )

            // Silinen siparişlerin ID'lerini log'a ekle
            val siparisIds = mutableListOf<Long>()
            db.rawQuery("SELECT id FROM siparis WHERE musteriId=? AND isDeleted=1", arrayOf(customerId.toString())).use { c ->
                while (c.moveToNext()) siparisIds.add(c.getLong(0))
            }
            siparisIds.forEach { siparisId ->
                writeChangeLog(
                    db, "siparis", "DELETE", siparisId,
                    JSONObject().apply {
                        put("id", siparisId)
                        put("musteriId", customerId)
                        put("isDeleted", 1)
                        put("deletedAt", now)
                    }.toString()
                )
            }

            // Bağlı ürünleri sil
            db.execSQL(
                """
                UPDATE urun SET isDeleted=1
                WHERE siparisId IN (SELECT id FROM siparis WHERE musteriId=?)
                """,
                arrayOf(customerId)
            )

            db.setTransactionSuccessful()
            Log.d(TAG, "✅ Müşteri cascade silindi + change_log: ID=$customerId (${siparisIds.size} sipariş)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ deleteCustomerCascade", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    // ================================================================
    // ♻️ RESTORE CUSTOMER + CASCADE — change_log yazılır
    // ================================================================
    fun restoreCustomerCascade(customerId: Long): Boolean {
        if (!sessionManager.isAdmin) return false

        val db = dbHelper.writableDatabase

        return try {
            db.beginTransaction()

            val restored = db.update(
                "musteri",
                ContentValues().apply {
                    put("isDeleted", 0)
                    putNull("deletedAt")
                    putNull("deletedBy")
                    putNull("deleteReason")
                    put("updatedAt", System.currentTimeMillis())
                },
                "id=? AND isDeleted=1",
                arrayOf(customerId.toString())
            )

            if (restored == 0) return false

            // ✅ Müşteri restore logu
            writeChangeLog(
                db, "musteri", "UPDATE", customerId,
                JSONObject().apply {
                    put("id", customerId)
                    put("isDeleted", 0)
                    put("updatedAt", System.currentTimeMillis())
                }.toString()
            )

            db.execSQL("UPDATE siparis SET isDeleted=0, updatedAt=? WHERE musteriId=?",
                arrayOf(System.currentTimeMillis(), customerId))

            // Restore edilen siparişlerin logunu ekle
            val siparisIds = mutableListOf<Long>()
            db.rawQuery("SELECT id FROM siparis WHERE musteriId=?", arrayOf(customerId.toString())).use { c ->
                while (c.moveToNext()) siparisIds.add(c.getLong(0))
            }
            siparisIds.forEach { siparisId ->
                writeChangeLog(
                    db, "siparis", "UPDATE", siparisId,
                    JSONObject().apply { put("id", siparisId); put("isDeleted", 0) }.toString()
                )
            }

            db.execSQL(
                """
                UPDATE urun SET isDeleted=0
                WHERE siparisId IN (SELECT id FROM siparis WHERE musteriId=?)
                """,
                arrayOf(customerId)
            )

            db.setTransactionSuccessful()
            Log.d(TAG, "✅ Müşteri restore edildi + change_log: ID=$customerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ restoreCustomerCascade", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    // ================================================================
    // 📋 READ
    // ================================================================
    fun getAllCustomers(): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.isDeleted = 0 ORDER BY m.id DESC
            """, null
        )
        while (cursor.moveToNext()) list.add(mapCustomer(cursor))
        cursor.close()
        return list
    }

    fun getCustomerById(id: Long): Customer? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.id=?
            """,
            arrayOf(id.toString())
        )
        val customer = if (cursor.moveToFirst()) mapCustomer(cursor) else null
        cursor.close()
        return customer
    }

    fun getCustomersWithFirmaInfo(): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam, f.telefon as firmaTelefon
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.isDeleted = 0
            ORDER BY f.firmaAdi, m.adSoyad
            """, null
        )
        while (cursor.moveToNext()) list.add(mapCustomer(cursor))
        cursor.close()
        return list
    }

    // ================================================================
    // 📞 TELEFONA GÖRE BUL (tekil)
    // ================================================================
    fun getCustomerByPhone(phoneNumber: String?): Customer? {
        if (phoneNumber.isNullOrBlank()) return null
        val cleanPhone = phoneNumber
            .replace("\\s".toRegex(), "")
            .replace("-", "").replace("(", "").replace(")", "")
        val db = dbHelper.readableDatabase
        val query = """
            SELECT * FROM musteri 
            WHERE REPLACE(REPLACE(REPLACE(REPLACE(ceptel,' ',''),'-',''),'(',''),')','') LIKE ?
            AND isDeleted = 0 LIMIT 1
        """
        return try {
            db.rawQuery(query, arrayOf("%$cleanPhone%")).use { cursor ->
                if (cursor.moveToFirst()) cursorToCustomer(cursor) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCustomerByPhone hatası", e)
            null
        }
    }

    fun findCustomerByIncomingPhone(incomingPhone: String): Customer? {
        val key10 = PhoneUtils.normalizeKeyTR(incomingPhone)
        if (key10.isBlank()) return null
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, f.firmaAdi as firmaAdiTam 
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.isDeleted = 0
              AND (
                  REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(m.ceptel,' ',''),'-',''),'(',''),')',''),'+','') LIKE ?
                  OR
                  REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(m.ceptel2,' ',''),'-',''),'(',''),')',''),'+','') LIKE ?
              )
            ORDER BY m.id DESC LIMIT 1
            """.trimIndent(),
            arrayOf("%$key10%", "%$key10%")
        )
        val customer = if (cursor.moveToFirst()) mapCustomer(cursor) else null
        cursor.close()
        return customer
    }

    // ================================================================
    // 📊 İSTATİSTİKLER
    // ================================================================
    fun getCustomerStatsByFirma(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT COALESCE(f.firmaAdi, 'Firma Atanmamış') as firmaAdi, COUNT(*) as musteriSayisi
            FROM musteri m LEFT JOIN firma f ON m.firmaid = f.id
            WHERE m.isDeleted = 0
            GROUP BY f.id ORDER BY musteriSayisi DESC
            """, null
        )
        while (cursor.moveToNext()) {
            stats[cursor.getString(0)] = cursor.getInt(1)
        }
        cursor.close()
        return stats
    }

    // ================================================================
    // 🧩 MAPPER
    // ================================================================
    private fun mapCustomer(cursor: Cursor): Customer {
        fun s(c: String) = cursor.getColumnIndex(c).takeIf { it != -1 && !cursor.isNull(it) }?.let { cursor.getString(it) }
        fun l(c: String) = cursor.getColumnIndex(c).takeIf { it != -1 && !cursor.isNull(it) }?.let { cursor.getLong(it) }
        fun d(c: String) = cursor.getColumnIndex(c).takeIf { it != -1 && !cursor.isNull(it) }?.let { cursor.getDouble(it) }

        return Customer(
            id             = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            uuid           = s("uuid") ?: UUID.randomUUID().toString(),
            adSoyad        = cursor.getString(cursor.getColumnIndexOrThrow("adSoyad")),
            ceptel         = s("ceptel"),
            ceptel2        = s("ceptel2"),
            adres          = s("adres"),
            bolge          = s("bolge"),
            musteriNotu    = s("musteriNotu"),
            firmaAdi       = s("firmaAdiTam") ?: s("firmaAdi"),
            firmaid        = l("firmaid"),
            isDeleted      = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")),
            deletedAt      = l("deletedAt"),
            deleteReason   = s("deleteReason"),
            deletedBy      = l("deletedBy"),
            latitude       = d("latitude"),
            longitude      = d("longitude"),
            locationTimestamp = l("locationTimestamp"),
            locationAddress   = s("locationAddress"),
            photoPath         = s("photoPath"),
            updatedAt      = l("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun cursorToCustomer(cursor: Cursor): Customer = mapCustomer(cursor)

    // ================================================================
    // 🔧 ContentValues
    // ================================================================
    private fun buildCustomerContentValues(customer: Customer): ContentValues =
        ContentValues().apply {
            put("adSoyad", customer.adSoyad)
            put("adres", customer.adres)
            put("ceptel", customer.ceptel)
            put("ceptel2", customer.ceptel2)
            put("bolge", customer.bolge)
            put("musteriNotu", customer.musteriNotu)
            put("firmaAdi", customer.firmaAdi)
            if (customer.firmaid != null) put("firmaid", customer.firmaid) else putNull("firmaid")
            put("uuid", customer.uuid.ifBlank { UUID.randomUUID().toString() })
            // 🔑 FIRMA UUID — firmaid'den lookup
            val fid = customer.firmaid
            if (fid != null && fid > 0L) {
                try {
                    dbHelper.readableDatabase.rawQuery(
                        "SELECT uuid FROM firma WHERE id=? LIMIT 1", arrayOf(fid.toString())
                    ).use { fc ->
                        if (fc.moveToFirst()) {
                            val u = fc.getString(0)
                            if (!u.isNullOrBlank()) {
                                put("firma_uuid", u)
                                Log.d(TAG, "🔗 musteri.firma_uuid=$u (firmaid=$fid)")
                            }
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "firma_uuid lookup hata: ${e.message}") }
            } else {
                putNull("firma_uuid")
            }
        }

    /** Eski musteri kayitlari icin firma_uuid backfill. */
    fun backfillMusteriFirmaUuid(): Int {
        val db = dbHelper.writableDatabase
        return try {
            db.execSQL("""UPDATE musteri SET firma_uuid = (SELECT uuid FROM firma WHERE firma.id = musteri.firmaid)
                          WHERE (firma_uuid IS NULL OR firma_uuid = '') AND firmaid IS NOT NULL AND firmaid > 0""")
            val c = db.rawQuery("SELECT changes()", null)
            val n = if (c.moveToFirst()) c.getInt(0) else 0
            c.close()
            Log.i(TAG, "🔧 backfillMusteriFirmaUuid: $n satir")
            n
        } catch (e: Exception) { Log.w(TAG, "backfillMusteriFirmaUuid hata: ${e.message}"); 0 }
    }
}
