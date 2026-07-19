package com.example.aiwidgetstudio

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.aiwidgetstudio.worker.RefreshWorkScheduler
import com.example.aiwidgetstudio.worker.ResetWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AppController : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        ResetWorkScheduler.schedule(this)
        RefreshWorkScheduler.schedule(this)
        RefreshWorkScheduler.runNow(this)
    }
}
