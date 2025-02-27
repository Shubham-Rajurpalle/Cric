package com.cricketApp.cric.home.Shots

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.CardCricShotsBinding

class VideoAdapter(
    private val fragmentManager: FragmentManager,
    private val videoList: List<Video>
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

            root.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("videoUrl", video.videoUrl)
                    putString("id", video.id)
                }

                val fragment = VideoPlayerFragment().apply {
                    arguments = bundle
                }


                fragmentManager.beginTransaction()
                    .replace(R.id.navHost, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    override fun getItemCount() = videoList.size

    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val hours = diff / (1000 * 60 * 60)
        return "$hours hours ago"
    }
}
