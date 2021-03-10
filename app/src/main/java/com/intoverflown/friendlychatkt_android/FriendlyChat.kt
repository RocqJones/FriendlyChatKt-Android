package com.intoverflown.friendlychatkt_android

import android.app.Application
import com.google.android.datatransport.BuildConfig
import timber.log.Timber

class FriendlyChat : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}