package com.cocinacapital.app

import android.app.Application
import com.cocinacapital.app.di.AppContainer

/** Declarar en el Manifest: android:name=".CocinaApp" */
class CocinaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
