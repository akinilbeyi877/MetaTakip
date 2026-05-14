package com.example.metatakip.test

import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.metatakip.R
import com.example.metatakip.deleteHistoryActive.ui.deleteHistoryFragment.DeleteHistoryFragment

class DeleteHistoryActivity : AppCompatActivity() {

    private lateinit var deleteHistoryFragment: DeleteHistoryFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_history)

        Log.d("DeleteHistoryActivity", "✅ Activity AÇILDI")

        // --------------------------------------------------
        // Fragment oluştur
        // --------------------------------------------------

        deleteHistoryFragment = DeleteHistoryFragment()

        // --------------------------------------------------
        // Fragment ekle (KTX YOK → klasik kullanım)
        // --------------------------------------------------

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, deleteHistoryFragment, "DeleteHistoryFragment")
            .commit()

        // --------------------------------------------------
        // 🔙 BACK HANDLER
        // --------------------------------------------------

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    Log.d("DeleteHistoryActivity", "🔙 BACK BASILDI")

                    // Fragment back'i yakalarsa activity kapanmaz
                    if (::deleteHistoryFragment.isInitialized) {
                        val handled = deleteHistoryFragment.handleBackPress()
                        if (handled) {
                            Log.d("DeleteHistoryActivity", "↩️ BACK Fragment tarafından yakalandı")
                            return
                        }
                    }

                    // Fragment yakalamadı → activity kapanır
                    Log.d("DeleteHistoryActivity", "❌ BACK Activity kapanıyor")
                    finish()
                }
            }
        )
    }
}
