package com.workspacehelper.app

import android.app.Application
import androidx.work.Configuration

// Provides WorkManager's configuration so it initializes reliably (the
// background offline-notification worker depends on it).
class App : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
