package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class BlockifyApplication : Application() {
    companion object {
        lateinit var instance: BlockifyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.i("BlockifyApplication", "FirebaseApp initialized successfully")
            }
        } catch (e: Throwable) {
            Log.w("BlockifyApplication", "FirebaseApp initialization: ${e.message}")
        }
    }
}
