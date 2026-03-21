package com.charles.pocketassistant

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }

    override fun callApplicationOnCreate(app: Application?) {
        // Initialize WorkManager for testing before Hilt creates components that depend on it
        app?.let {
            val config = Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(it, config)
        }
        super.callApplicationOnCreate(app)
    }
}
