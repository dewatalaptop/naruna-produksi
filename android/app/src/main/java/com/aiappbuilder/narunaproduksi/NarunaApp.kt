package com.aiappbuilder.narunaproduksi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.nuvora.bugreporter.BugReporter

class NarunaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crash reports go to the App Builder (see ai-app-builder/templates/shared/bug-reporter).
        // versionName is read at runtime: BuildConfig is disabled by default in AGP 8.
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (ignored: Exception) {
            "?"
        }
        BugReporter.init(this, appId = "naruna-produksi", key = "52dac225f4c8288b9fcfa66ac2ba4dba", version = versionName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
