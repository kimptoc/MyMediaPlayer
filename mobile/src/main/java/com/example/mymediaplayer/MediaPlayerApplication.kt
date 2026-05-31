package com.example.mymediaplayer

import com.example.mymediaplayer.shared.BuildConfig

import android.app.Application
import timber.log.Timber

class MediaPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
