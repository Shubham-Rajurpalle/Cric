package com.cricketApp.cric.home.Shots

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.cricketApp.cric.Meme.MemeMessage
import com.cricketApp.cric.R

class HomeMemePreviewAdapter(
    private val onMemeClick: (MemeMessage) -> Unit
) : RecyclerView.Adapter<HomeMemePreviewAdapter.MemePreviewVH>() {

    private val items = mutableListOf<MemeMessage>()

    fun submitList(list: List<MemeMessage>) {
        items.clear()
        items.addAll(list.take(3))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemePreviewVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_meme_preview, parent, false)
        return MemePreviewVH(view)
    }

    override fun onBindViewHolder(holder: MemePreviewVH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class MemePreviewVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView              = itemView.findViewById(R.id.cardMemePreview)
        private val image: ImageView            = itemView.findViewById(R.id.ivMemePreview)
        private val tvHit: TextView             = itemView.findViewById(R.id.tvMemeHit)
        private val tvTeam: TextView            = itemView.findViewById(R.id.tvMemeTeam)
        private val tvRank: TextView            = itemView.findViewById(R.id.tvMemeRank)
        private val lottieLoader: LottieAnimationView = itemView.findViewById(R.id.lottieLoader)

        fun bind(meme: MemeMessage) {
            tvRank.text = when (adapterPosition) {
                0 -> "#1 🥇"
                1 -> "#2 🥈"
                else -> "#3 🥉"
            }

            tvHit.text  = "🔥 ${meme.hit}"
            tvTeam.text = meme.team.ifEmpty { "🏏" }

            // Show Lottie, hide image while loading
            lottieLoader.visibility = View.VISIBLE
            lottieLoader.playAnimation()
            image.visibility = View.INVISIBLE

            Glide.with(itemView.context)
                .load(meme.memeUrl)
                .centerCrop()
                .placeholder(R.drawable.loading)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        lottieLoader.cancelAnimation()
                        lottieLoader.visibility = View.GONE
                        image.visibility = View.VISIBLE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        lottieLoader.cancelAnimation()
                        lottieLoader.visibility = View.GONE
                        image.visibility = View.VISIBLE
                        return false
                    }
                })
                .into(image)

            card.setOnClickListener { onMemeClick(meme) }
        }
    }
}