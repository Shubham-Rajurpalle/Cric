package com.cricketApp.cric

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // For splash screen and user logging info fetching
        splashScreenSetup()

    }

    private fun splashScreenSetup() {
        Handler(Looper.getMainLooper()).postDelayed({

            // Checking for the Login info
            val sharedPreference=getSharedPreferences("user_login_info", MODE_PRIVATE)
            val isLoggedIn=sharedPreference.getBoolean("isLoggedIn",false)

            if(isLoggedIn){
                startActivity(Intent(this@MainActivity,Home::class.java))
            }else{
                startActivity(Intent(this@MainActivity,SignIn::class.java))
            }

            // Removing current activity from stack
            finish()
        },2000)
    }
}