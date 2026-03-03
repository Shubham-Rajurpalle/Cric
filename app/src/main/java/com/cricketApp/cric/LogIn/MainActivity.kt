package com.cricketApp.cric.LogIn

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.cricketApp.cric.BuildConfig
import com.cricketApp.cric.R
import com.cricketApp.cric.home.Home
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.io.File

class MainActivity : AppCompatActivity() {

    private var downloadReceiver: BroadcastReceiver? = null

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0) // Change to 3600 before release
                .build()
            setConfigSettingsAsync(configSettings)
            setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        android.util.Log.d("MAIN_ACTIVITY", "NEW CODE RUNNING - No Play Store logic")
        checkForUpdate()
    }

    private fun checkForUpdate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                android.util.Log.d("REMOTE_CONFIG", "Fetch success: ${task.isSuccessful}")
                android.util.Log.d("REMOTE_CONFIG", "Exception: ${task.exception?.message}")

                if (task.isSuccessful) {
                    val latestVersion = remoteConfig.getLong("latest_version")
                    val forceUpdate = remoteConfig.getBoolean("force_update_required")
                    val apkUrl = remoteConfig.getString("apk_download_url")
                    val currentVersion = BuildConfig.VERSION_CODE.toLong()

                    android.util.Log.d("REMOTE_CONFIG", "Current version: $currentVersion")
                    android.util.Log.d("REMOTE_CONFIG", "Latest version: $latestVersion")
                    android.util.Log.d("REMOTE_CONFIG", "Force update: $forceUpdate")
                    android.util.Log.d("REMOTE_CONFIG", "APK URL: $apkUrl")

                    if (forceUpdate && currentVersion < latestVersion) {
                        showForceUpdateDialog(apkUrl)
                    } else {
                        splashScreenSetup()
                    }
                } else {
                    android.util.Log.d("REMOTE_CONFIG", "FETCH FAILED - going to splash")
                    // Fetch failed — let user in
                    splashScreenSetup()
                }
            }
    }

    private fun showForceUpdateDialog(apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("🚀 Update Required")
            .setMessage("A new version is available. Please download and install to continue.")
            .setCancelable(false)
            .setPositiveButton("Download & Install") { _, _ ->
                downloadAndInstallApk(apkUrl)
            }
            .show()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun downloadAndInstallApk(apkUrl: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)

        val layoutProgress = dialogView.findViewById<LinearLayout>(R.id.layoutProgress)
        val btnDownload = dialogView.findViewById<TextView>(R.id.btnDownload)
        val tvDownloadStatus = dialogView.findViewById<TextView>(R.id.tvDownloadStatus)
        val tvProgressPercent = dialogView.findViewById<TextView>(R.id.tvProgressPercent)
        val tvDownloadSize = dialogView.findViewById<TextView>(R.id.tvDownloadSize)
        val progressFill = dialogView.findViewById<View>(R.id.progressFill)

        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        // Start download on button click
        btnDownload.setOnClickListener {
            btnDownload.visibility = View.GONE
            layoutProgress.visibility = View.VISIBLE

            val fileName = "cric-update.apk"
            val outputFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (outputFile.exists()) outputFile.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Cricket App Update")
                .setDescription("Downloading latest version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(outputFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            // Track progress
            val progressHandler = Handler(Looper.getMainLooper())
            val progressRunnable = object : Runnable {
                override fun run() {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor.moveToFirst()) {
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            tvProgressPercent.text = "$percent%"
                            tvDownloadSize.text = "${formatSize(downloaded)} / ${formatSize(total)}"

                            // Animate progress fill width
                            val parentWidth = (progressFill.parent as View).width
                            val targetWidth = (parentWidth * percent / 100)
                            progressFill.layoutParams.width = targetWidth
                            progressFill.requestLayout()

                            if (percent >= 100) {
                                tvDownloadStatus.text = "Installing..."
                                tvProgressPercent.text = "100%"
                            }
                        }
                    }
                    cursor.close()
                    progressHandler.postDelayed(this, 300)
                }
            }
            progressHandler.post(progressRunnable)

            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        progressHandler.removeCallbacks(progressRunnable)
                        dialog.dismiss()
                        triggerInstall(outputFile)
                    }
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
            } else {
                registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024f)
        else "%.1f MB".format(bytes / (1024f * 1024f))
    }


    private fun triggerInstall(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(installIntent)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Install Failed")
                .setMessage("Could not install APK. Error: ${e.message}")
                .setCancelable(false)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun splashScreenSetup() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@MainActivity, Home::class.java))
            finish()
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let { unregisterReceiver(it) }
    }
}