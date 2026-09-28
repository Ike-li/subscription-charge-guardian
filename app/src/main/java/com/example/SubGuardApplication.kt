package com.example

import android.app.Application
import android.util.Log

class SubGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("SubGuardApplication", "Application onCreate executed successfully")
    }
}
