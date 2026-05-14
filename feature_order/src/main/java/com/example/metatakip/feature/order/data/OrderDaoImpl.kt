package com.example.metatakip.feature.order.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.example.metatakip.feature_data.db.MetaTakipDb
import com.example.metatakip.feature_data.entityModel.Order
import com.example.metatakip.feature_data.entityModel.SessionManager

class OrderDaoImpl(private val context: Context) : OrderDaoInterface {

    private val dbHelper = MetaTakipDb.getInstance(context)
    private val sessionManager = SessionManager(context)
    private val tag = "OrderDaoImpl"

    private val baseSelect = """
    SELECT
        s.*,
        COALESCE(m.adSoyad, '') AS musteriAdi,
        COALESCE(m.ceptel, '') AS musteriTelefon,
        COALESCE(m.uuid, '') AS musteriUuid,
        COALESCE(s.firma_uuid, f.uuid, '') AS firmaUuid,
        COALESCE(f.firmaAdi, m.firmaAdi, '') AS firmaAdi,
        COALESCE(m.adres, m.bolge, '') AS musteriAdres,
        (SELECT COALESCE(SUM(adet), 0) FROM urun WHERE siparisId = s.id AND (isDeleted = 0 OR isDeleted IS NULL)) AS toplamAdet
    FROM siparis s
    LEFT JOIN musteri m ON m.id = s.musteriId
    LEFT JOIN firma f ON f.id = COALESCE(s.firmaid, m.firmaid)
    """.trimIndent()

