package com.example.metatakip.controllers.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.metatakip.controllers.allGenericFormAndList.GenericFormActivity
import com.example.metatakip.controllers.allGenericFormAndList.GenericListActivity
import com.example.metatakip.feature.order.data.OrderDaoImpl
import com.example.metatakip.feature_data.entityModel.Order
import kotlinx.coroutines.*

/**
 * 📦 OrderNotificationService
 *
 * Yeni Sipariş durumundaki siparişleri 10 saniyede bir kontrol eder.
 * Bildirim gönderilen sipariş ID'lerini SharedPreferences'a kaydeder:
 *   - Uygulama kapanıp açılsa bile aynı sipariş için tekrar bildirim gönderilmez.
 *   - Servis ilk başladığında mevcut tüm siparişleri "zaten görüldü" olarak işaretler
 *     (spam önleme); sadece bundan sonra gelen YENI siparişler bildirim tetikler.
 */
class OrderNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHECK_INTERVAL = 10_000L

    // RAM içi set — SharedPreferences'tan beslenir
    private val notifiedIds = mutableSetOf<Long>()
    private var isFirstCheck = true

    companion object {
        private const val CHANNEL_ID    = "order_monitor_channel"
        private const val FG_CHANNEL_ID = "order_service_status"   // sessiz — sadece foreground zorunluluğu
        private const val FG_NOTIF_ID   = 1001
        private const val PREFS_NAME    = "order_notif_prefs"
        private const val KEY_IDS       = "notified_ids"
        private const val TAG           = "OrderNotificationService"

        fun start(context: Context) {
            val intent = Intent(context, OrderNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OrderNotificationService::class.java))
        }
    }

    // ─────────────────────────────────────────────
    // Yaşam döngüsü
    // ─────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createForegroundChannel()
        startForeground(FG_NOTIF_ID, createForegroundNotification())
        loadNotifiedIds()
        startOrderMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "Servis durduruldu")
    }

    // ─────────────────────────────────────────────
    // SharedPreferences — kalıcı ID yönetimi
    // ─────────────────────────────────────────────

    private fun loadNotifiedIds() {
        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IDS, "") ?: ""
        if (raw.isNotBlank()) {
            raw.split(",").mapNotNull { it.trim().toLongOrNull() }
                .forEach { notifiedIds.add(it) }
        }
        Log.d(TAG, "Kalıcı bildirim kaydı yüklendi: ${notifiedIds.size} sipariş")
    }

    private fun persistNotifiedId(id: Long) {
        notifiedIds.add(id)
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IDS, notifiedIds.joinToString(","))
            .apply()
    }

    /**
     * Durumu değişen siparişleri (artık "Yeni Sipariş" olmayan) setten temizler.
     * Set'in süresiz büyümesini önler.
     */
    private fun pruneNotifiedIds(activeYeniIds: Set<Long>) {
        val toRemove = notifiedIds.filter { it !in activeYeniIds }
        if (toRemove.isEmpty()) return
        notifiedIds.removeAll(toRemove.toSet())
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IDS, notifiedIds.joinToString(","))
            .apply()
        Log.d(TAG, "Temizlenen eski ID sayısı: ${toRemove.size}")
    }

    // ─────────────────────────────────────────────
    // Sipariş izleme döngüsü
    // ─────────────────────────────────────────────

    private fun startOrderMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    checkNewOrders()
                } catch (e: Exception) {
                    Log.e(TAG, "Kontrol hatası: ${e.message}")
                }
                delay(CHECK_INTERVAL)
            }
        }
    }

    private suspend fun checkNewOrders() = withContext(Dispatchers.IO) {
        val orders = OrderDaoImpl(this@OrderNotificationService)
            .getAllSiparis()
            .filter { it.isDeleted == 0 && it.durum == "Yeni Sipariş" }

        val activeIds = orders.map { it.id }.toSet()

        if (isFirstCheck) {
            // ✋ İlk kontrol: mevcut tüm siparişleri "zaten bildirildi" say — spam önleme
            orders.forEach { persistNotifiedId(it.id) }
            isFirstCheck = false
            Log.d(TAG, "İlk kontrol tamamlandı — ${orders.size} mevcut sipariş kaydedildi, bildirim gönderilmedi")
            return@withContext
        }

        // Eski ID'leri temizle (hafıza şişmesini önle)
        pruneNotifiedIds(activeIds)

        // Sadece daha önce bildirilmemiş siparişler için bildirim gönder
        orders.forEach { order ->
            if (!notifiedIds.contains(order.id)) {
                sendOrderNotification(order)
                persistNotifiedId(order.id)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Bildirim oluşturma
    // ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sipariş Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni sipariş bildirimleri"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                FG_CHANNEL_ID,
                "Sipariş Servis Durumu",
                NotificationManager.IMPORTANCE_MIN          // bildirim çekmecesinde görünmez
            ).apply {
                description = "Arka plan servis göstergesi"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun createForegroundNotification(): Notification =
        NotificationCompat.Builder(this, FG_CHANNEL_ID)    // sessiz kanal
            .setContentTitle("Sipariş Takip Aktif")
            .setContentText("Yeni siparişler izleniyor...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setPriority(NotificationCompat.PRIORITY_MIN)   // bildirim çekmecesinde gözükmez
            .setSilent(true)
            .build()

    private fun sendOrderNotification(order: Order) {
        val nm = getSystemService(NotificationManager::class.java)

        // Tıklayınca → OrderPopupActivity
        val popupPi = PendingIntent.getActivity(
            this,
            order.id.toInt(),
            Intent(this, OrderPopupActivity::class.java).apply {
                putExtra("order_id", order.id)
                putExtra("musteri_adi", order.musteriAdi)
                putExtra("musteri_telefon", order.musteriTelefon)
                putExtra("urun_tipi", order.urunTipi)
                putExtra("metrekare", order.metrekare)
                putExtra("ucret", order.ucret)
                putExtra("firma_adi", order.firmaAdi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aksiyon: Tüm siparişler listesi
        val listPi = PendingIntent.getActivity(
            this,
            order.id.toInt() + 1000,
            Intent(this, GenericListActivity::class.java).apply {
                putExtra("listType", "siparis")
                putExtra("filterDurum", "Yeni Sipariş")
                putExtra("pageTitle", "🛒 ALINACAK SİPARİŞLER")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aksiyon: Sipariş düzenle
        val editPi = PendingIntent.getActivity(
            this,
            order.id.toInt() + 2000,
            Intent(this, GenericFormActivity::class.java).apply {
                putExtra("targetTable", "siparis")
                putExtra("edit_mode", true)
                putExtra("id", order.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = buildString {
            append("👤 Müşteri: ${order.musteriAdi}\n")
            append("📞 Telefon: ${order.musteriTelefon}\n")
            if (order.urunTipi.isNotBlank()) append("📦 Ürün: ${order.urunTipi}\n")
            if (order.metrekare > 0) append("📐 M²: ${String.format("%.2f", order.metrekare)}\n")
            if (order.ucret > 0) append("💰 Ücret: ${String.format("%.2f", order.ucret)} ₺\n")
            append("🏢 Firma: ${order.firmaAdi}")
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📦 YENİ SİPARİŞ!")
            .setContentText("${order.musteriAdi} — ${order.firmaAdi}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .addAction(android.R.drawable.ic_menu_agenda, "Tüm Siparişler", listPi)
            .addAction(android.R.drawable.ic_menu_edit, "Sipariş Düzenle", editPi)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(popupPi)
            .setAutoCancel(true)
            .build()

        nm.notify(order.id.toInt(), notification)
        Log.d(TAG, "📢 Bildirim: Sipariş #${order.id} — ${order.musteriAdi}")

        // Bildirim kaydına ekle
        NotificationLogManager.add(
            this,
            NotificationLogEntry(
                orderId    = order.id,
                musteriAdi = order.musteriAdi,
                firmaAdi   = order.firmaAdi,
                urunTipi   = order.urunTipi,
                tarih      = System.currentTimeMillis()
            )
        )
    }
}
