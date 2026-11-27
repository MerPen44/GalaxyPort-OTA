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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    // === CONFIGURATION ===
    // Update with your raw GitHub URL
    private val otaUrl = "https://raw.githubusercontent.com/MerPen44/GalaxyPort-OTA/main/ota.json"
    
    // === STATE VARIABLES ===
    private var downloadUrl: String? = null
    private var downloadId: Long = -1L
    private var newVersionName: String = ""
    private var installedVersionName: String = "0.5"
    private var installedVersionCode: Int = 5

    // === UI HOOKS ===
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvChangelog: TextView
    private lateinit var btnAction: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cardChangelog: View

    enum class State {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        DOWNLOAD_COMPLETE,
        ERROR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSystemProperties()
        updateUIState(State.IDLE)

        btnAction.setOnClickListener {
            when (btnAction.tag) {
                "CHECK" -> checkForUpdates()
                "DOWNLOAD" -> startDownload()
                "REBOOT" -> tvStatusTitle.text = "Reboot to recovery and flash the zip from your Downloads folder."
            }
        }

        // Register download completion receiver
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (_: Exception) {}
    }

    private fun initViews() {
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvChangelog = findViewById(R.id.tvChangelog)
        btnAction = findViewById(R.id.btnAction)
        progressBar = findViewById(R.id.progressBar)
        cardChangelog = findViewById(R.id.cardChangelog)
    }

    private fun loadSystemProperties() {
        installedVersionName = getSystemProp("ro.galaxyport.version", "0.5")
        installedVersionCode = getSystemProp("ro.galaxyport.version_code", "5").toIntOrNull() ?: 5
        tvCurrentVersion.text = "One UI 8.0 • GalaxyPort v$installedVersionName"
    }

    private fun checkForUpdates() {
        updateUIState(State.CHECKING)

        Thread {
            try {
                val conn = URL(otaUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)

                val remoteCode = json.getInt("versionCode")
                newVersionName = json.getString("version")
                val changelog = json.optString("changelog", "No changelog provided.")
                downloadUrl = json.getString("url")

                runOnUiThread {
                    if (remoteCode > installedVersionCode) {
                        tvChangelog.text = changelog
                        updateUIState(State.UPDATE_AVAILABLE)
                    } else {
                        updateUIState(State.UP_TO_DATE)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatusTitle.text = "Failed to connect: ${e.localizedMessage}"
                    updateUIState(State.ERROR)
                }
            }
        }.start()
    }

    private fun startDownload() {
        val url = downloadUrl ?: return
        updateUIState(State.DOWNLOADING)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("GalaxyPort v$newVersionName")
            .setDescription("Downloading update zip...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "GalaxyPort-v$newVersionName.zip"
            )

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                runOnUiThread {
                    updateUIState(State.DOWNLOAD_COMPLETE)
                }
            }
        }
    }

    // === UI STATE MACHINE (Easy for you to customize) ===
    private fun updateUIState(state: State) {
        when (state) {
            State.IDLE -> {
                progressBar.visibility = View.GONE
                cardChangelog.visibility = View.GONE
                tvStatusTitle.text = "Check for new system updates"
                btnAction.text = "Check for update"
                btnAction.tag = "CHECK"
                btnAction.isEnabled = true
            }
            State.CHECKING -> {
                progressBar.visibility = View.VISIBLE
                tvStatusTitle.text = "Checking for update..."
                btnAction.isEnabled = false
            }
            State.UP_TO_DATE -> {
                progressBar.visibility = View.GONE
                cardChangelog.visibility = View.GONE
                tvStatusTitle.text = "Your software is up to date."
                btnAction.text = "Check again"
                btnAction.tag = "CHECK"
                btnAction.isEnabled = true
            }
            State.UPDATE_AVAILABLE -> {
                progressBar.visibility = View.GONE
                cardChangelog.visibility = View.VISIBLE
                tvStatusTitle.text = "GalaxyPort v$newVersionName is available!"
                btnAction.text = "Download"
                btnAction.tag = "DOWNLOAD"
                btnAction.isEnabled = true
            }
            State.DOWNLOADING -> {
                progressBar.visibility = View.VISIBLE
                tvStatusTitle.text = "Downloading to Downloads folder..."
                btnAction.isEnabled = false
            }
            State.DOWNLOAD_COMPLETE -> {
                progressBar.visibility = View.GONE
                tvStatusTitle.text = "Download complete!\nReboot to recovery to install."
                btnAction.text = "Instructions"
                btnAction.tag = "REBOOT"
                btnAction.isEnabled = true
            }
            State.ERROR -> {
                progressBar.visibility = View.GONE
                btnAction.text = "Retry"
                btnAction.tag = "CHECK"
                btnAction.isEnabled = true
            }
        }
    }

    private fun getSystemProp(key: String, def: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("getprop", key))
            BufferedReader(InputStreamReader(proc.inputStream)).readLine()?.trim() ?: def
        } catch (_: Exception) {
            def
        }
    }
}
