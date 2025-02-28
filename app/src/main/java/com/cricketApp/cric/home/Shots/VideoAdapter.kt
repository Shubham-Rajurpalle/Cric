package com.cricketApp.cric.home.Shots

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.cricketApp.cric.databinding.CardCricShotsBinding

class VideoAdapter(
    private var videoList: MutableList<Video>,
    private val onVideoClick: (Video) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(val binding: CardCricShotsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = CardCricShotsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videoList[position]
        with(holder.binding) {
            // Show ProgressBar before loading image
            loadingProgress.visibility = View.VISIBLE
            imagePlayer.visibility = View.INVISIBLE

            // Glide with Listener to handle loading states
            Glide.with(root.context)
                .load(video.thumbnailUrl)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        loadingProgress.visibility = View.GONE // Hide progress if failed
                        imagePlayer.visibility = View.VISIBLE  // Still show a placeholder
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        loadingProgress.visibility = View.GONE // Hide progress when loaded
                        imagePlayer.visibility = View.VISIBLE
                        return false
                    }
                })
                .into(imagePlayer)

            // Set other text views
            videoTitle.text = video.title
            viewCount.text = "${video.views} Views"
            timeBefore.text = getTimeAgo(video.timestamp)

            root.setOnClickListener { onVideoClick(video) }
        }
    }

    override fun getItemCount() = videoList.size

    fun updateData(newVideos: List<Video>) {
        videoList.clear()
        videoList.addAll(newVideos)
        notifyDataSetChanged()
    }

    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val hours = diff / (1000 * 60 * 60)
        return "$hours hours ago"
    }
}
