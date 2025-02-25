package com.cricketApp.cric
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.CardCricShotsBinding
import java.util.Locale

class VideoAdapter(
    private val context: Context,
    private var videos: List<Cric_shot_video>
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val videoManager: FirebaseVideoManager = FirebaseVideoManager()

    inner class VideoViewHolder(binding: CardCricShotsBinding) : RecyclerView.ViewHolder(binding.root) {
        val imagePlayer: ImageView = binding.imagePlayer
        val textTitle: TextView = binding.textTitle
        val textViewCount: TextView = binding.textViewCount
        val textTimeAgo: TextView = binding.textTimeAgo
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = CardCricShotsBinding.inflate(LayoutInflater.from(context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]

        Glide.with(context)
            .load(video.thumbnailUrl)
            .into(holder.imagePlayer)

        holder.textTitle.text = video.title

        holder.textViewCount.text = "${formatViewCount(video.viewCount)} Views"

        holder.textTimeAgo.text = getTimeAgo(video.uploadTimestamp)

        holder.itemView.setOnClickListener {
            videoManager.incrementViewCount(video.id)

            // Local update - Note: this is a temporary update for UI, the actual data
            // will be refreshed next time loadVideos() is called
            val updatedVideo = videos[position]
            updatedVideo.viewCount =updatedVideo.viewCount+ 1

            // Create a new list with the updated video
            val updatedVideos = videos.toMutableList()
            updatedVideos[position] = updatedVideo

            // Update adapter data
            updateVideos(updatedVideos)

            val intent = Intent(context, VideoPlayer::class.java)
            intent.putExtra("VIDEO_ID", video.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = videos.size


    fun updateVideos(newVideos: List<Cric_shot_video>) {
        this.videos = newVideos
        notifyDataSetChanged()
    }
}

fun getTimeAgo(timeInMillis: Long): String {
    val currentTime = System.currentTimeMillis()
    val timeDiff = currentTime - timeInMillis

    val seconds = timeDiff / 1000

    return when {
        seconds < 60 -> "$seconds seconds ago"
        seconds < 3600 -> {
            val minutes = seconds / 60
            "$minutes minute${if (minutes == 1L) " ago" else "s ago"}"
        }
        seconds < 86400 -> {
            val hours = seconds / 3600
            "$hours hour${if (hours == 1L) " ago" else "s ago"}"
        }
        else -> {
            val days = seconds / 86400
            "$days day${if (days == 1L) " ago" else "s ago"}"
        }
    }
}

fun formatViewCount(viewCount: Long): String {
    return when {
        viewCount < 1000 -> "$viewCount"
        viewCount < 1000000 -> String.format(Locale.getDefault(), "%.1fk", viewCount / 1000.0)
        else -> String.format(Locale.getDefault(), "%.1fM", viewCount / 1000000.0)
    }
}