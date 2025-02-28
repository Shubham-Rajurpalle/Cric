package com.cricketApp.cric.home.Shots

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.CardCricShotsBinding

class VideoAdapter(
    private var videoList: MutableList<Video>, // Change to mutable
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
            Glide.with(root.context).load(video.thumbnailUrl).into(imagePlayer)
            videoTitle.text = video.title
            viewCount.text = "${video.views} Views"
            timeBefore.text = getTimeAgo(video.timestamp)

            root.setOnClickListener { onVideoClick(video) }
        }
    }

    override fun getItemCount() = videoList.size

    // ✅ Function to update the data properly
    fun updateData(newVideos: List<Video>) {
        videoList.clear()
        videoList.addAll(newVideos)
        notifyDataSetChanged() // Refresh RecyclerView
    }

    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val hours = diff / (1000 * 60 * 60)
        return "$hours hours ago"
    }
}
