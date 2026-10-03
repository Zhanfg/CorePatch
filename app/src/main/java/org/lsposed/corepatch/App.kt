package org.lsposed.corepatch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.libxposed.service.XposedServiceHelper.OnServiceListener

class App : Application(), OnServiceListener {

    companion object {
        var rwPrefs: SharedPreferences? = null
        var mService: XposedService? = null
        var serviceError: String? = null
        var serviceApiVersion: Int? = null
        var reloadListener: () -> Unit = {}
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        try {
            XposedServiceHelper.registerListener(this)
        } catch (t: Throwable) {
            recordServiceError("registerListener", t)
        }
    }

    override fun onServiceBind(service: XposedService) {
        synchronized(this) {
            try {
                val prefs = service.getRemotePreferences("conf")
                mService = service
                rwPrefs = prefs
                serviceApiVersion = runCatching {
                    service.javaClass.getMethod("getApiVersion").invoke(service) as Int
                }.getOrNull()
                serviceError = null
            } catch (t: Throwable) {
                mService = null
                rwPrefs = null
                serviceApiVersion = null
                recordServiceError("onServiceBind", t)
            }
            runCatching { reloadListener() }
                .onFailure { recordServiceError("reloadListener", it) }
        }
    }

    override fun onServiceDied(service: XposedService) {
        synchronized(this) {
            if (mService == service) {
                mService = null
                rwPrefs = null
                serviceApiVersion = null
            }
            runCatching { reloadListener() }
                .onFailure { recordServiceError("serviceDied", it) }
        }
    }

    private fun recordServiceError(stage: String, throwable: Throwable) {
        val detail = "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}"
        serviceError = "$stage · $detail"
        Log.e("CorePatch", "Xposed service failure at $stage", throwable)
    }
}
