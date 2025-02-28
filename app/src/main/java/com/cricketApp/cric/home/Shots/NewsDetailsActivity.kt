package com.cricketApp.cric.home.Shots

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.ActivityNewsDetailsBinding
import com.google.firebase.firestore.FirebaseFirestore

class NewsDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewsDetailsBinding
    private val firestore = FirebaseFirestore.getInstance()
    private var newsId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        newsId = intent.getStringExtra("newsId")
        if (newsId != null) {
            fetchNewsDetails(newsId!!)
            updateViewsCount(newsId!!)
        }

        binding.backArrow.setOnClickListener {
            finish()
        }

        binding.shareButton.setOnClickListener {
            shareNews()
        }
    }

    private fun fetchNewsDetails(newsId: String) {
        firestore.collection("NewsPosts").document(newsId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val news = document.toObject(News::class.java)
                    if (news != null) {
                        binding.newsTitle.text = news.title
                        binding.newsContent.text = news.newsContent
                        binding.newsViews.text = "${news.views} Views"
                        binding.newsTime.text = getTimeAgo(news.timestamp)

                        Glide.with(this)
                            .load(news.imageUrl)
                            .into(binding.newsImage)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching news details", e)
            }
    }

    private fun updateViewsCount(newsId: String) {
        val newsRef = firestore.collection("NewsPosts").document(newsId)

        newsRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val currentViews = document.getLong("views") ?: 0
                val newViewsCount = currentViews + 1

                newsRef.update("views", newViewsCount)
                    .addOnSuccessListener {
                        binding.newsViews.text = "$newViewsCount Views"
                        Log.d("Firestore", "News views updated in Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error updating views count", e)
                    }
            }
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 60 -> "$minutes min ago"
            minutes < 1440 -> "${minutes / 60} hours ago"
            else -> "${minutes / 1440} days ago"
        }
    }

    private fun shareNews() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "${binding.newsTitle.text}\n\n${binding.newsContent.text}")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share news via"))
    }
}