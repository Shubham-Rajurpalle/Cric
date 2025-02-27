package com.cricketApp.cric.home.Shots

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.cricketApp.cric.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class VideoPlayerFragment : Fragment() {
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private var player: ExoPlayer? = null
    private var videoId: String? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_video_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerView = view.findViewById(R.id.playerView)
        progressBar = view.findViewById(R.id.progressBar)  // Initialize ProgressBar

        videoId = arguments?.getString("id")

        if (videoId.isNullOrEmpty()) {
            Log.e("VideoPlayerFragment", "Video ID is missing")
        } else {
            Log.d("VideoPlayerFragment", "Video ID: $videoId")
            fetchVideoUrl(videoId!!)
            updateViewCount(videoId!!)
        }
    }

    /**
     * Fetches the latest video URL from Firestore before playing it.
     */
    private fun fetchVideoUrl(videoId: String) {
        firestore.collection("videos").document(videoId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val videoUrl = document.getString("videoUrl")
                    if (!videoUrl.isNullOrEmpty()) {
                        Log.d("VideoPlayerFragment", "Fetched video URL: $videoUrl")
                        setupPlayer(videoUrl)
                    } else {
                        Log.e("VideoPlayerFragment", "Video URL is empty in Firestore")
                    }
                } else {
                    Log.e("VideoPlayerFragment", "Video document does not exist")
                }
            }
            .addOnFailureListener { e ->
                Log.e("VideoPlayerFragment", "Error fetching video URL", e)
            }
    }

    /**
     * Sets up ExoPlayer with the given video URL and loading animation.
     */
    private fun setupPlayer(videoUrl: String) {
        Log.d("VideoPlayerFragment", "Setting up ExoPlayer with URL: $videoUrl")

        try {
            val uri = Uri.parse(videoUrl)
            player = ExoPlayer.Builder(requireContext()).build().also { exoPlayer ->
                playerView.player = exoPlayer
                playerView.useController = true
                val mediaItem = MediaItem.fromUri(uri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true

                // Handle buffering state
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                progressBar.visibility = View.VISIBLE  // Show loader
                            }
                            Player.STATE_READY, Player.STATE_ENDED -> {
                                progressBar.visibility = View.GONE  // Hide loader
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerFragment", "Error setting up ExoPlayer", e)
        }
    }

    /**
     * Updates the view count in Firestore.
     */
    private fun updateViewCount(videoId: String) {
        firestore.collection("videos").document(videoId)
            .update("views", FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d("VideoPlayerFragment", "View count updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e("VideoPlayerFragment", "Error updating view count", e)
            }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}
