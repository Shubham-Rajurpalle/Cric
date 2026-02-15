package com.cricketApp.cric.LogIn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.installreferrer.BuildConfig
import com.cricketApp.cric.R
import com.cricketApp.cric.home.Home
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings


class MainActivity : AppCompatActivity() {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour
                .build()
            setConfigSettingsAsync(configSettings)
            setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkForUpdate()
    }

    private fun checkForUpdate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val latestVersion = remoteConfig.getLong("latest_version")
                    val forceUpdate = remoteConfig.getBoolean("force_update_required")
                    val currentVersion = BuildConfig.VERSION_CODE

                    if (forceUpdate && currentVersion < latestVersion) {
                        showForceUpdateDialog()
                    } else {
                        splashScreenSetup()
                    }
                } else {
                    splashScreenSetup()
                }
            }
    }

    private fun showForceUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle("Update Required")
            .setMessage("A new version of this app is available. Please update to continue.")
            .setCancelable(false)
            .setPositiveButton("Update") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun splashScreenSetup() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@MainActivity, Home::class.java))
            finish()
        }, 2000)
    }
}
