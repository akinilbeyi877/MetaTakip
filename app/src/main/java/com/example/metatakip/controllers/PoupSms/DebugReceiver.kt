package com.example.metatakip.controllers.poupsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "null"
        Log.d("DEBUG_RECEIVER", "🎯 Action: $action")

        if (action.contains("PHONE", ignoreCase = true) ||
            action.contains("CALL", ignoreCase = true)) {

            Log.d("DEBUG_RECEIVER", "📞 Telefon event'i!")

            // Tüm extras'ı logla
            intent.extras?.keySet()?.forEach { key ->
                val value = intent.extras?.get(key)
                Log.d("DEBUG_RECEIVER", "   🔍 $key = $value")
            }

            // Telephony durumu
            if (action == TelephonyManager.ACTION_PHONE_STATE_CHANGED ||
                action == "android.intent.action.PHONE_STATE" ||
                action == "android.telephony.action.PHONE_STATE_CHANGED") {

                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                Log.d("DEBUG_RECEIVER", "📞 Durum: $state, Numara: $number")
            }
        }
    }
}