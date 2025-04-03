package com.cricketApp.cric.Utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cricketApp.cric.R
import com.cricketApp.cric.home.Home
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "content_milestones"
        private const val CHANNEL_NAME = "Content Milestones"
        private const val CHANNEL_DESCRIPTION = "Notifications for popular content"
        private const val COOLDOWN_PREFS = "notification_cooldown_prefs"
        private const val COOLDOWN_DURATION = 300000 // 5 minutes in milliseconds (changed from 10 minutes)
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(COOLDOWN_PREFS, Context.MODE_PRIVATE)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Check if the message contains data
        if (remoteMessage.data.isNotEmpty()) {
            // Get data from message
            val contentType = remoteMessage.data["contentType"] ?: ""
            val contentId = remoteMessage.data["contentId"] ?: ""
            val title = remoteMessage.data["title"] ?: "New Notification"
            val message = remoteMessage.data["message"] ?: "Check out what's happening!"
            val team = remoteMessage.data["team"] ?: ""

            // Check if we should show this notification based on cooldown
            val shouldShow = shouldShowNotification(contentType)

            if (shouldShow) {
                showNotification(contentType, contentId, title, message, team)
                updateCooldownTimestamp(contentType)
            }
        }

        // Check if the message contains notification
        remoteMessage.notification?.let {
            // If there's a notification payload, use it as fallback
            val contentType = remoteMessage.data["contentType"] ?: "GENERAL"
            val contentId = remoteMessage.data["contentId"] ?: System.currentTimeMillis().toString()
            val team = remoteMessage.data["team"] ?: ""

            val shouldShow = shouldShowNotification(contentType)

            if (shouldShow) {
                showNotification(contentType, contentId, it.title ?: "New Notification", it.body ?: "Check out what's happening!", team)
                updateCooldownTimestamp(contentType)
            }
        }
    }

    /**
     * Check if enough time has passed since last notification of this type
     */
    private fun shouldShowNotification(contentType: String): Boolean {
        // If cooldown is disabled (set to 0), always show notifications
        if (COOLDOWN_DURATION <= 0) return true

        val lastNotificationTime = sharedPreferences.getLong("last_${contentType}_notification", 0)
        val currentTime = System.currentTimeMillis()
        return currentTime - lastNotificationTime > COOLDOWN_DURATION
    }

    /**
     * Update the timestamp for this content type
     */
    private fun updateCooldownTimestamp(contentType: String) {
        sharedPreferences.edit().apply {
            putLong("last_${contentType}_notification", System.currentTimeMillis())
            apply()
        }
    }

    override fun onNewToken(token: String) {
        sendRegistrationTokenToServer(token)
    }

    private fun sendRegistrationTokenToServer(token: String) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = com.google.firebase.database.FirebaseDatabase.getInstance()
        database.getReference("Users/$userId/fcmToken").setValue(token)
    }

    private fun showNotification(contentType: String, contentId: String, title: String, message: String, team: String) {
        // Create intent to open the appropriate screen when notification is tapped
        val intent = Intent(this, Home::class.java).apply {
            putExtra("NOTIFICATION_CONTENT_TYPE", contentType)
            putExtra("NOTIFICATION_CONTENT_ID", contentId)
            putExtra("SHOULD_NAVIGATE", true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            contentId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Create notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.bell_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Add team info if available
        if (team.isNotEmpty()) {
            notificationBuilder.setSubText("Team: $team")
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(contentId.hashCode(), notificationBuilder.build())
    }
}