    override fun getAllOrder(): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("$baseSelect WHERE s.isDeleted=0 ORDER BY s.id DESC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(mapOrder(cursor))
                }
            }
            list
        } catch (e: Exception) {
            Log.e(tag, "getAllOrder hata: ${e.message}")
            emptyList()
        }
    }

    override fun getOrderById(id: Long): Order? {
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("$baseSelect WHERE s.id=? LIMIT 1", arrayOf(id.toString())).use { cursor ->
                if (cursor.moveToFirst()) mapOrder(cursor) else null
            }
        } catch (e: Exception) { null }
    }

    override fun addOrder(order: Order): Long {
        val db = dbHelper.writableDatabase
        return try {
            enrichOrderUuids(db, order)
            val values = buildContentValues(order)
            db.insert("siparis", null, values)
        } catch (e: Exception) { -1L }
    }

    override fun updateOrderById(id: Long, order: Order): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            enrichOrderUuids(db, order)
            val values = buildContentValues(order)
            db.update("siparis", values, "id=?", arrayOf(id.toString())) > 0
        } catch (e: Exception) { false }
    }

    /** Form sadece id'leri verirse, firma/musteri uuid'lerini lookup ederek doldur. */
    private fun enrichOrderUuids(db: android.database.sqlite.SQLiteDatabase, order: Order) {
        if (order.firmaUuid.isBlank() && order.firmaId > 0L) {
            try {
                db.rawQuery("SELECT uuid FROM firma WHERE id=? LIMIT 1", arrayOf(order.firmaId.toString())).use { c ->
                    if (c.moveToFirst()) {
                        val u = c.getString(0); if (!u.isNullOrBlank()) order.firmaUuid = u
                    }
                }
            } catch (_: Exception) {}
        }
        if (order.musteriUuid.isBlank() && order.musteriId > 0L) {
            try {
                db.rawQuery("SELECT uuid FROM musteri WHERE id=? LIMIT 1", arrayOf(order.musteriId.toString())).use { c ->
                    if (c.moveToFirst()) {
                        val u = c.getString(0); if (!u.isNullOrBlank()) order.musteriUuid = u
                    }
                }
            } catch (_: Exception) {}
        }
        android.util.Log.d("OrderDao", "🔗 enrich: firmaId=${order.firmaId} firmaUuid=${order.firmaUuid} musteriId=${order.musteriId} musteriUuid=${order.musteriUuid}")
    }

    /** Eski siparis kayitlari icin firma_uuid + musteri_uuid backfill. */
    fun backfillSiparisUuids(): Int {
        val db = dbHelper.writableDatabase
        return try {
            db.execSQL("""UPDATE siparis SET firma_uuid = (SELECT uuid FROM firma WHERE firma.id = siparis.firmaid)
                          WHERE (firma_uuid IS NULL OR firma_uuid = '') AND firmaid IS NOT NULL AND firmaid > 0""")
            db.execSQL("""UPDATE siparis SET musteri_uuid = (SELECT uuid FROM musteri WHERE musteri.id = siparis.musteriId)
                          WHERE (musteri_uuid IS NULL OR musteri_uuid = '') AND musteriId IS NOT NULL AND musteriId > 0""")
            android.util.Log.i("OrderDao", "🔧 backfillSiparisUuids tamamlandi")
            1
        } catch (e: Exception) { android.util.Log.w("OrderDao", "backfillSiparisUuids hata: ${e.message}"); 0 }
    }

    override fun updateOrderDurumu(orderId: Long, yeniDurum: String, teslimTarihi: String): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply {
                put("durum", yeniDurum)
                if (teslimTarihi.isNotEmpty()) put("teslimTarihi", teslimTarihi)
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("siparis", values, "id=?", arrayOf(orderId.toString())) > 0
        } catch (e: Exception) { false }
    }

    override fun softDeleteOrder(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply {
                put("isDeleted", 1)
                put("deletedAt", System.currentTimeMillis())
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("siparis", values, "id=?", arrayOf(id.toString())) > 0
        } catch (e: Exception) { false }
    }

    override fun restoreOrder(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply {
                put("isDeleted", 0)
                putNull("deletedAt")
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("siparis", values, "id=?", arrayOf(id.toString())) > 0
        } catch (e: Exception) { false }
    }

    override fun getDeletedOrderCount(): Int {
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("SELECT COUNT(*) FROM siparis WHERE isDeleted=1", null).use { 
                if (it.moveToFirst()) it.getInt(0) else 0 
            }
        } catch (e: Exception) { 0 }
    }

    override fun getDeletedOrder(): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("$baseSelect WHERE s.isDeleted=1 ORDER BY s.deletedAt DESC", null).use { cursor ->
                while (cursor.moveToNext()) { list.add(mapOrder(cursor)) }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override fun getAllOrderIncludingDeleted(): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        return try {
            db.rawQuery("$baseSelect ORDER BY s.id DESC", null).use { cursor ->
                while (cursor.moveToNext()) { list.add(mapOrder(cursor)) }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override fun getOrdersByMusteriUuid(uuid: String, showAll: Boolean): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        val query = if (showAll) "$baseSelect WHERE m.uuid=? ORDER BY s.id DESC"
                    else "$baseSelect WHERE m.uuid=? AND s.isDeleted=0 ORDER BY s.id DESC"
        return try {
            db.rawQuery(query, arrayOf(uuid)).use { cursor ->
                while (cursor.moveToNext()) { list.add(mapOrder(cursor)) }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override fun getOrdersByMusteriAdiVeTel(adi: String, tel: String, showAll: Boolean): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        val query = if (showAll) "$baseSelect WHERE m.adSoyad LIKE ? AND m.ceptel LIKE ? ORDER BY s.id DESC"
                    else "$baseSelect WHERE m.adSoyad LIKE ? AND m.ceptel LIKE ? AND s.isDeleted=0 ORDER BY s.id DESC"
        return try {
            db.rawQuery(query, arrayOf("%$adi%", "%$tel%")).use { cursor ->
                while (cursor.moveToNext()) { list.add(mapOrder(cursor)) }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override fun getOrdersByTelefon(tel: String, showAll: Boolean): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        val query = if (showAll) "$baseSelect WHERE m.ceptel LIKE ? ORDER BY s.id DESC"
                    else "$baseSelect WHERE m.ceptel LIKE ? AND s.isDeleted=0 ORDER BY s.id DESC"
        return try {
            db.rawQuery(query, arrayOf("%$tel%")).use { cursor ->
                while (cursor.moveToNext()) { list.add(mapOrder(cursor)) }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override fun getOrdersByMusteriAdi(adi: String, showAll: Boolean): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        val query = if (showAll) "$baseSelect WHERE m.adSoyad LIKE ? ORDER BY s.id DESC"
                    else "$baseSelect WHERE m.adSoyad LIKE ? AND s.isDeleted=0 ORDER BY s.id DESC"
        return try {
            db.rawQuery(query, arrayOf("%$adi%")).use { cursor ->
                while (cursor.moveToNext()) { list.add(mapOrder(cursor)) }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override fun getOrdersByDurum(durum: String): List<Order> {
        val list = mutableListOf<Order>()
        val db = dbHelper.readableDatabase
        val query = "$baseSelect WHERE s.durum=? AND s.isDeleted=0 ORDER BY s.id DESC"
        return try {
            db.rawQuery(query, arrayOf(durum)).use { cursor ->
                while (cursor.moveToNext()) { list.add(mapOrder(cursor)) }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    override fun updateOrderPhoto(orderId: Long, photoPath: String): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val v = ContentValues().apply {
                put("photoPath", photoPath)
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("siparis", v, "id=?", arrayOf(orderId.toString())) > 0
        } catch (e: Exception) { false }
    }

    // ============================================================
    // 📦 ÜRÜN METOTLARI
    // ============================================================

    fun addUrun(
        siparisId: Long,
        ad: String,
        urunTipi: String = "",
        adet: Int = 1,
        m2: Double = 0.0,
        fiyat: Double = 0.0,
        tutar: Double = 0.0
    ): Long {
        val db = dbHelper.writableDatabase
        return try {
            // 🔗 KURESEL BAG: siparisin uuid'sini DB'den cek
            val siparisUuid = lookupSiparisUuidLocal(db, siparisId)
            // 🌍 URUN UUID
            val urunUuid = java.util.UUID.randomUUID().toString()

            val values = ContentValues().apply {
                put("uuid", urunUuid)
                put("siparisId", siparisId)
                put("siparis_uuid", siparisUuid)
                put("ad", ad)
                put("urunTipi", urunTipi)
                put("adet", adet)
                put("m2", m2)
                put("fiyat", fiyat)
                put("tutar", tutar)
                put("isDeleted", 0)
                put("updatedAt", System.currentTimeMillis())
            }
            val id = db.insert("urun", null, values)
            android.util.Log.i("OrderDaoImpl", "✅ addUrun id=$id uuid=$urunUuid siparisId=$siparisId siparisUuid=$siparisUuid")
            id
        } catch (e: Exception) {
            android.util.Log.e("OrderDaoImpl", "addUrun hata", e); -1L
        }
    }

    private fun lookupSiparisUuidLocal(db: android.database.sqlite.SQLiteDatabase, siparisId: Long): String {
        if (siparisId <= 0L) return ""
        return try {
            val c = db.rawQuery("SELECT uuid FROM siparis WHERE id = ? LIMIT 1", arrayOf(siparisId.toString()))
            val u = if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            c.close()
            u
        } catch (_: Exception) { "" }
    }

    fun updateUrunById(
        urunId: Long,
        siparisId: Long,
        ad: String,
        urunTipi: String = "",
        adet: Int = 1,
        m2: Double = 0.0,
        fiyat: Double = 0.0,
        tutar: Double = 0.0
    ): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply {
                put("siparisId", siparisId)
                put("ad", ad)
                put("urunTipi", urunTipi)
                put("adet", adet)
                put("m2", m2)
                put("fiyat", fiyat)
                put("tutar", tutar)
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("urun", values, "id=?", arrayOf(urunId.toString())) > 0
        } catch (e: Exception) { false }
    }

    fun softDeleteUrun(urunId: Long): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            val values = ContentValues().apply { 
                put("isDeleted", 1) 
                put("updatedAt", System.currentTimeMillis())
            }
            db.update("urun", values, "id=?", arrayOf(urunId.toString())) > 0
        } catch (e: Exception) { false }
    }

    private fun mapOrder(cursor: Cursor): Order {
        fun s(col: String): String = try { 
            val idx = cursor.getColumnIndex(col)
            if (idx != -1) cursor.getString(idx) ?: "" else ""
        } catch (e: Exception) { "" }
        
        fun d(col: String): Double = try { 
            val idx = cursor.getColumnIndex(col)
            if (idx != -1) cursor.getDouble(idx) else 0.0
        } catch (e: Exception) { 0.0 }
        
        fun l(col: String): Long = try { 
            val idx = cursor.getColumnIndex(col)
            if (idx != -1) cursor.getLong(idx) else 0L
        } catch (e: Exception) { 0L }
        
        fun i(col: String): Int = try { 
            val idx = cursor.getColumnIndex(col)
            if (idx != -1) cursor.getInt(idx) else 0
        } catch (e: Exception) { 0 }

        return Order().apply {
            id = l("id")
            uuid = s("uuid")
            musteriId = l("musteriId")
            firmaId = l("firmaid")
            musteriAdi = s("musteriAdi")
            musteriTelefon = s("musteriTelefon")
            musteriUuid = s("musteriUuid")
            firmaUuid = s("firmaUuid")
            firmaAdi = s("firmaAdi")
            urunTipi = s("urunTipi")
            durum = s("durum")
            metrekare = d("metrekare")
            ucret = d("ucret")
            indirim = d("indirim")
            ekUcret = d("ekUcret")
            notlar = s("notlar")
            yetkili = s("yetkili")
            duzenlemeTarihi = s("duzenlemeTarihi")
            teslimAlmaTarihi = s("teslimAlmaTarihi")
            teslimTarihi = s("teslimTarihi")
            photoPath = s("photoPath")
            isDeleted = i("isDeleted")
            toplamAdet = i("toplamAdet")
            adres = s("musteriAdres")
            siparisNoSeq = l("siparis_no_seq")
            // createdAt DB'de saniye cinsinden gelir, milisaniyeye çeviriyoruz
            val rawCreatedAt = l("createdAt")
            createdAt = if (rawCreatedAt > 0L) {
                // DB'den gelen değer saniye mi milisaniye mi? Milisaniye ise 13 basamak (>1e12)
                if (rawCreatedAt > 1_000_000_000_000L) rawCreatedAt else rawCreatedAt * 1000L
            } else 0L
        }
    }

    private fun buildContentValues(order: Order): ContentValues = ContentValues().apply {
        put("uuid", order.uuid)
        put("musteriId", order.musteriId)
        put("firmaid", if (order.firmaId > 0) order.firmaId else null)
        // 🔑 UUID bağlantıları — senkronizasyon için kritik
        if (order.musteriUuid.isNotBlank()) put("musteri_uuid", order.musteriUuid)
        if (order.firmaUuid.isNotBlank()) put("firma_uuid", order.firmaUuid)
        put("durum", order.durum)
        put("urunTipi", order.urunTipi)
        put("ucret", order.ucret)
        put("metrekare", order.metrekare)
        put("en", order.en)
        put("boy", order.boy)
        put("indirim", order.indirim)
        put("ekUcret", order.ekUcret)
        put("notlar", order.notlar)
        put("yetkili", order.yetkili)
        // 📅 Tarih alanları — dd/MM/yyyy formatında TEXT
        put("duzenlemeTarihi", order.duzenlemeTarihi)
        put("teslimAlmaTarihi", order.teslimAlmaTarihi)
        put("teslimTarihi", order.teslimTarihi)
        put("isDeleted", order.isDeleted)
        put("updatedAt", System.currentTimeMillis())
        // 🌐 user_id sadece audit icin — sira numarasi kuresel hesaplaniyor
        try {
            val ctx = com.example.metatakip.feature_data.db.MetaTakipDb.appContext
            if (ctx != null) {
                val sm = com.example.metatakip.feature_data.entityModel.SessionManager(ctx)
                val uid = if (sm.currentUserId > 0L) sm.currentUserId else sm.userId
                put("user_id", uid)
            }
        } catch (_: Exception) {}
    }

    // Geriye dönük uyumluluk
    fun getAllSiparis() = getAllOrder()
    fun getAllOrders() = getAllOrder()
    fun getAllSiparisIncludingDeleted() = getAllOrderIncludingDeleted()
    fun getSiparisById(id: Long) = getOrderById(id)
    fun getDeletedSiparis() = getDeletedOrder()
    fun restoreSiparis(id: Long) = restoreOrder(id)
    fun addSiparis(order: Order) = addOrder(order)
    fun updateSiparisById(id: Long, order: Order) = updateOrderById(id, order)
    fun softDeleteSiparis(id: Long) = softDeleteOrder(id)
}
