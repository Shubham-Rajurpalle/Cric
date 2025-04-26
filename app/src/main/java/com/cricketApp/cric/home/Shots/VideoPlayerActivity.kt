package com.cricketApp.cric.home.Shots

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cricketApp.cric.databinding.ActivityVideoPlayerBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var videoId: String? = null
    private var videoUrl: String? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var hasLiked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoId = intent.getStringExtra("id")
        videoUrl = intent.getStringExtra("videoUrl")

        if (videoId.isNullOrEmpty() || videoUrl.isNullOrEmpty()) {
            finish()
            return
        }

        setupPlayer(videoUrl!!)
        updateViewCount(videoId!!)
        fetchCounts(videoId!!) // Fetch initial like/share counts

        // Check if user has already liked this video using SharedPreferences
        checkIfVideoLiked(videoId!!)

        // Click Listeners
        binding.likeButton.setOnClickListener {
            if (!hasLiked) {
                updateLikeCount(videoId!!)
            }
        }
        binding.shareButton.setOnClickListener { shareVideo(videoUrl!!, videoId!!) }
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Handling back press with OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
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
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        binding.progressBar.visibility = if (playbackState == Player.STATE_BUFFERING)
                            View.VISIBLE
                        else
                            View.GONE
                    }
                })
            }
        } catch (e: Exception) {
            // Log.e("VideoPlayerActivity", "Error setting up ExoPlayer", e)
        }
    }

    private fun updateViewCount(videoId: String) {
        firestore.collection("videos").document(videoId)
            .update("views", FieldValue.increment(1))
            .addOnSuccessListener {
                // Log.d("VideoPlayerActivity", "View count updated successfully")
            }
            .addOnFailureListener { e ->
                // Log.e("VideoPlayerActivity", "Error updating view count", e)
            }
    }

    private fun fetchCounts(videoId: String) {
        firestore.collection("videos").document(videoId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val likes = document.getLong("likes") ?: 0
                    val shares = document.getLong("shares") ?: 0
                    binding.likeCount.text = "$likes "
                    binding.shareCount.text = "$shares "
                }
            }
            .addOnFailureListener { e ->
                // Log.e("VideoPlayerActivity", "Error fetching counts", e)
            }
    }

    private fun checkIfVideoLiked(videoId: String) {
        // Get SharedPreferences
        val sharedPrefs = getSharedPreferences("video_likes", Context.MODE_PRIVATE)

        // Check if this video ID exists in SharedPreferences
        hasLiked = sharedPrefs.getBoolean(videoId, false)

        // Update UI based on like status
        updateLikeButtonAppearance()
    }

    private fun updateLikeButtonAppearance() {
        if (hasLiked) {
            // Change the appearance of the like button when already liked
            binding.likeButton.alpha = 0.5f
            // You could also change the drawable if you have liked/unliked images
            // binding.likeButton.setImageResource(R.drawable.ic_liked)
        } else {
            binding.likeButton.alpha = 1.0f
            // binding.likeButton.setImageResource(R.drawable.ic_like)
        }
    }

    private fun updateLikeCount(videoId: String) {
        // First update UI immediately
        val currentText = binding.likeCount.text.toString()
        val currentLikes = currentText.substringBefore(" ").toIntOrNull() ?: 0
        binding.likeCount.text = "${currentLikes + 1} "

        // Mark as liked locally
        hasLiked = true
        updateLikeButtonAppearance()

        // Save like state to SharedPreferences
        val sharedPrefs = getSharedPreferences("video_likes", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putBoolean(videoId, true)
        editor.apply()

        // Update like count in Firebase
        firestore.collection("videos").document(videoId)
            .update("likes", FieldValue.increment(1))
            .addOnFailureListener { e ->
                // On failure, revert the UI changes
                binding.likeCount.text = "$currentLikes "
                hasLiked = false
                updateLikeButtonAppearance()

                // Also revert SharedPreferences
                val prefsEditor = sharedPrefs.edit()
                prefsEditor.putBoolean(videoId, false)
                prefsEditor.apply()

                // Log.e("VideoPlayerActivity", "Error updating like count", e)
            }
    }

    private fun shareVideo(videoUrl: String, videoId: String) {
        firestore.collection("videos").document(videoId)
            .update("shares", FieldValue.increment(1))
            .addOnSuccessListener {
                val currentText = binding.shareCount.text.toString()
                val currentShares = currentText.substringBefore(" ").toIntOrNull() ?: 0
                binding.shareCount.text = "${currentShares + 1} "
            }
            .addOnFailureListener { e ->
                // Log.e("VideoPlayerActivity", "Error updating share count", e)
            }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this amazing cricket video: $videoUrl")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Video"))
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}