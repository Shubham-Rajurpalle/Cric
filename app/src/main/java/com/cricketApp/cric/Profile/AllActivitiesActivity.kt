package com.cricketApp.cric.Profile

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.Chat.CommentActivity
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivityAllActivitiesBinding
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AllActivitiesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllActivitiesBinding
    private lateinit var activitiesAdapter: UserActivityAdapter
    private val allActivities = ArrayList<UserActivity>()
    private val TAG = "AdMobActivity"
    private lateinit var adView: AdView

    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllActivitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "All Activities"

        // Setup RecyclerView
        setupRecyclerView()

        // Load Activities
        loadAllUserActivities()

        MobileAds.initialize(this) {
            loadBannerAd()
        }
    }

    private fun loadBannerAd() {
        adView = binding.adView
        val adRequest = AdRequest.Builder().build()

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Log.d(TAG, "Ad loaded")
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                // Log.e(TAG, "Ad failed to load: $loadAdError")
            }
        }

        adView.loadAd(adRequest)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        // Enable long press when setting up the adapter
        activitiesAdapter = UserActivityAdapter(
            allActivities,
            onActivityClick = { activity ->
                // Handle activity click
                when (activity.type) {
                    UserActivityType.CHAT -> {
                        navigateToComments(activity.id, "chat")
                    }
                    UserActivityType.MEME -> {
                        navigateToComments(activity.id, "meme")
                    }
                    UserActivityType.POLL -> {
                        navigateToComments(activity.id, "poll")
                    }
                    UserActivityType.COMMENT -> {
                        val parentType = activity.additionalData?.get("parentType") as? String ?: "chat"
                        val parentId = activity.additionalData?.get("parentId") as? String ?: ""
                        navigateToComments(parentId, parentType)
                    }
                }
            },
            enableLongPress = true  // Enable long-press functionality
        )

        binding.recyclerViewAllActivities.apply {
            layoutManager = LinearLayoutManager(this@AllActivitiesActivity)
            adapter = activitiesAdapter
        }
    }

    private fun navigateToComments(itemId: String, itemType: String) {
        val intent = Intent(this, CommentActivity::class.java).apply {
            putExtra("ITEM_ID", itemId)
            putExtra("ITEM_TYPE", itemType)
        }
        startActivity(intent)
    }

    private fun loadAllUserActivities() {
        val userId = currentUser?.uid ?: return

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewAllActivities.visibility = View.GONE

        // Clear existing activities
        allActivities.clear()

        // First load all chats
        loadUserChats(userId) {
            // Then load memes
            loadUserMemes(userId) {
                // Then load polls
                loadUserPolls(userId) {
                    // Finally load comments and update UI
                    loadUserComments(userId) {
                        // Sort activities by timestamp (newest first)
                        allActivities.sortByDescending { it.timestamp }

                        // Update adapter
                        activitiesAdapter.notifyDataSetChanged()

                        // Hide loading, show content
                        binding.progressBar.visibility = View.GONE
                        binding.recyclerViewAllActivities.visibility = View.VISIBLE

                        // Show empty state if needed
                        if (allActivities.isEmpty()) {
                            binding.emptyStateLayout.visibility = View.VISIBLE
                        } else {
                            binding.emptyStateLayout.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun loadUserChats(userId: String, onComplete: () -> Unit) {
        val chatsRef = FirebaseDatabase.getInstance().getReference("NoBallZone/chats")

        chatsRef.orderByChild("senderId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (chatSnapshot in snapshot.children) {
                        val chat = FirebaseDataHelper.getChatMessageFromSnapshot(chatSnapshot)
                        chat?.let {
                            val activity = UserActivity(
                                id = it.id,
                                type = UserActivityType.CHAT,
                                username = it.senderName,
                                userId = it.senderId,
                                team = it.team,
                                content = it.message,
                                imageUrl = it.imageUrl,
                                timestamp = it.timestamp,
                                hits = it.hit,
                                misses = it.miss,
                                reactions = it.reactions
                            )
                            allActivities.add(activity)
                        }
                    }
                    onComplete()
                }

                override fun onCancelled(error: DatabaseError) {
                //    Log.e("AllActivitiesActivity", "Error loading chats", error.toException())
                    onComplete()
                }
            })
    }

    private fun loadUserMemes(userId: String, onComplete: () -> Unit) {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        memesRef.orderByChild("senderId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (memeSnapshot in snapshot.children) {
                        val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                        meme?.let {
                            val activity = UserActivity(
                                id = it.id,
                                type = UserActivityType.MEME,
                                username = it.senderName,
                                userId = it.senderId,
                                team = it.team,
                                content = "",  // Memes don't have text content
                                imageUrl = it.memeUrl,
                                timestamp = it.timestamp,
                                hits = it.hit,
                                misses = it.miss,
                                reactions = it.reactions
                            )
                            allActivities.add(activity)
                        }
                    }
                    onComplete()
                }

                override fun onCancelled(error: DatabaseError) {
                //    Log.e("AllActivitiesActivity", "Error loading memes", error.toException())
                    onComplete()
                }
            })
    }

    private fun loadUserPolls(userId: String, onComplete: () -> Unit) {
        val pollsRef = FirebaseDatabase.getInstance().getReference("NoBallZone/polls")

        pollsRef.orderByChild("senderId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (pollSnapshot in snapshot.children) {
                        val poll = FirebaseDataHelper.getPollMessageFromSnapshot(pollSnapshot)
                        poll?.let {
                            val activity = UserActivity(
                                id = it.id,
                                type = UserActivityType.POLL,
                                username = it.senderName,
                                userId = it.senderId,
                                team = it.team,
                                content = it.question,
                                timestamp = it.timestamp,
                                hits = it.hit,
                                misses = it.miss,
                                reactions = it.reactions,
                                additionalData = mapOf<String, Any>(
                                    "options" to it.options as Any,
                                    "voters" to (it.voters ?: mapOf<String, String>()) as Any
                                )
                            )
                            allActivities.add(activity)
                        }
                    }
                    onComplete()
                }

                override fun onCancelled(error: DatabaseError) {
                //    Log.e("AllActivitiesActivity", "Error loading polls", error.toException())
                    onComplete()
                }
            })
    }

    private fun loadUserComments(userId: String, onComplete: () -> Unit) {
        // Load comments from chats, memes, and polls
        val commentSources = listOf(
            Pair("NoBallZone/chats", "chat"),
            Pair("NoBallZone/memes", "meme"),
            Pair("NoBallZone/polls", "poll")
        )

        var completedSources = 0

        for ((path, parentType) in commentSources) {
            loadCommentsFromCollection(path, userId, parentType) {
                completedSources++
                if (completedSources == commentSources.size) {
                    onComplete()
                }
            }
        }
    }

    private fun loadCommentsFromCollection(path: String, userId: String, parentType: String, onComplete: () -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference(path)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (itemSnapshot in snapshot.children) {
                    val itemId = itemSnapshot.key ?: continue
                    val commentsSnapshot = itemSnapshot.child("comments")

                    for (commentSnapshot in commentsSnapshot.children) {
                        val commentSenderId = commentSnapshot.child("senderId").getValue(String::class.java) ?: continue

                        if (commentSenderId == userId) {
                            val commentId = commentSnapshot.key ?: continue
                            val senderName = commentSnapshot.child("senderName").getValue(String::class.java) ?: "Anonymous"
                            val team = commentSnapshot.child("team").getValue(String::class.java) ?: "No Team"
                            val message = commentSnapshot.child("message").getValue(String::class.java) ?: ""
                            val imageUrl = commentSnapshot.child("imageUrl").getValue(String::class.java) ?: ""
                            val timestamp = commentSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                            val activity = UserActivity(
                                id = commentId,
                                type = UserActivityType.COMMENT,
                                username = senderName,
                                userId = userId,
                                team = team,
                                content = message,
                                imageUrl = imageUrl,
                                timestamp = timestamp,
                                additionalData = mapOf(
                                    "parentId" to itemId,
                                    "parentType" to parentType
                                )
                            )
                            allActivities.add(activity)
                        }
                    }
                }
                onComplete()
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("AllActivitiesActivity", "Error loading comments from $path", error.toException())
                onComplete()
            }
        })
    }
}