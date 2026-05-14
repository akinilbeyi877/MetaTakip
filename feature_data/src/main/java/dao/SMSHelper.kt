package dao

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.SignalStrength
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

class SMSHelper(private val context: Context) {

    companion object {
        const val SMS_SENT_ACTION = "com.example.metatakip.SMS_SENT"
        const val SMS_DELIVERED_ACTION = "com.example.metatakip.SMS_DELIVERED"
        const val REQUEST_CODE_SMS_SENT = 1001
        const val REQUEST_CODE_SMS_DELIVERED = 1002
        private const val TAG = "SMS_HELPER"
    }

    // ==================== PERMISSION HELPERS ====================

    private fun hasSmsPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "🔐 SMS izni kontrolü: $hasPermission")
        return hasPermission
    }

    private fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNetworkStatePermission(): Boolean {
        // normal permission (runtime istemez), ama lint için guard iyi oluyor
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun <T> telephonySafe(block: () -> T): T? {
        return try {
            if (!hasPhoneStatePermission()) return null
            block()
        } catch (se: SecurityException) {
            null
        } catch (t: Throwable) {
            null
        }
    }

    // ==================== SİNYAL GÜCÜ FONKSİYONLARI ====================

    private fun getSignalDbm(signalStrength: SignalStrength?): Int {
        return try {
            when {
                signalStrength == null -> -1

                // Android 29+ için reflection ile getDbm()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    try {
                        val method = signalStrength.javaClass.getMethod("getDbm")
                        method.invoke(signalStrength) as Int
                    } catch (e: Exception) {
                        getSignalDbmFromCellStrengths(signalStrength)
                    }
                }

                // Android 23+ için cell strengths
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    getSignalDbmFromCellStrengths(signalStrength)
                }

                else -> {
                    try {
                        val method = signalStrength.javaClass.getMethod("getDbm")
                        method.invoke(signalStrength) as Int
                    } catch (e: Exception) {
                        val gsm = signalStrength.gsmSignalStrength
                        if (gsm != 99 && gsm != 0) (gsm * 2) - 113 else -1
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "dBm alınamadı: ${e.message}")
            -1
        }
    }

    @SuppressLint("NewApi")
    private fun getSignalDbmFromCellStrengths(signalStrength: SignalStrength): Int {
        return try {
            val lteStrengths =
                signalStrength.getCellSignalStrengths(CellSignalStrengthLte::class.java)
            if (lteStrengths.isNotEmpty()) return lteStrengths[0].dbm

            val wcdmaStrengths =
                signalStrength.getCellSignalStrengths(CellSignalStrengthWcdma::class.java)
            if (wcdmaStrengths.isNotEmpty()) return wcdmaStrengths[0].dbm

            val gsmStrengths =
                signalStrength.getCellSignalStrengths(CellSignalStrengthGsm::class.java)
            if (gsmStrengths.isNotEmpty()) return gsmStrengths[0].dbm

            val cdmaStrengths =
                signalStrength.getCellSignalStrengths(CellSignalStrengthCdma::class.java)
            if (cdmaStrengths.isNotEmpty()) return cdmaStrengths[0].dbm

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val nrStrengths =
                    signalStrength.getCellSignalStrengths(CellSignalStrengthNr::class.java)
                if (nrStrengths.isNotEmpty()) return nrStrengths[0].dbm
            }

            -1
        } catch (e: Exception) {
            Log.e(TAG, "Hücre sinyallerinden dBm alınamadı", e)
            -1
        }
    }

    private fun checkSignalStrength(): Pair<Boolean, String> {
        return try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // ✅ API 28 altı: telephonyManager.signalStrength yok → null kullan
            val signalStrength: SignalStrength? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    telephonySafe { telephonyManager.signalStrength }
                } else {
                    null
                }

            if (signalStrength == null) {
                Log.w(TAG, "📶 Sinyal gücü alınamıyor (API<28 veya izin yok)")
                return Pair(true, "Sinyal gücü alınamıyor") // uyarı ama devam et
            }

            val signalLevel = signalStrength.level
            val dBm = getSignalDbm(signalStrength)

            Log.d(TAG, "📶 Sinyal seviyesi: $signalLevel, dBm: $dBm")

            when {
                signalLevel >= 1 -> {
                    val quality = when (signalLevel) {
                        4 -> "Mükemmel"
                        3 -> "İyi"
                        2 -> "Orta"
                        1 -> "Zayıf"
                        else -> "Bilinmeyen"
                    }
                    Pair(true, "$quality sinyal (Seviye: $signalLevel, dBm: $dBm)")
                }

                signalLevel == 0 -> Pair(true, "Sinyal çok zayıf (Seviye: $signalLevel)")
                else -> Pair(true, "Sinyal kontrol edilemedi")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Sinyal kontrol hatası: ${e.message}", e)
            Pair(true, "Sinyal kontrol hatası")
        }
    }

    // ==================== ŞEBEKE KONTROL FONKSİYONLARI ====================

    fun checkNetworkAndSimStatus(): Pair<Boolean, String> {
        val networkMessage = StringBuilder()

        val simStatus = checkSimCardStatus()
        if (!simStatus.first) {
            networkMessage.append("❌ SIM kart: ").append(simStatus.second)
            return Pair(false, networkMessage.toString())
        }

        val signalStatus = checkSignalStrength()
        networkMessage.append("📶 ").append(signalStatus.second)

        val networkStatus = checkNetworkConnection()
        if (!networkStatus.first) {
            networkMessage.append("\n⚠️ Ağ bağlantısı: ").append(networkStatus.second)
        }

        val dataStatus = checkCellularData()
        if (!dataStatus.first) {
            networkMessage.append("\nℹ️ ").append(dataStatus.second)
        }

        return Pair(true, networkMessage.toString())
    }

    private fun checkSimCardStatus(): Pair<Boolean, String> {
        return try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val simState = telephonySafe { telephonyManager.simState } ?: telephonyManager.simState

            val simStateText = when (simState) {
                TelephonyManager.SIM_STATE_ABSENT -> "SIM kart takılı değil"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM PIN gerekiyor"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM PUK gerekiyor"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "SIM ağ kilidi var"
                TelephonyManager.SIM_STATE_PERM_DISABLED -> "SIM kalıcı olarak devre dışı"
                TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "SIM kart I/O hatası"
                TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "SIM kart kısıtlı"
                TelephonyManager.SIM_STATE_READY -> "SIM kart hazır"
                else -> "Bilinmeyen SIM durumu"
            }

            Log.d(TAG, "📱 SIM durumu: $simStateText ($simState)")

            if (simState != TelephonyManager.SIM_STATE_READY) {
                Pair(false, simStateText)
            } else {
                Pair(true, "SIM kart hazır")
            }

        } catch (e: Exception) {
            Log.e(TAG, "SIM kontrol hatası", e)
            Pair(false, "SIM kart kontrol edilemedi")
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkNetworkConnection(): Pair<Boolean, String> {
        return try {
            if (!hasNetworkStatePermission()) {
                return Pair(true, "Ağ izni yok (ACCESS_NETWORK_STATE)") // engel olma
            }

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                    ?: return Pair(false, "Aktif ağ bağlantısı yok")

                val capabilities = connectivityManager.getNetworkCapabilities(network)
                    ?: return Pair(false, "Ağ özellikleri alınamadı")

                val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                if (hasCellular) {
                    Pair(true, "Hücresel ağ bağlantısı var")
                } else {
                    val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                    if (hasWifi || hasEthernet) {
                        Pair(true, "Hücresel ağ yok ama internet bağlantısı var")
                    } else {
                        Pair(false, "Hiçbir ağ bağlantısı yok")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                if (networkInfo != null && networkInfo.isConnected) {
                    Pair(true, "Ağ bağlantısı var")
                } else {
                    Pair(false, "Ağ bağlantısı yok")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ağ kontrol hatası", e)
            Pair(true, "Ağ kontrol edilemedi")
        }
    }

    private fun checkCellularData(): Pair<Boolean, String> {
        return try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // isDataEnabled API 28+; ayrıca SecurityException yiyebilir
            val isDataEnabled: Boolean? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    telephonySafe { telephonyManager.isDataEnabled }
                } else {
                    null
                }

            when (isDataEnabled) {
                true -> Pair(true, "Mobil veri aktif")
                false -> Pair(false, "Mobil veri kapalı")
                null -> Pair(true, "Mobil veri durumu kontrol edilemedi")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Mobil veri kontrol hatası", e)
            Pair(true, "Mobil veri kontrol edilemedi")
        }
    }

    // ==================== SMS GÖNDERME FONKSİYONLARI ====================

    fun sendSMS(phoneNumber: String, message: String): Boolean {
        Log.d(TAG, "📨 SMS gönderimi başlıyor: $phoneNumber")
        Log.d(TAG, "📝 Mesaj uzunluğu: ${message.length}")

        val cleanedNumber = cleanPhoneNumber(phoneNumber)
        if (!isValidPhoneNumber(cleanedNumber)) {
            Log.e(TAG, "❌ Geçersiz telefon numarası: $cleanedNumber")
            Toast.makeText(context, "❌ Geçersiz telefon numarası", Toast.LENGTH_LONG).show()
            return false
        }

        if (!hasSmsPermission()) {
            Log.e(TAG, "❌ SMS izni yok")
            Toast.makeText(context, "📱 SMS gönderme izni gerekli", Toast.LENGTH_LONG).show()
            return false
        }

        val networkStatus = checkNetworkAndSimStatus()
        Log.d(TAG, "📶 Şebeke durumu: ${networkStatus.second}")

        try {
            val smsManager = getSmsManager()
            val messageParts = smsManager.divideMessage(message)
            Log.d(TAG, "📊 Mesaj parça sayısı: ${messageParts.size}")

            if (messageParts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    cleanedNumber,
                    null,
                    messageParts,
                    null,
                    null
                )
                Log.d(TAG, "📤 Çok parçalı SMS gönderildi")
            } else {
                smsManager.sendTextMessage(
                    cleanedNumber,
                    null,
                    message,
                    null,
                    null
                )
                Log.d(TAG, "📤 Tek parçalı SMS gönderildi")
            }

            Log.d(TAG, "✅ SMS gönderim isteği başarılı: $phoneNumber")
            Toast.makeText(context, "✅ SMS gönderildi", Toast.LENGTH_SHORT).show()
            return true

        } catch (e: SecurityException) {
            Log.e(TAG, "🔒 SMS güvenlik izni hatası", e)
            Toast.makeText(context, "❌ SMS gönderme izni reddedildi", Toast.LENGTH_LONG).show()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ SMS gönderme hatası: ${e.message}", e)
            Toast.makeText(context, "❌ SMS gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun sendSimpleSMS(phoneNumber: String, message: String): Boolean {
        Log.d(TAG, "📨 Basit SMS gönderimi: $phoneNumber")

        val cleanedNumber = cleanPhoneNumber(phoneNumber)
        if (!isValidPhoneNumber(cleanedNumber)) return false
        if (!hasSmsPermission()) return false

        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(cleanedNumber, null, message, null, null)
            Log.d(TAG, "✅ Basit SMS gönderildi")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Basit SMS hatası", e)
            false
        }
    }

    // ==================== YARDIMCI FONKSİYONLAR ====================

    private fun cleanPhoneNumber(number: String): String {
        var cleaned = number.replace("[^0-9+]".toRegex(), "")

        cleaned = when {
            cleaned.startsWith("90") && cleaned.length == 12 -> cleaned
            cleaned.startsWith("0") && cleaned.length == 11 -> "9$cleaned"
            cleaned.startsWith("+90") && cleaned.length == 13 -> cleaned.substring(1)
            else -> {
                if (cleaned.length == 10 && cleaned.startsWith("5")) "90$cleaned" else cleaned
            }
        }

        Log.d(TAG, "📱 Telefon temizleme: $number -> $cleaned")
        return cleaned
    }

    private fun isValidPhoneNumber(number: String): Boolean {
        val valid = when {
            number.startsWith("90") && number.length == 12 -> true
            number.startsWith("+90") && number.length == 13 -> true
            number.startsWith("0") && number.length == 11 -> true
            number.startsWith("5") && number.length == 10 -> true
            number.startsWith("+") && number.length >= 10 -> true
            number.length >= 10 && number.all { it.isDigit() } -> true
            else -> false
        }

        Log.d(TAG, "🔍 Telefon geçerlilik: $number -> $valid")
        return valid
    }

    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    fun openDefaultSmsApp(phoneNumber: String, message: String = ""): Boolean {
        return try {
            Log.d(TAG, "📱 Varsayılan SMS uygulaması açılıyor: $phoneNumber")

            val uri = Uri.parse("smsto:$phoneNumber")
            val intent = Intent(Intent.ACTION_SENDTO, uri)

            if (message.isNotEmpty()) {
                intent.putExtra("sms_body", message)
                Log.d(TAG, "📝 Mesaj eklendi: $message")
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Toast.makeText(context, "📱 SMS uygulaması açılıyor...", Toast.LENGTH_SHORT).show()
                true
            } else {
                Log.e(TAG, "❌ SMS uygulaması bulunamadı")
                Toast.makeText(context, "❌ SMS uygulaması bulunamadı", Toast.LENGTH_LONG).show()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ SMS uygulaması açma hatası", e)
            Toast.makeText(context, "❌ SMS uygulaması açılamadı", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun getNetworkStatusReport(): String {
        val report = StringBuilder()
        report.append("📱 Şebeke Durumu Raporu\n")
        report.append("======================\n")

        val simStatus = checkSimCardStatus()
        report.append("SIM Kart: ${simStatus.second}\n")

        val signalStatus = checkSignalStrength()
        report.append("Sinyal: ${signalStatus.second}\n")

        val networkStatus = checkNetworkConnection()
        report.append("Ağ Bağlantısı: ${networkStatus.second}\n")

        val dataStatus = checkCellularData()
        report.append("Mobil Veri: ${dataStatus.second}\n")

        report.append("SMS İzni: ${if (hasSmsPermission()) "VAR" else "YOK"}\n")

        return report.toString()
    }

    fun getNetworkType(): String {
        return try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val type = telephonySafe { telephonyManager.dataNetworkType }
                ?: TelephonyManager.NETWORK_TYPE_UNKNOWN

            when (type) {
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
                TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
                TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
                TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
                TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
                TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
                TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
                TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
                TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
                else -> "Bilinmeyen"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Şebeke türü alınamadı", e)
            "Bilinmeyen"
        }
    }
}