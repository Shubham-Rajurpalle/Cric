package com.cricketApp.cric.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.cricketApp.cric.Chat.ChatFragment
import com.cricketApp.cric.Chat.NotificationActivity
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Meme.MemeFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivityHomeBinding
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.Utils.NotificationService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Home : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var notificationService: NotificationService

    // Add this permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("Permissions", "Notification permission granted")
            initializeNotificationService()
        } else {
            Log.d("Permissions", "Notification permission denied")
            // Initialize service anyway - it will just log without showing notifications
            initializeNotificationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        // In your MainActivity.onCreate() or Application.onCreate()
        subscribeToNotifications()
        handleNotificationNavigation(intent)

        // Replace with permission check
        checkNotificationPermission()

        loadProfileImageForBottomNav()
        setupBottomNav()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHost)
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.lastOrNull()

                if (currentFragment !is HomeFragment) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.navHost, HomeFragment())
                        .addToBackStack(null)
                        .commit()

                    binding.bottomNavigation.post {
                        binding.bottomNavigation.selectedItemId = R.id.homeIcon
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            switchFragment(HomeFragment(), "Home")
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    Log.d("Permissions", "Notification permission already granted")
                    initializeNotificationService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Explain why we need permission then request
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission")
                        .setMessage("We need notification permission to show you alerts when content is trending")
                        .setPositiveButton("Grant") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Not Now") { _, _ ->
                            // Initialize service anyway
                            initializeNotificationService()
                        }
                        .show()
                }
                else -> {
                    // First time asking - request directly
                    Log.d("Permissions", "Requesting notification permission")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // No runtime permission needed for Android < 13
            initializeNotificationService()
        }
    }

    fun subscribeToNotifications() {
        // Subscribe all users to the general "trending" topic
        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("trending")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to trending notifications")
                } else {
                    Log.e("FCM", "Failed to subscribe to trending notifications")
                }
            }

        // If user is logged in, subscribe to their team's topic
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val database = com.google.firebase.database.FirebaseDatabase.getInstance()
            database.getReference("Users/$userId/iplTeam").addListenerForSingleValueEvent(
                object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val team = snapshot.getValue(String::class.java) ?: return
                        if (team.isNotEmpty()) {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("team_$team")
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Log.d("FCM", "Subscribed to team $team notifications")
                                    } else {
                                        Log.e("FCM", "Failed to subscribe to team $team notifications")
                                    }
                                }
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        Log.e("FCM", "Failed to fetch user team: ${error.message}")
                    }
                }
            )
        }
    }

    private fun initializeNotificationService() {
        notificationService = NotificationService(this)
        notificationService.createNotificationChannel()
        notificationService.startListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle navigation when app is already running
        handleNotificationNavigation(intent)
    }

    private fun handleNotificationNavigation(intent: Intent) {
        val contentType = intent.getStringExtra("NOTIFICATION_CONTENT_TYPE")
        val contentId = intent.getStringExtra("NOTIFICATION_CONTENT_ID")
        val shouldNavigate = intent.getBooleanExtra("SHOULD_NAVIGATE", false)

        if (contentType != null && contentId != null && shouldNavigate) {
            // Navigate based on content type
            when (contentType) {
                "CHAT" -> {
                    // Navigate to ChatFragment and highlight the message
                    val fragment = ChatFragment().apply {
                        arguments = Bundle().apply {
                            putString("HIGHLIGHT_MESSAGE_ID", contentId)
                        }
                    }
                    switchFragment(fragment, "Chat")
                    binding.bottomNavigation.selectedItemId = R.id.chatIcon
                }
                "POLL" -> {
                    // Polls are in ChatFragment too
                    val fragment = ChatFragment().apply {
                        arguments = Bundle().apply {
                            putString("HIGHLIGHT_MESSAGE_ID", contentId)
                        }
                    }
                    switchFragment(fragment, "Chat")
                    binding.bottomNavigation.selectedItemId = R.id.chatIcon
                }
                "MEME" -> {
                    // Navigate to MemeFragment and highlight the meme
                    val fragment = MemeFragment().apply {
                        arguments = Bundle().apply {
                            putString("HIGHLIGHT_MESSAGE_ID", contentId)
                        }
                    }
                    switchFragment(fragment, "Meme")
                    binding.bottomNavigation.selectedItemId = R.id.memeIcon
                }
                "COMMENT" -> {
                    // Comments require parent info which we don't have here,
                    // So just navigate to either Chat or Meme based on parent type
                    // This would ideally be handled by having more info in the notification
                    val parentType = intent.getStringExtra("PARENT_TYPE") ?: "chat"
                    if (parentType.contains("meme")) {
                        binding.bottomNavigation.selectedItemId = R.id.memeIcon
                    } else {
                        binding.bottomNavigation.selectedItemId = R.id.chatIcon
                    }
                }
            }

            // Clear navigation flags
            intent.removeExtra("SHOULD_NAVIGATE")
        }
    }

    // Add a method to handle bell icon clicks to open Notifications Activity
    fun openNotifications() {
        val intent = Intent(this, NotificationActivity::class.java)
        startActivity(intent)
    }


    private fun setupBottomNav() {
        binding.bottomNavigation.itemIconTintList = null
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeIcon -> switchFragment(HomeFragment(), "Home")
                R.id.chatIcon -> switchFragment(ChatFragment(), "Chat")
                R.id.memeIcon -> switchFragment(MemeFragment(), "Meme")
                R.id.leaderboardIcon -> switchFragment(LeaderboardFragment(), "Leaderboard")
                R.id.profileIcon -> {
                    if (isUserLoggedIn()) {
                        switchFragment(ProfileFragment(), "Profile")
                    } else {
                        showLoginRequiredDialog()
                        // Return false to prevent selecting the Profile tab
                        return@setOnItemSelectedListener false
                    }
                }
                else -> false
            }
            true
        }
    }

    private fun loadProfileImageForBottomNav() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("Users/${currentUser.uid}/profilePhoto")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val photoUrl = snapshot.getValue(String::class.java)
                    if (!photoUrl.isNullOrEmpty()) {
                        // Get the profile menu item view
                        val profileMenuItem = binding.bottomNavigation.menu.findItem(R.id.profileIcon)

                        // Load the image using Glide and set it as the icon
                        Glide.with(this@Home)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.profile_icon)
                            .error(R.drawable.profile_icon)
                            .into(object : CustomTarget<Drawable>() {
                                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                    profileMenuItem.setIcon(resource)
                                }

                                override fun onLoadCleared(placeholder: Drawable?) {
                                    profileMenuItem.setIcon(R.drawable.profile_icon)
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Home", "Error loading profile photo", error.toException())
                }
            })
        }
    }

    private fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(this,R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage("You need to login to view your profile")
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(this, SignIn::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                // Make sure Home tab is selected
                binding.bottomNavigation.selectedItemId = R.id.homeIcon
            }
            .setCancelable(false)
            .show()
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        try {
            val fragmentManager = supportFragmentManager

            // Don't replace if it's the same fragment
            val currentFragment = fragmentManager.findFragmentById(R.id.navHost)
            if (currentFragment != null && currentFragment.javaClass == fragment.javaClass) {
                return
            }

            // Use commitAllowingStateLoss to prevent IllegalStateException
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.navHost, fragment, tag)

            if (tag != "Home") {
                transaction.addToBackStack("Home")
            }

            transaction.commitAllowingStateLoss()
        } catch (e: Exception) {
            Log.e("Home", "Error switching fragment: ${e.message}", e)
        }
    }
}
