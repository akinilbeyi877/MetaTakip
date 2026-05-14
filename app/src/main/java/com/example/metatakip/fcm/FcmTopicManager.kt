
package com.example.metatakip.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

object FcmTopicManager {

    private const val TAG = "FcmTopicManager"

    fun buildCompanyTopic(companyName: String): String {
        return "company_" + companyName
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
    }

    fun subscribe(companyName: String) {
        val topic = buildCompanyTopic(companyName)

        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                Log.d(TAG, "subscribe topic=$topic success=${task.isSuccessful}")
            }
    }

    fun logToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "Token alınamadı", task.exception)
                    return@addOnCompleteListener
                }
                Log.d(TAG, "FCM TOKEN = ${task.result}")
            }
    }
}
