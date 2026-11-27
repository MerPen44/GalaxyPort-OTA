package com.galaxyport.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val otaUrl = "https://raw.githubusercontent.com/MerPen44/GalaxyPort-OTA/main/ota.json"
    private var downloadUrl: String? = null
    private var newVersionName: String = ""

    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvChangelog: TextView
    private lateinit var btnAction: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cardChangelog: View

    enum class State { IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, DOWNLOADING, DOWNLOAD_COMPLETE, ERROR }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvChangelog = findViewById(R.id.tvChangelog)
        btnAction = findViewById(R.id.btnAction)
        progressBar = findViewById(R.id.progressBar)
        cardChangelog = findViewById(R.id.cardChangelog)

        val installedVer = getProp("ro.galaxyport.version", "0.5")
        val installedCode = getProp("ro.galaxyport.version_code", "5").toIntOrNull() ?: 5
        tvCurrentVersion.text = "One UI 8.0 • GalaxyPort v$installedVer (code $installedCode)"

        btnAction.setOnClickListener {
            when (btnAction.tag) {
                "CHECK" -> checkForUpdates()
                "DOWNLOAD" -> startDownload()
                "REBOOT" -> tvStatusTitle.text = "Reboot to recovery and flash the zip from Downloads."
            }
        }

        ContextCompat.registerReceiver(
            this,
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(onDownloadComplete) } catch (_: Exception) {}
    }

    private fun checkForUpdates() {
        progressBar.visibility = View.VISIBLE
        cardChangelog.visibility = View.GONE
        tvStatusTitle.text = "Checking for update..."
        btnAction.isEnabled = false

        Thread {
            try {
                val conn = URL(otaUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val remoteCode = json.getInt("versionCode")
                newVersionName = json.getString("version")
                val changelog = json.optString("changelog", "No changelog.")
                downloadUrl = json.getString("url")

                val installedCode = getProp("ro.galaxyport.version_code", "5").toIntOrNull() ?: 5

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (remoteCode > installedCode) {
                        tvChangelog.text = changelog
                        cardChangelog.visibility = View.VISIBLE
                        tvStatusTitle.text = "GalaxyPort v$newVersionName is available!"
                        btnAction.text = "Download"
                        btnAction.tag = "DOWNLOAD"
                    } else {
                        tvStatusTitle.text = "You are on the latest version."
                        btnAction.text = "Check again"
                        btnAction.tag = "CHECK"
                    }
                    btnAction.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvStatusTitle.text = "Check failed: ${e.localizedMessage}"
                    btnAction.text = "Retry"
                    btnAction.tag = "CHECK"
                    btnAction.isEnabled = true
                }
            }
        }.start()
    }

    private fun startDownload() {
        val url = downloadUrl ?: return
        progressBar.visibility = View.VISIBLE
        tvStatusTitle.text = "Downloading update..."
        btnAction.isEnabled = false

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("GalaxyPort v$newVersionName")
            .setDescription("Downloading ROM update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GalaxyPort-v$newVersionName.zip")

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                tvStatusTitle.text = "Download complete!\nSaved to Downloads folder."
                btnAction.text = "Ready"
                btnAction.tag = "REBOOT"
                btnAction.isEnabled = true
            }
        }
    }

    private fun getProp(key: String, def: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("getprop", key))
            BufferedReader(InputStreamReader(proc.inputStream)).readLine()?.trim() ?: def
        } catch (_: Exception) {
            def
        }
    }
}
