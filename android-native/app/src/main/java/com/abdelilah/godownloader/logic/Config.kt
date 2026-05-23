package com.abdelilah.godownloader.logic

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

data class Config(
    var downloadDir: String = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "godownload").absolutePath,
    var maxThreads: Int = 8,
    var maxConcurrentDownloads: Int = 3,
    var proxyEnabled: Boolean = false,
    var proxyUrl: String = "",
    var language: String = "en" // "en" or "ar"
)

object ConfigManager {
    private const val PREFS_NAME = "godownloader_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadConfig(): Config {
        return Config(
            downloadDir = prefs.getString("download_dir", null) ?: Config().downloadDir,
            maxThreads = prefs.getInt("max_threads", 8),
            maxConcurrentDownloads = prefs.getInt("max_concurrent", 3),
            proxyEnabled = prefs.getBoolean("proxy_enabled", false),
            proxyUrl = prefs.getString("proxy_url", "") ?: "",
            language = prefs.getString("language", "en") ?: "en"
        )
    }

    fun saveConfig(config: Config) {
        prefs.edit().apply {
            putString("download_dir", config.downloadDir)
            putInt("max_threads", config.maxThreads)
            putInt("max_concurrent", config.maxConcurrentDownloads)
            putBoolean("proxy_enabled", config.proxyEnabled)
            putString("proxy_url", config.proxyUrl)
            putString("language", config.language)
            apply()
        }
    }
}
