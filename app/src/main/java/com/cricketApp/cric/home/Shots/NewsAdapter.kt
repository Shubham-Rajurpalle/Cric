package com.cricketApp.cric.home.Shots

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.CardNewsBinding
import com.google.firebase.firestore.FirebaseFirestore

class NewsAdapter(private var newsList: MutableList<News>, private val context: Context) :
    RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    inner class NewsViewHolder(val binding: CardNewsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = CardNewsBinding.inflate(inflater, parent, false)
        return NewsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val news = newsList[position]

        holder.binding.videoTitle.text = news.title
        holder.binding.viewCount.text = "${news.views} Views"
        holder.binding.timeBefore.text = getTimeAgo(news.timestamp)

        Glide.with(context)
            .load(news.imageUrl)
            .placeholder(R.drawable.loading)
            .into(holder.binding.imagePlayer)

        holder.binding.detailsButton.setOnClickListener {
            updateViewsCount(news, holder.binding.viewCount)
            val intent = Intent(context, NewsDetailsActivity::class.java).apply {
                putExtra("newsId", news.id)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = newsList.size

    fun updateNews(newNewsList: List<News>) {
        this.newsList.clear()
        this.newsList.addAll(newNewsList)
        notifyDataSetChanged()
    }

    private fun updateViewsCount(news: News, viewCountTextView: android.widget.TextView) {
        val firestore = FirebaseFirestore.getInstance()
        val newsRef = firestore.collection("NewsPosts").document(news.id)

        var newViewsCount = news.views + 1
        newsRef.update("views", newViewsCount)
            .addOnSuccessListener {
                news.views = newViewsCount
                viewCountTextView.text = "$newViewsCount Views"
                Log.d("Firestore", "Views updated for ${news.id}")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error updating views", e)
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
}
