package com.cricketApp.cric.Chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.Chat.ChatFragment
import com.cricketApp.cric.Meme.MemeFragment
import com.cricketApp.cric.Notifications.NotificationItem
import com.cricketApp.cric.NotificationsAdapter
import com.cricketApp.cric.R
import com.cricketApp.cric.Utils.NotificationService
import com.cricketApp.cric.databinding.ActivityNotificationBinding
import com.cricketApp.cric.home.Home
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var adapter: NotificationsAdapter
    private lateinit var notificationService: NotificationService
    private val notifications = ArrayList<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notificationService = NotificationService(this)

        setupRecyclerView()
        setupActionBar()
        loadNotifications()

        // Setup refresh functionality
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadNotifications()
        }

        // Setup mark all as read button
        binding.buttonMarkAllRead.setOnClickListener {
            notificationService.markAllNotificationsAsRead()
            // Update UI to reflect all notifications are read
            for (notification in notifications) {
                notification.read = true
            }
            adapter.notifyDataSetChanged()
        }

        // Setup back button
        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            title = "Notifications"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter(notifications) { notification ->
            // Mark as read when clicked
            if (!notification.read) {
                notificationService.markNotificationAsRead(notification.id)
                notification.read = true
                adapter.notifyItemChanged(notifications.indexOf(notification))
            }

            // Handle navigation based on content type
            navigateToContent(notification)
        }

        binding.recyclerViewNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            adapter = this@NotificationActivity.adapter
        }
    }

    private fun loadNotifications() {
        binding.progressBar.visibility = View.VISIBLE
        binding.noNotificationsText.visibility = View.GONE

        val notificationsRef = FirebaseDatabase.getInstance().getReference("Notifications")

        // Get notifications from the last 24 hours
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        notificationsRef.orderByChild("timestamp").startAt(oneDayAgo.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    notifications.clear()

                    for (notificationSnapshot in snapshot.children) {
                        try {
                            val notificationId = notificationSnapshot.key ?: continue
                            val contentType = notificationSnapshot.child("contentType").getValue(String::class.java) ?: ""
                            val contentId = notificationSnapshot.child("contentId").getValue(String::class.java) ?: ""
                            val senderName = notificationSnapshot.child("senderName").getValue(String::class.java) ?: ""
                            val team = notificationSnapshot.child("team").getValue(String::class.java) ?: ""
                            val message = notificationSnapshot.child("message").getValue(String::class.java) ?: ""
                            val reactionCategory = notificationSnapshot.child("reactionCategory").getValue(String::class.java) ?: ""
                            val reactionValue = notificationSnapshot.child("reactionValue").getValue(String::class.java) ?: ""
                            val count = notificationSnapshot.child("count").getValue(Int::class.java) ?: 0
                            val timestamp = notificationSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            val read = notificationSnapshot.child("read").getValue(Boolean::class.java) ?: false

                            val notification = NotificationItem(
                                id = notificationId,
                                contentType = contentType,
                                contentId = contentId,
                                senderName = senderName,
                                team = team,
                                message = message,
                                reactionCategory = reactionCategory,
                                reactionValue = reactionValue,
                                count = count,
                                timestamp = timestamp,
                                read = read
                            )

                            notifications.add(notification)
                        } catch (e: Exception) {
                            Log.e("NotificationsActivity", "Error parsing notification: ${e.message}")
                        }
                    }

                    // Sort by timestamp (newest first)
                    notifications.sortByDescending { it.timestamp }

                    // Update UI
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false

                    if (notifications.isEmpty()) {
                        binding.noNotificationsText.visibility = View.VISIBLE
                    } else {
                        binding.noNotificationsText.visibility = View.GONE
                    }

                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("NotificationsActivity", "Error loading notifications: ${error.message}")
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.noNotificationsText.visibility = View.VISIBLE
                    binding.noNotificationsText.text = "Error loading notifications"
                }
            })
    }

    private fun navigateToContent(notification: NotificationItem) {
        // Create an intent for the Home activity with navigation data
        val intent = Intent(this, Home::class.java).apply {
            putExtra("NOTIFICATION_CONTENT_TYPE", notification.contentType)
            putExtra("NOTIFICATION_CONTENT_ID", notification.contentId)
            putExtra("SHOULD_NAVIGATE", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}