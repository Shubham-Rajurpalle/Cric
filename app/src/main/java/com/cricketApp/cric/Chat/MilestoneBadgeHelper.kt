package com.cricketApp.cric.Utils

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.cricketApp.cric.R

/**
 * Helper class for adding visual milestone badges to content items
 * that have reached significant engagement thresholds (100+ hits, reactions, etc.)
 */
object MilestoneBadgeHelper {

    // Thresholds for displaying badges
    private const val TRENDING_THRESHOLD = 100
    private const val FIRE_THRESHOLD = 200
    private const val VIRAL_THRESHOLD = 500

    /**
     * Check if content has reached a milestone worthy of a badge
     * @param hit The hit count
     * @param miss The miss count
     * @param reactions Map of reaction counts
     * @return True if any metric has reached the trending threshold
     */
    fun hasMilestone(hit: Int, miss: Int, reactions: Map<String, Int>): Boolean {
        // Check if hit count reached threshold
        if (hit >= TRENDING_THRESHOLD) return true

        // Check if miss count reached threshold
        if (miss >= TRENDING_THRESHOLD) return true

        // Check if any reaction reached threshold
        for (count in reactions.values) {
            if (count >= TRENDING_THRESHOLD) return true
        }

        return false
    }

    /**
     * Update milestone badge visibility and text for a content item
     * @param badgeView The badge view to update
     * @param badgeText The text view within the badge (can be null)
     * @param hit The hit count
     * @param miss The miss count
     * @param reactions Map of reaction counts
     */
    fun updateMilestoneBadge(
        badgeView: View,
        badgeText: TextView? = null,
        hit: Int = 0,
        miss: Int = 0,
        reactions: Map<String, Int> = emptyMap()
    ) {
        // Find the highest count
        val highestCount = maxOf(
            hit,
            miss,
            reactions.values.maxOrNull() ?: 0
        )

        // Determine badge level and text
        when {
            highestCount >= VIRAL_THRESHOLD -> {
                badgeView.visibility = View.VISIBLE
                badgeText?.text = "VIRAL"
                badgeView.setBackgroundResource(R.drawable.badge_viral_background)
            }
            highestCount >= FIRE_THRESHOLD -> {
                badgeView.visibility = View.VISIBLE
                badgeText?.text = "FIRE"
                badgeView.setBackgroundResource(R.drawable.badge_fire_background)
            }
            highestCount >= TRENDING_THRESHOLD -> {
                badgeView.visibility = View.VISIBLE
                badgeText?.text = "TRENDING"
                badgeView.setBackgroundResource(R.drawable.badge_trending_background)
            }
            else -> {
                badgeView.visibility = View.GONE
            }
        }
    }

    /**
     * Update milestone badge icon for a content item (for layouts that use an icon instead of text)
     * @param badgeIcon The badge icon view to update
     * @param hit The hit count
     * @param miss The miss count
     * @param reactions Map of reaction counts
     */
    fun updateMilestoneBadgeIcon(
        badgeIcon: ImageView,
        hit: Int = 0,
        miss: Int = 0,
        reactions: Map<String, Int> = emptyMap()
    ) {
        // Find the highest count
        val highestCount = maxOf(
            hit,
            miss,
            reactions.values.maxOrNull() ?: 0
        )

        // Determine badge level and icon
        when {
            highestCount >= VIRAL_THRESHOLD -> {
                badgeIcon.visibility = View.VISIBLE
                badgeIcon.setImageResource(R.drawable.ic_viral)
            }
            highestCount >= FIRE_THRESHOLD -> {
                badgeIcon.visibility = View.VISIBLE
                badgeIcon.setImageResource(R.drawable.ic_fire)
            }
            highestCount >= TRENDING_THRESHOLD -> {
                badgeIcon.visibility = View.VISIBLE
                badgeIcon.setImageResource(R.drawable.ic_trending)
            }
            else -> {
                badgeIcon.visibility = View.GONE
            }
        }
    }
}