package com.cricketApp.cric.Profile

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cricketApp.cric.Chat.CommentActivity
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivityAllActivitiesBinding
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
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

    // Full sorted list (loaded once, paginated in memory)
    private val allActivitiesFull = ArrayList<UserActivity>()

    // Currently displayed
    private val displayedActivities = ArrayList<UserActivity>()

    private val PAGE_SIZE = 15
    private var currentPage = 0
    private var isLoadingMore = false
    private var allSourcesLoaded = 0  // track 4 sources: chats, memes, polls, comments
    private val TOTAL_SOURCES = 4

    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllActivitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "All Activities"

        setupRecyclerView()
        loadAllUserActivities()

        MobileAds.initialize(this) {
            val adRequest = AdRequest.Builder().build()
            binding.adView.adListener = object : AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) {}
            }
            binding.adView.loadAd(adRequest)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        activitiesAdapter = UserActivityAdapter(
            displayedActivities,
            onActivityClick = { activity ->
                val intent = Intent(this, CommentActivity::class.java)
                when (activity.type) {
                    UserActivityType.COMMENT -> {
                        intent.putExtra(
                            "ITEM_ID",
                            activity.additionalData?.get("parentId") as? String ?: ""
                        )
                        intent.putExtra(
                            "ITEM_TYPE",
                            activity.additionalData?.get("parentType") as? String ?: "chat"
                        )
                    }

                    else -> {
                        intent.putExtra("ITEM_ID", activity.id)
                        intent.putExtra("ITEM_TYPE", activity.type.name.lowercase())
                    }
                }
                startActivity(intent)
            },
            enableLongPress = true
        )

        val layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAllActivities.layoutManager = layoutManager
        binding.recyclerViewAllActivities.adapter = activitiesAdapter

        // Infinite scroll — load next page when near bottom
        binding.recyclerViewAllActivities.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || isLoadingMore) return

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisible = layoutManager.findFirstVisibleItemPosition()

                if ((visibleItemCount + firstVisible) >= totalItemCount - 3) {
                    loadNextPage()
                }
            }
        })
    }

    private fun loadAllUserActivities() {
        val userId = currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewAllActivities.visibility = View.GONE
        allActivitiesFull.clear()
        allSourcesLoaded = 0

        loadUserChats(userId)
        loadUserMemes(userId)
        loadUserPolls(userId)
        loadUserComments(userId)
    }

    private fun onSourceLoaded() {
        allSourcesLoaded++
        if (allSourcesLoaded == TOTAL_SOURCES) {
            // All sources done — sort everything and show first page
            allActivitiesFull.sortByDescending { it.timestamp }
            currentPage = 0
            displayedActivities.clear()

            binding.progressBar.visibility = View.GONE
            binding.recyclerViewAllActivities.visibility = View.VISIBLE

            if (allActivitiesFull.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                loadNextPage()
            }
        }
    }

    private fun loadNextPage() {
        if (isLoadingMore) return
        val start = currentPage * PAGE_SIZE
        if (start >= allActivitiesFull.size) return  // No more data

        isLoadingMore = true
        val end = minOf(start + PAGE_SIZE, allActivitiesFull.size)
        val nextItems = allActivitiesFull.subList(start, end)

        val insertStart = displayedActivities.size
        displayedActivities.addAll(nextItems)
        activitiesAdapter.notifyItemRangeInserted(insertStart, nextItems.size)

        currentPage++
        isLoadingMore = false
    }

    private fun loadUserChats(userId: String) {
        FirebaseDatabase.getInstance().getReference("NoBallZone/chats")
            .orderByChild("senderId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (s in snapshot.children) {
                        FirebaseDataHelper.getChatMessageFromSnapshot(s)?.let {
                            allActivitiesFull.add(
                                UserActivity(
                                    it.id,
                                    UserActivityType.CHAT,
                                    it.senderName,
                                    it.senderId,
                                    it.team,
                                    it.message,
                                    it.imageUrl ?: "",
                                    it.timestamp,
                                    it.hit,
                                    it.miss,
                                    it.reactions
                                )
                            )
                        }
                    }
                    onSourceLoaded()
                }

                override fun onCancelled(error: DatabaseError) {
                    onSourceLoaded()
                }
            })
    }

    private fun loadUserMemes(userId: String) {
        FirebaseDatabase.getInstance().getReference("NoBallZone/memes")
            .orderByChild("senderId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (s in snapshot.children) {
                        FirebaseDataHelper.getMemeMessageFromSnapshot(s)?.let {
                            allActivitiesFull.add(
                                UserActivity(
                                    it.id,
                                    UserActivityType.MEME,
                                    it.senderName,
                                    it.senderId,
                                    it.team,
                                    "",
                                    it.memeUrl,
                                    it.timestamp,
                                    it.hit,
                                    it.miss,
                                    it.reactions
                                )
                            )
                        }
                    }
                    onSourceLoaded()
                }

                override fun onCancelled(error: DatabaseError) {
                    onSourceLoaded()
                }
            })
    }

    private fun loadUserPolls(userId: String) {
        FirebaseDatabase.getInstance().getReference("NoBallZone/polls")
            .orderByChild("senderId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (s in snapshot.children) {
                        FirebaseDataHelper.getPollMessageFromSnapshot(s)?.let {
                            allActivitiesFull.add(
                                UserActivity(
                                    it.id,
                                    UserActivityType.POLL,
                                    it.senderName,
                                    it.senderId,
                                    it.team,
                                    it.question,
                                    "",
                                    it.timestamp,
                                    it.hit,
                                    it.miss,
                                    it.reactions,
                                    mapOf(
                                        "options" to (it.options as Any),
                                        "voters" to ((it.voters ?: mapOf<String, String>()) as Any)
                                    )
                                )
                            )
                        }
                    }
                    onSourceLoaded()
                }

                override fun onCancelled(error: DatabaseError) {
                    onSourceLoaded()
                }
            })
    }

    private fun loadUserComments(userId: String) {
        FirebaseDatabase.getInstance()
            .getReference("userComments/$userId")
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (s in snapshot.children) {
                        allActivitiesFull.add(
                            UserActivity(
                                id = s.key ?: continue,
                                type = UserActivityType.COMMENT,
                                username = s.child("senderName").getValue(String::class.java)
                                    ?: "Anonymous",
                                userId = userId,
                                team = s.child("team").getValue(String::class.java) ?: "",
                                content = s.child("message").getValue(String::class.java) ?: "",
                                imageUrl = s.child("imageUrl").getValue(String::class.java) ?: "",
                                timestamp = s.child("timestamp").getValue(Long::class.java) ?: 0L,
                                additionalData = mapOf(
                                    "parentId" to (s.child("parentId").getValue(String::class.java)
                                        ?: ""),
                                    "parentType" to (s.child("parentType")
                                        .getValue(String::class.java) ?: "chat")
                                )
                            )
                        )
                    }
                    onSourceLoaded()
                }

                override fun onCancelled(error: DatabaseError) {
                    onSourceLoaded()
                }
            })
    }
}