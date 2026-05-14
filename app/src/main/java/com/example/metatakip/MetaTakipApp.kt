package com.example.metatakip

import android.app.Application
import androidx.multidex.MultiDex
import com.example.metatakip.feature_backup.firebase.FirebaseRealtimeBridgeManager
import com.example.metatakip.feature_backup.util.BackupPreferences
import com.example.metatakip.feature_backup.util.ChangeLogManager
import com.google.firebase.FirebaseApp

class MetaTakipApp : Application() {

    override fun onCreate() {
        super.onCreate()

        MultiDex.install(this)
        FirebaseApp.initializeApp(this)

        // 🔥🔥🔥 KRİTİK: BackupPreferences'i initialize et (TÜM diğer işlemlerden ÖNCE)
        BackupPreferences.initialize(this)

        ChangeLogManager.initialize(this)
        FirebaseRealtimeBridgeManager.start(this)


    }
}