package com.cricketApp.cric.home.Shots

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.cricketApp.cric.databinding.ActivityVideoPlayerBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var videoId: String? = null
    private var videoUrl: String? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoId = intent.getStringExtra("id")
        videoUrl = intent.getStringExtra("videoUrl")

        if (videoId.isNullOrEmpty() || videoUrl.isNullOrEmpty()) {
            Log.e("VideoPlayerActivity", "Video ID or URL is missing")
            finish()
            return
        }

        setupPlayer(videoUrl!!)
        updateViewCount(videoId!!)
        fetchCounts(videoId!!) // Fetch initial like/share counts

        // Click Listeners
        binding.likeButton.setOnClickListener { updateLikeCount(videoId!!) }
        binding.shareButton.setOnClickListener { shareVideo(videoUrl!!, videoId!!) }
        binding.backButton.setOnClickListener { onBackPressed() }
    }

    private fun setupPlayer(videoUrl: String) {
        try {
            val uri = Uri.parse(videoUrl)
            player = ExoPlayer.Builder(this).build().also { exoPlayer ->
                binding.playerView.player = exoPlayer
                binding.playerView.useController = true
                val mediaItem = MediaItem.fromUri(uri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true

                // Buffering indicator
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        binding.progressBar.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "Error setting up ExoPlayer", e)
        }
    }

    private fun updateViewCount(videoId: String) {
        firestore.collection("videos").document(videoId)
            .update("views", FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d("VideoPlayerActivity", "View count updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e("VideoPlayerActivity", "Error updating view count", e)
            }
    }

    private fun fetchCounts(videoId: String) {
        firestore.collection("videos").document(videoId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val likes = document.getLong("likes") ?: 0
                    val shares = document.getLong("shares") ?: 0
                    binding.likeCount.text = likes.toString()
                    binding.shareCount.text = shares.toString()
                }
            }
            .addOnFailureListener { e ->
                Log.e("VideoPlayerActivity", "Error fetching counts", e)
            }
    }

    private fun updateLikeCount(videoId: String) {
        firestore.collection("videos").document(videoId)
            .update("likes", FieldValue.increment(1))
            .addOnSuccessListener {
                val currentLikes = binding.likeCount.text.toString().toIntOrNull() ?: 0
                binding.likeCount.text = (currentLikes + 1).toString()
            }
            .addOnFailureListener { e ->
                Log.e("VideoPlayerActivity", "Error updating like count", e)
            }
    }

    private fun shareVideo(videoUrl: String, videoId: String) {
        firestore.collection("videos").document(videoId)
            .update("shares", FieldValue.increment(1))
            .addOnSuccessListener {
                val currentShares = binding.shareCount.text.toString().toIntOrNull() ?: 0
                binding.shareCount.text = (currentShares + 1).toString()
            }
            .addOnFailureListener { e ->
                Log.e("VideoPlayerActivity", "Error updating share count", e)
            }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this amazing video: $videoUrl")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Video"))
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
