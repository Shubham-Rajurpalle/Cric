package com.cricketApp.cric.home.Shots

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cricketApp.cric.databinding.ActivityVideoPlayerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var videoId: String? = null
    private var videoUrl: String? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var userHasLiked = false

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
        checkIfUserLiked(videoId!!) // Check if the current user has already liked this video

        // Click Listeners
        binding.likeButton.setOnClickListener { toggleLike(videoId!!) }
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

    private fun checkIfUserLiked(videoId: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User not logged in
            userHasLiked = false
            return
        }

        val userId = currentUser.uid
        firestore.collection("users")
            .document(userId)
            .collection("likedVideos")
            .document(videoId)
            .get()
            .addOnSuccessListener { document ->
                userHasLiked = document.exists()
                updateLikeButtonAppearance()
            }
            .addOnFailureListener { e ->
                // Log.e("VideoPlayerActivity", "Error checking if user liked video", e)
                userHasLiked = false
                updateLikeButtonAppearance()
            }
    }

    private fun updateLikeButtonAppearance() {
        // Change the appearance of like button based on whether user has liked the video
        // This is a placeholder - you should replace with actual UI changes
        // For example, you might change the button background color or icon
        if (userHasLiked) {
            binding.likeButton.alpha = 1.0f // Make button fully opaque to indicate it's been liked
            // You could also change a drawable resource:
            // binding.likeButton.setImageResource(R.drawable.ic_liked)
        } else {
            binding.likeButton.alpha = 0.5f // Make button semi-transparent to indicate it's not liked
            // binding.likeButton.setImageResource(R.drawable.ic_not_liked)
        }
    }

    private fun toggleLike(videoId: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to like videos", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid

        if (userHasLiked) {
            // User has already liked, so unlike the video
            firestore.collection("videos").document(videoId)
                .update("likes", FieldValue.increment(-1))
                .addOnSuccessListener {
                    // Remove the like record from user's liked videos collection
                    firestore.collection("users")
                        .document(userId)
                        .collection("likedVideos")
                        .document(videoId)
                        .delete()
                        .addOnSuccessListener {
                            userHasLiked = false
                            updateLikeButtonAppearance()

                            // Update the like count in UI
                            val currentText = binding.likeCount.text.toString()
                            val currentLikes = currentText.substringBefore(" ").toIntOrNull() ?: 0
                            if (currentLikes > 0) {
                                binding.likeCount.text = "${currentLikes - 1} "
                            }
                        }
                }
        } else {
            // User hasn't liked yet, so add a like
            firestore.collection("videos").document(videoId)
                .update("likes", FieldValue.increment(1))
                .addOnSuccessListener {
                    // Add a record to user's liked videos collection
                    val likedVideoData = hashMapOf(
                        "timestamp" to FieldValue.serverTimestamp()
                    )

                    firestore.collection("users")
                        .document(userId)
                        .collection("likedVideos")
                        .document(videoId)
                        .set(likedVideoData)
                        .addOnSuccessListener {
                            userHasLiked = true
                            updateLikeButtonAppearance()

                            // Update the like count in UI
                            val currentText = binding.likeCount.text.toString()
                            val currentLikes = currentText.substringBefore(" ").toIntOrNull() ?: 0
                            binding.likeCount.text = "${currentLikes + 1} "
                        }
                }
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