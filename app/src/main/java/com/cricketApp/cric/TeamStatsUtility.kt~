package com.cricketApp.cric.Utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

/**
 * Utility class to handle team statistics updates
 * This ensures that when a user interacts with content (hit/miss),
 * both the content item and the team's aggregate statistics are updated
 */
object TeamStatsUtility {

    private const val TAG = "TeamStatsUtility"

    /**
     * Update hit or miss count for a team
     *
     * @param team Team identifier (e.g., "CSK", "MI", "DC")
     * @param isHit True if updating hit count, false for miss count
     * @param increment True to increment, false to decrement
     */
    fun updateTeamStats(team: String, isHit: Boolean, increment: Boolean = true) {
        // Convert team identifier to lowercase to match Firebase structure
        val teamId = team.lowercase()

        // Reference to the team in Firebase
        val teamRef = FirebaseDatabase.getInstance().getReference("teams/$teamId")

        // Field to update (hits or misses)
        val field = if (isHit) "hits" else "misses"

        // Value to increment or decrement by
        val value = if (increment) 1 else -1

        // Use transaction to handle concurrent updates safely
        teamRef.child(field).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                var count = mutableData.getValue(Int::class.java) ?: 0
                count += value

                // Ensure we don't go below zero
                if (count < 0) count = 0

                mutableData.value = count
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Error updating team stats: ${error.message}")
                } else if (committed) {
                    Log.d(TAG, "Team $teamId ${field} updated successfully")
                }
            }
        })
    }

    /**
     * Update content item hit/miss and team stats in a single call
     *
     * @param contentType Type of content ("chat", "meme", "poll", "comment")
     * @param contentId ID of the content item
     * @param team Team identifier
     * @param isHit True if updating hit count, false for miss count
     * @param commentId Optional comment ID if updating comment stats
     * @param parentId Optional parent ID if updating comment stats
     * @param callback Optional callback with success status and new value
     */
    fun updateContentAndTeamStats(
        contentType: String,
        contentId: String,
        team: String,
        isHit: Boolean,
        commentId: String? = null,
        parentId: String? = null,
        callback: ((success: Boolean, newValue: Int) -> Unit)? = null
    ) {
        // Determine the path based on content type and whether it's a comment
        val path = when {
            commentId != null && parentId != null -> {
                // This is a comment on a parent item
                "NoBallZone/$contentType/$parentId/comments/$commentId"
            }
            else -> {
                // This is a main content item (chat, meme, poll)
                "NoBallZone/$contentType/$contentId"
            }
        }

        // Field to update (hit or miss)
        val field = if (isHit) "hit" else "miss"

        // Reference to the content item
        val contentRef = FirebaseDatabase.getInstance().getReference("$path/$field")

        // Use transaction to handle concurrent updates safely
        contentRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentValue = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = currentValue + 1
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Error updating content stats: ${error.message}")
                    callback?.invoke(false, 0)
                } else if (committed && currentData != null) {
                    val newValue = currentData.getValue(Int::class.java) ?: 0

                    // Now update the team stats - passing the original team name,
                    // updateTeamStats will handle converting it to lowercase
                    updateTeamStats(team, isHit)

                    callback?.invoke(true, newValue)
                    Log.d(TAG, "Content $contentId $field updated successfully")
                } else {
                    callback?.invoke(false, 0)
                }
            }
        })
    }
}