package com.example.metatakip.feature_backup.firebase

import android.content.Context
import android.util.Log
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging

/**
 * 📡 Firebase Token Kayıt Yöneticisi
 * Cihazın anlık bildirim (FCM) adresini Firestore'a mühürler.
 */
object FirebaseTokenRegistrar {
    private const val TAG = "FirebaseTokenRegistrar"

    fun register(context: Context) {
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (token.isNullOrBlank()) return@addOnSuccessListener

                    val groupId = SyncGroupResolver.resolve(context)
                    val deviceId = BackupPreferences.getOrCreateDeviceId()

                    // 🌍 Hibrit İletişim Paketi
                    val payload = hashMapOf(
                        "groupId" to groupId,
                        "deviceId" to deviceId,
                        "deviceName" to BackupPreferences.getDeviceName(),
                        "token" to token,
                        "updatedAt" to System.currentTimeMillis(),
                        "platform" to "android",
                        "status" to "active" // Cihazın canlı olduğunu belirtir
                    )

                    // Firestore'a (sync_groups/{groupId}/device_tokens/{deviceId}) kaydet
                    Firebase.firestore.collection("sync_groups")
                        .document(groupId)
                        .collection("device_tokens")
                        .document(deviceId)
                        .set(payload, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ Cihaz iletişim adresi (Token) başarıyla güncellendi.")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ Token Firestore'a yazılamadı: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "⚠️ Firebase Messaging Token alınamadı: ${e.message}")
                }
        }
    }

    /**
     * 🚪 Cihaz ayrıldığında veya kullanıcı çıkış yaptığında token'ı pasife çekmek için.
     */
    fun unregister(context: Context) {
        val groupId = SyncGroupResolver.resolve(context)
        val deviceId = BackupPreferences.getOrCreateDeviceId()

        Firebase.firestore.collection("sync_groups")
            .document(groupId)
            .collection("device_tokens")
            .document(deviceId)
            .update("status", "inactive", "updatedAt", System.currentTimeMillis())
    }
}