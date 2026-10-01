package com.vineyard.aivideostudio

import android.app.Application
import com.vineyard.aivideostudio.core.util.CrashHandler
import com.vineyard.aivideostudio.di.AppContainer

class EditoraApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CrashHandler.install(container.logger)
    }
}
