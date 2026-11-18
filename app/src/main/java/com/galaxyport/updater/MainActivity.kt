package com.galaxyport.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val otaUrl = "https://raw.githubusercontent.com/MerPen44/GalaxyPort-OTA/main/ota.json"
    private var downloadUrl: String? = null
    private var latestVersion: String = ""

    private fun getProp(key: String, defaultVal: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readLine()?.trim()
            if (out.isNullOrEmpty()) defaultVal else out
        } catch (_: Exception) {
            defaultVal
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvCurrent = findViewById<TextView>(R.id.tvCurrentVersion)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnCheck = findViewById<Button>(R.id.btnCheck)
        val btnDownload = findViewById<Button>(R.id.btnDownload)

        val installedVer = getProp("ro.galaxyport.version", "0.5")
        val installedCode = getProp("ro.galaxyport.version_code", "5").toIntOrNull() ?: 5
        tvCurrent.text = "Installed: GalaxyPort v$installedVer (code $installedCode)"

        btnCheck.setOnClickListener {
            tvStatus.text = "Checking for updates..."
            btnDownload.isEnabled = false
            Thread {
                try {
                    val conn = URL(otaUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    val text = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    val remoteCode = json.getInt("versionCode")
                    latestVersion = json.getString("version")
                    val changelog = json.optString("changelog", "")
                    val url = json.getString("url")

                    runOnUiThread {
                        if (remoteCode > installedCode) {
                            downloadUrl = url
                            btnDownload.isEnabled = true
                            tvStatus.text = "Update available: v$latestVersion\n\nChangelog:\n$changelog"
                        } else {
                            tvStatus.text = "You are on the latest version (v$installedVer)."
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvStatus.text = "Check failed: ${e.message}" }
                }
            }.start()
        }

        btnDownload.setOnClickListener {
            val url = downloadUrl ?: return@setOnClickListener
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("GalaxyPort v$latestVersion")
                .setDescription("Downloading ROM update...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GalaxyPort-v$latestVersion.zip")
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(req)
            tvStatus.text = "Downloading to Downloads/GalaxyPort-v$latestVersion.zip...\nWhen done, reboot to Recovery and flash it (no data wipe needed!)."
        }
    }
}
