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

    private val otaUrl = "https://raw.githubusercontent.com/YOUR_USERNAME/GalaxyPort-OTA/main/ota.json"
    private var downloadUrl: String? = null
    private var newVersionName: String = ""

    private lateinit var tvSubStatus: TextView
    private lateinit var tvVersionTag: TextView
    private lateinit var tvChangelog: TextView
    private lateinit var btnAction: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutSplash: View
    private lateinit var cardUpdate: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSubStatus = findViewById(R.id.tvSubStatus)
        tvVersionTag = findViewById(R.id.tvVersionTag)
        tvChangelog = findViewById(R.id.tvChangelog)
        btnAction = findViewById(R.id.btnAction)
        progressBar = findViewById(R.id.progressBar)
        layoutSplash = findViewById(R.id.layoutSplash)
        cardUpdate = findViewById(R.id.cardUpdate)

        checkForUpdates()

        btnAction.setOnClickListener {
            when (btnAction.tag) {
                "CHECK" -> checkForUpdates()
                "DOWNLOAD" -> startDownload()
                "REBOOT" -> tvSubStatus.text = "Reboot to recovery and flash zip from Downloads."
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
        layoutSplash.visibility = View.VISIBLE
        cardUpdate.visibility = View.GONE
        tvSubStatus.text = "Connecting to update server..."
        btnAction.isEnabled = false

        Thread {
            try {
                val conn = URL(otaUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val remoteCode = json.getInt("versionCode")
                newVersionName = json.getString("version")
                val changelog = json.optString("changelog", "Bug fixes and performance improvements.")
                downloadUrl = json.getString("url")

                val installedCode = getProp("ro.galaxyport.version_code", "5").toIntOrNull() ?: 5

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (remoteCode > installedCode) {
                        layoutSplash.visibility = View.GONE
                        tvVersionTag.text = "GalaxyPort v$newVersionName"
                        tvChangelog.text = changelog
                        cardUpdate.visibility = View.VISIBLE
                        btnAction.text = "Download files"
                        btnAction.tag = "DOWNLOAD"
                    } else {
                        tvSubStatus.text = "You are on the latest version."
                        btnAction.text = "Check for updates"
                        btnAction.tag = "CHECK"
                    }
                    btnAction.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvSubStatus.text = "Latest update check failed."
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
        btnAction.isEnabled = false

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("GalaxyPort v$newVersionName")
            .setDescription("Downloading update zip...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GalaxyPort-v$newVersionName.zip")

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                tvSubStatus.text = "Download complete!"
                btnAction.text = "Ready to flash"
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
