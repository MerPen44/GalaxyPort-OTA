package com.galaxyport.updater

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val otaUrl = "https://raw.githubusercontent.com/MerPen44/GalaxyPort-OTA/main/ota.json"
    private var downloadUrl: String? = null
    private var newVersionName: String = ""

    private lateinit var orbContainer: View
    private lateinit var orbWarm: View
    private lateinit var orbCool: View
    private lateinit var layoutSplash: View
    private lateinit var cardUpdate: View
    private lateinit var tvBrandTitle: TextView
    private lateinit var tvSubStatus: TextView
    private lateinit var tvUpdateTitle: TextView
    private lateinit var tvVersionTag: TextView
    private lateinit var tvWhatsNewLabel: TextView
    private lateinit var tvChangelog: TextView
    private lateinit var tvUpdateInfo: TextView
    private lateinit var tvFooterInfo: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnAction: Button
    private lateinit var btnBack: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#09090C")
        window.navigationBarColor = Color.parseColor("#09090C")
        setContentView(R.layout.activity_main)

        orbContainer = findViewById(R.id.orbContainer)
        orbWarm = findViewById(R.id.orbWarm)
        orbCool = findViewById(R.id.orbCool)
        layoutSplash = findViewById(R.id.layoutSplash)
        cardUpdate = findViewById(R.id.cardUpdate)
        tvBrandTitle = findViewById(R.id.tvBrandTitle)
        tvSubStatus = findViewById(R.id.tvSubStatus)
        tvUpdateTitle = findViewById(R.id.tvUpdateTitle)
        tvVersionTag = findViewById(R.id.tvVersionTag)
        tvWhatsNewLabel = findViewById(R.id.tvWhatsNewLabel)
        tvChangelog = findViewById(R.id.tvChangelog)
        tvUpdateInfo = findViewById(R.id.tvUpdateInfo)
        tvFooterInfo = findViewById(R.id.tvFooterInfo)
        progressBar = findViewById(R.id.progressBar)
        btnAction = findViewById(R.id.btnAction)
        btnBack = findViewById(R.id.btnBack)

        applyFonts()
        startOrbEffects()

        val installedVer = getProp("ro.galaxyport.version", "0.5")
        val installedCode = getProp("ro.galaxyport.version_code", "5").toIntOrNull() ?: 5
        tvFooterInfo.text = "GalaxyPort v$installedVer (build $installedCode) • SM-P610"

        btnBack.setOnClickListener {
            if (cardUpdate.visibility == View.VISIBLE) {
                crossfade(cardUpdate, layoutSplash)
                btnAction.text = "Check for updates"
                btnAction.tag = "CHECK"
            } else {
                finish()
            }
        }

        btnAction.setOnClickListener {
            when (btnAction.tag) {
                "CHECK" -> checkForUpdates()
                "DOWNLOAD" -> startDownload()
                "REBOOT" -> tvSubStatus.text = "Saved in Downloads/. Reboot to Recovery to flash!"
                else -> checkForUpdates()
            }
        }

        ContextCompat.registerReceiver(
            this,
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        checkForUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(onDownloadComplete) } catch (_: Exception) {}
    }

    private fun startOrbEffects() {
        // Real hardware Gaussian blur on the orb (API 31+, minSdk is 33)
        orbContainer.setRenderEffect(
            RenderEffect.createBlurEffect(95f, 95f, Shader.TileMode.CLAMP)
        )

        val warmAnim = ObjectAnimator.ofPropertyValuesHolder(
            orbWarm,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -26f, 22f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -24f, 18f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f)
        ).apply {
            duration = 5500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val coolAnim = ObjectAnimator.ofPropertyValuesHolder(
            orbCool,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 24f, -20f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 28f, -18f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.1f, 0.95f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.1f, 0.95f)
        ).apply {
            duration = 6500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        warmAnim.start()
        coolAnim.start()
    }

    private fun applyFonts() {
        // 1. Check if user bundled SamsungSharpSans in assets/fonts/
        val assetBold = tryLoadAssetFont("fonts/SamsungSharpSans-Bold.ttf")
            ?: tryLoadAssetFont("fonts/SamsungSharpSans.ttf")
        val assetReg = tryLoadAssetFont("fonts/SamsungSharpSans-Regular.ttf") ?: assetBold

        // 2. Otherwise fall back to built-in Samsung One UI system fonts on the tablet
        val sysBold = assetBold
            ?: tryLoadSysFont("/system/fonts/SamsungOneUI-700.ttf")
            ?: tryLoadSysFont("/system/fonts/OneUISans-VF.ttf")
            ?: Typeface.create("sans-serif-medium", Typeface.BOLD)
        val sysReg = assetReg
            ?: tryLoadSysFont("/system/fonts/SamsungOneUI-Regular.ttf")
            ?: tryLoadSysFont("/system/fonts/OneUISans-VF.ttf")
            ?: Typeface.create("sans-serif", Typeface.NORMAL)

        tvBrandTitle.typeface = sysBold
        tvUpdateTitle.typeface = sysBold
        tvWhatsNewLabel.typeface = sysBold
        btnAction.typeface = sysBold
        tvSubStatus.typeface = sysReg
        tvVersionTag.typeface = sysBold
        tvChangelog.typeface = sysReg
        tvUpdateInfo.typeface = sysReg
        tvFooterInfo.typeface = sysReg
    }

    private fun tryLoadAssetFont(path: String): Typeface? = try {
        Typeface.createFromAsset(assets, path)
    } catch (_: Exception) { null }

    private fun tryLoadSysFont(path: String): Typeface? = try {
        val f = File(path)
        if (f.exists()) Typeface.createFromFile(f) else null
    } catch (_: Exception) { null }

    private fun crossfade(hideView: View, showView: View) {
        if (showView.visibility == View.VISIBLE) return
        hideView.animate().alpha(0f).translationY(-20f).setDuration(220).withEndAction {
            hideView.visibility = View.GONE
            hideView.translationY = 0f
            showView.alpha = 0f
            showView.translationY = 30f
            showView.visibility = View.VISIBLE
            showView.animate().alpha(1f).translationY(0f).setDuration(320)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }.start()
    }

    private fun checkForUpdates() {
        crossfade(cardUpdate, layoutSplash)
        progressBar.visibility = View.VISIBLE
        tvSubStatus.text = "Checking for software updates..."
        btnAction.isEnabled = false

        Thread {
            try {
                // ?t=... bypasses GitHub Raw's 5-minute cache
                val conn = URL("$otaUrl?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "GalaxyPortUpdater/1.0")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)

                val remoteCode = json.getInt("versionCode")
                newVersionName = json.getString("version")
                val changelog = json.optString("changelog", "Stability and reliability improvements.")
                downloadUrl = json.getString("url")

                val installedVer = getProp("ro.galaxyport.version", "0.5")
                val installedCode = getProp("ro.galaxyport.version_code", "5").toIntOrNull() ?: 5

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (remoteCode > installedCode) {
                        tvVersionTag.text = "GalaxyPort v$newVersionName"
                        tvChangelog.text = changelog
                        tvUpdateInfo.text = "• Installed: v$installedVer (build $installedCode)\n• New version: v$newVersionName (build $remoteCode)\n• Flash in Recovery without wiping /data"
                        crossfade(layoutSplash, cardUpdate)
                        btnAction.text = "Download files"
                        btnAction.tag = "DOWNLOAD"
                    } else {
                        tvSubStatus.text = "Your software is up to date."
                        btnAction.text = "Check for updates"
                        btnAction.tag = "CHECK"
                    }
                    btnAction.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvSubStatus.text = "Check failed: ${e.javaClass.simpleName}"
                    btnAction.text = "Retry"
                    btnAction.tag = "CHECK"
                    btnAction.isEnabled = true
                }
            }
        }.start()
    }

    private fun startDownload() {
        val url = downloadUrl ?: return
        crossfade(cardUpdate, layoutSplash)
        progressBar.visibility = View.VISIBLE
        tvSubStatus.text = "Downloading update..."
        btnAction.isEnabled = false

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("GalaxyPort v$newVersionName")
            .setDescription("Downloading ROM update zip...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GalaxyPort-v$newVersionName.zip")

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                tvSubStatus.text = "Download complete!\nSaved to Downloads/GalaxyPort-v$newVersionName.zip"
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
