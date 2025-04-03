package com.cricketApp.cric.Utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cricketApp.cric.Chat.NotificationActivity
import com.cricketApp.cric.R
import com.cricketApp.cric.home.Home
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Service to handle content milestone notifications
 * Listens for notifications when content reaches 100+ reactions/ratings
 * Shows system notifications to users
 */
class NotificationService(private val context: Context) {

    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "content_milestones"
        private const val CHANNEL_NAME = "Content Milestones"
        private const val CHANNEL_DESCRIPTION = "Notifications for popular content"

        // Request code for pending intents
        private const val REQUEST_CODE_BASE = 1000

        // Cooldown period between notifications (15 minutes in milliseconds)
        private const val NOTIFICATION_COOLDOWN = 60*5*1000 // 15 minutes

        // Map to track when we last sent a notification for a specific content item
        private val notificationTimestamps = ConcurrentHashMap<String, Long>()
    }

    private var notificationId = 100

    /**
     * Check if the app has notification permission
     */
    private fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Initialize notification channels (required for Android 8.0+)
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH  // Changed to HIGH to ensure notifications appear
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

        //    Log.d(TAG, "Created notification channel: $CHANNEL_ID")
        }
    }

    /**
     * Start listening for new notifications in Firebase
     */
    fun startListening() {
        val database = FirebaseDatabase.getInstance()
        val notificationsRef = database.getReference("Notifications")

        // First get a count of all notifications for debugging
        notificationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
            //    Log.d(TAG, "Found ${snapshot.childrenCount} total notifications in database")
            }

            override fun onCancelled(error: DatabaseError) {
             //   Log.e(TAG, "Error checking total notifications: ${error.message}")
            }
        })

        // Listen for new notifications with ChildEventListener
        notificationsRef.orderByChild("timestamp")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    // Log new notification detected
                //    Log.d(TAG, "New notification detected with ID: ${snapshot.key}")

                    // Process new notification
                    processNotification(snapshot)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    // Not handling changes
                //    Log.d(TAG, "Notification changed: ${snapshot.key}")
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    // Not handling removals
                //    Log.d(TAG, "Notification removed: ${snapshot.key}")
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    // Not handling moves
                }

                override fun onCancelled(error: DatabaseError) {
                 //   Log.e(TAG, "Error listening for notifications: ${error.message}")
                }
            })

    //    Log.d(TAG, "Started listening for notifications")
    }

    /**
     * Process a notification from Firebase and show system notification
     */
    private fun processNotification(snapshot: DataSnapshot) {
        try {
        //    Log.d(TAG, "Processing notification data: ${snapshot.key}")

            // Extract notification data
            val contentType = snapshot.child("contentType").getValue(String::class.java) ?: ""
            val contentId = snapshot.child("contentId").getValue(String::class.java) ?: ""
            val senderId = snapshot.child("senderId").getValue(String::class.java) ?: ""
            val senderName = snapshot.child("senderName").getValue(String::class.java) ?: ""
            val team = snapshot.child("team").getValue(String::class.java) ?: ""
            val message = snapshot.child("message").getValue(String::class.java) ?: ""
            val reactionCategory = snapshot.child("reactionCategory").getValue(String::class.java) ?: ""
            val reactionValue = snapshot.child("reactionValue").getValue(String::class.java) ?: ""
            val count = snapshot.child("count").getValue(Int::class.java) ?: 0
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
            val read = snapshot.child("read").getValue(Boolean::class.java) ?: false

            // Skip if missing critical info or count not at threshold
            if (contentId.isEmpty() || contentType.isEmpty() || count < 100) {
            //    Log.d(TAG, "Skipping notification - missing info or count below threshold")
                return
            }

            // Skip already read notifications
            if (read) {
            //    Log.d(TAG, "Skipping notification - already marked as read")
                return
            }

            // Log the extracted data
        //    Log.d(TAG, "Content type: $contentType, ID: $contentId, Sender: $senderName, Count: $count")

            // Check cooldown period - create a unique key for this content + reaction type
            val notificationKey = "$contentId:$reactionCategory:$reactionValue"
            val currentTime = System.currentTimeMillis()
            val lastNotificationTime = notificationTimestamps[notificationKey] ?: 0L

            // If we're still in cooldown period, skip this notification
            if (currentTime - lastNotificationTime < NOTIFICATION_COOLDOWN) {
           //     Log.d(TAG, "Skipping notification due to cooldown: $notificationKey")
                return
            }

            // Update timestamp for this notification type
            notificationTimestamps[notificationKey] = currentTime

            // Create notification title and message
            val title: String
            val notificationMessage: String

            when (contentType) {
                "CHAT" -> {
                    title = "Popular Chat Message"
                    notificationMessage = when (reactionCategory) {
                        "hitMiss" -> "${senderName}'s message received ${count}${getSuffix(count)} ${reactionValue.capitalize()}!"
                        "reaction" -> "${senderName}'s message got ${count}${getSuffix(count)} ${getEmojiName(reactionValue)} reactions!"
                        else -> "${senderName}'s message is trending!"
                    }
                }
                "POLL" -> {
                    title = "Popular Poll"
                    notificationMessage = when (reactionCategory) {
                        "hitMiss" -> "${senderName}'s poll received ${count}${getSuffix(count)} ${reactionValue.capitalize()}!"
                        "reaction" -> "${senderName}'s poll got ${count}${getSuffix(count)} ${getEmojiName(reactionValue)} reactions!"
                        else -> "${senderName}'s poll is trending!"
                    }
                }
                "MEME" -> {
                    title = "Popular Meme"
                    notificationMessage = when (reactionCategory) {
                        "hitMiss" -> "${senderName}'s meme received ${count}${getSuffix(count)} ${reactionValue.capitalize()}!"
                        "reaction" -> "${senderName}'s meme got ${count}${getSuffix(count)} ${getEmojiName(reactionValue)} reactions!"
                        else -> "${senderName}'s meme is trending!"
                    }
                }
                "COMMENT" -> {
                    title = "Popular Comment"
                    notificationMessage = when (reactionCategory) {
                        "hitMiss" -> "${senderName}'s comment received ${count}${getSuffix(count)} ${reactionValue.capitalize()}!"
                        "reaction" -> "${senderName}'s comment got ${count}${getSuffix(count)} ${getEmojiName(reactionValue)} reactions!"
                        else -> "${senderName}'s comment is trending!"
                    }
                }
                else -> {
                    title = "Popular Content"
                    notificationMessage = "Content from ${senderName} is trending!"
                }
            }

        //    Log.d(TAG, "Notification title: $title")
        //    Log.d(TAG, "Notification message: $notificationMessage")

            // Create pending intent for when notification is tapped
            val pendingIntent = createContentPendingIntent(contentType, contentId)

            // Create a pending intent to open all notifications
            val allNotificationsIntent = createAllNotificationsPendingIntent()

            // Check notification permission
            if (!checkNotificationPermission()) {
            //    Log.e(TAG, "Missing POST_NOTIFICATIONS permission - notification won't appear")
                return
            }

            // Build notification
       //     Log.d(TAG, "Building notification...")
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.bell_icon)
                .setContentTitle(title)
                .setContentText(notificationMessage)
                .setContentIntent(pendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            // Add team info if available
            if (team.isNotEmpty()) {
                builder.setSubText("Team: $team")
            }

            // Add message preview if available
            if (message.isNotEmpty() && message != "Meme") {
                val shortMessage = if (message.length > 50) {
                    message.substring(0, 47) + "..."
                } else {
                    message
                }
                builder.setStyle(NotificationCompat.BigTextStyle().bigText("$notificationMessage\n\n\"$shortMessage\""))
            }

            // Show notification
            try {
            //    Log.d(TAG, "Attempting to show notification...")
                with(NotificationManagerCompat.from(context)) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        notify(notificationId++, builder.build())
                    //    Log.d(TAG, "Successfully showed system notification for $contentType with ID $contentId")
                    } else {
                    //    Log.e(TAG, "Permission check failed inside notification show attempt")
                    }
                }
            } catch (e: Exception) {
                // Handle any other exceptions
            //    Log.e(TAG, "Error showing notification: ${e.message}", e)
            }

        //    Log.d(TAG, "Notification processing completed")

        } catch (e: Exception) {
        //    Log.e(TAG, "Error processing notification: ${e.message}", e)
        }
    }

    /**
     * Create appropriate PendingIntent for when the notification is tapped
     */
    private fun createContentPendingIntent(contentType: String, contentId: String): PendingIntent {
        // Create an intent for the Home activity, which will handle proper navigation
        val intent = Intent(context, Home::class.java).apply {
            putExtra("NOTIFICATION_CONTENT_TYPE", contentType)
            putExtra("NOTIFICATION_CONTENT_ID", contentId)
            putExtra("SHOULD_NAVIGATE", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Create unique request code for this content
        val requestCode = REQUEST_CODE_BASE + contentId.hashCode() % 1000

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create PendingIntent for viewing all notifications
     */
    private fun createAllNotificationsPendingIntent(): PendingIntent {
        val intent = Intent(context, NotificationActivity::class.java)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + 999,  // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Get proper number suffix (1st, 2nd, 3rd, etc.)
     */
    private fun getSuffix(count: Int): String {
        return when {
            count % 100 in 11..13 -> "th"
            count % 10 == 1 -> "st"
            count % 10 == 2 -> "nd"
            count % 10 == 3 -> "rd"
            else -> "th"
        }
    }

    /**
     * Convert emoji type to readable name
     */
    private fun getEmojiName(reactionType: String): String {
        return when (reactionType) {
            "fire" -> "Fire"
            "laugh" -> "Laugh"
            "cry" -> "Crying"
            "troll" -> "Broken Heart"
            else -> reactionType.capitalize()
        }
    }

    /**
     * Mark a notification as read in Firebase
     */
    fun markNotificationAsRead(notificationId: String) {
        val notificationRef = FirebaseDatabase.getInstance().getReference("Notifications/$notificationId/read")
        notificationRef.setValue(true)
            .addOnSuccessListener {
             //   Log.d(TAG, "Notification marked as read: $notificationId")
            }
            .addOnFailureListener {
             //   Log.e(TAG, "Failed to mark notification as read: ${it.message}")
            }
    }

    /**
     * Mark all notifications as read
     */
    fun markAllNotificationsAsRead() {
        val database = FirebaseDatabase.getInstance()
        val notificationsRef = database.getReference("Notifications")

        notificationsRef.orderByChild("read").equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (notificationSnapshot in snapshot.children) {
                        notificationSnapshot.ref.child("read").setValue(true)
                    }
                //    Log.d(TAG, "Marked all notifications as read")
                }

                override fun onCancelled(error: DatabaseError) {
                //    Log.e(TAG, "Error marking all notifications as read: ${error.message}")
                }
            })
    }

    /**
     * Test method to send a sample notification
     */
    fun showTestNotification() {
     //   Log.d(TAG, "Sending test notification...")

        val intent = Intent(context, NotificationActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.bell_icon)
            .setContentTitle("Test Notification")
            .setContentText("This is a test notification from the Cricket App")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(999, builder.build())
            //    Log.d(TAG, "Test notification sent successfully")
            } else {
            //    Log.e(TAG, "Missing notification permission for test notification")
            }
        }
    }
}