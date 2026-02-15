package com.cricketApp.cric.Chat

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cricketApp.cric.R

class ActivityImageViewer : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // Hide status bar and navigation bar for immersive experience
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        // Get image URL from intent
        val imageUrl = intent.getStringExtra("IMAGE_URL") ?: return

        // Initialize the ImageView
        val imageView = findViewById<ImageView>(R.id.imageView)

        // Load the image with Glide
        Glide.with(this)
            .load(imageUrl)
            .into(imageView)

        // Set up close button
        val closeButton = findViewById<ImageButton>(R.id.buttonClose)
        closeButton.setOnClickListener {
            finish()
        }
    }
}