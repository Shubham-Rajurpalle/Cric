package com.cricketApp.cric.Profile

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.ChatMessage
import com.cricketApp.cric.Chat.CommentActivity
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.Chat.PollMessage
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.LogIn.UserInfo
import com.cricketApp.cric.Meme.MemeMessage
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.Calendar

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private lateinit var activitiesAdapter: profileActivityAdapter
    private val activities = ArrayList<UserActivity>()

    private val currentUser = FirebaseAuth.getInstance().currentUser
    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1
    private val valueEventListeners = HashMap<DatabaseReference, ValueEventListener>()
    private var isFragmentActive = false

    // User statistics
    private var userHits: Int = 0
    private var userMisses: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isFragmentActive = true


        // Setup back button
        binding.backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        // Setup profile edit button
        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(requireContext(), UserInfo::class.java))
        }

        // Setup RecyclerView for activities
        setupActivitiesRecyclerView()

        // Load user data
        loadUserProfile()

        // Calculate user statistics
        calculateUserStatistics()

        // Load user activities (after user profile is loaded)
        loadUserActivities()

        // Setup See All button - Modified to launch AllActivitiesActivity
        binding.seeAllButton.setOnClickListener {
            // Launch the AllActivitiesActivity instead of showing a dialog
            val intent = Intent(requireContext(), AllActivitiesActivity::class.java)
            startActivity(intent)
        }

        // Setup FAQ, Terms, Privacy sections
        binding.faqSection.setOnClickListener {
            // Navigate to FAQ URL
            val faqUrl = "https://sites.google.com/view/cricpolicies/home"
            val faqIntent = Intent(Intent.ACTION_VIEW, Uri.parse(faqUrl))
            context?.startActivity(faqIntent)
        }

        binding.termsSection.setOnClickListener {
            // Navigate to Terms URL
            val termsUrl = "https://sites.google.com/view/cricpolicies/terms-and-conditions"
            val termsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl))
            context?.startActivity(termsIntent)
        }

        binding.privacySection.setOnClickListener {
            // Navigate to Privacy URL
            val privacyUrl = "https://sites.google.com/view/cricpolicies/privacy-policy"
            val privacyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
            context?.startActivity(privacyIntent)
        }

        // Setup logout button
        binding.logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setupActivitiesRecyclerView() {
        activitiesAdapter = profileActivityAdapter(activities) { activity ->
            // Handle click on activity item based on type
            when (activity.type) {
                UserActivityType.CHAT -> {
                    val intent = Intent(context, CommentActivity::class.java).apply {
                        putExtra("ITEM_ID", activity.id)
                        putExtra("ITEM_TYPE", "chat")
                    }
                    startActivity(intent)
                }
                UserActivityType.MEME -> {
                    val intent = Intent(context, CommentActivity::class.java).apply {
                        putExtra("ITEM_ID", activity.id)
                        putExtra("ITEM_TYPE", "meme")
                    }
                    startActivity(intent)
                }
                UserActivityType.POLL -> {
                    val intent = Intent(context, CommentActivity::class.java).apply {
                        putExtra("ITEM_ID", activity.id)
                        putExtra("ITEM_TYPE", "poll")
                    }
                    startActivity(intent)
                }
                UserActivityType.COMMENT -> {
                    // Navigate to parent item
                    val parentType = activity.additionalData?.get("parentType") as? String ?: "chat"
                    val parentId = activity.additionalData?.get("parentId") as? String ?: ""

                    val intent = Intent(context, CommentActivity::class.java).apply {
                        putExtra("ITEM_ID", parentId)
                        putExtra("ITEM_TYPE", parentType)
                    }
                    startActivity(intent)
                }
            }
        }

        binding.activitiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = activitiesAdapter
        }
    }

    private fun loadUserProfile() {
        if (!isFragmentActive) return

        val userId = currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFragmentActive) return

                // Load username
                val username = snapshot.child("username").getValue(String::class.java)
                binding.userName.text = username ?: "User"

                // Load profile photo
                val photoUrl = snapshot.child("profilePhoto").getValue(String::class.java)
                if (!photoUrl.isNullOrEmpty()) {
                    try {
                        Glide.with(this@ProfileFragment)
                            .load(photoUrl)
                            .placeholder(R.drawable.profile_empty)
                            .into(binding.profileImage)
                    } catch (e: Exception) {
                    //    Log.e("ProfileFragment", "Error loading profile image", e)
                    }
                }

                // Update hits/misses if available
                if (snapshot.hasChild("hits")) {
                    userHits = snapshot.child("hits").getValue(Int::class.java) ?: 0
                }

                if (snapshot.hasChild("misses")) {
                    userMisses = snapshot.child("misses").getValue(Int::class.java) ?: 0
                }

                updateStatisticsDisplay()

                // Now that we have the user profile, load their activities
                loadUserActivities()
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("ProfileFragment", "Error loading user data", error.toException())
            }
        }

        // Store listener for cleanup
        userRef.addListenerForSingleValueEvent(listener)
        valueEventListeners[userRef] = listener
    }

    private fun calculateUserStatistics() {
        val userId = currentUser?.uid ?: return

        // Create a reference to store user stats
        val userStatsRef = FirebaseDatabase.getInstance().getReference("Users/$userId")

        // Calculate hits and misses from chats
        calculateStatsFromCollection("NoBallZone/chats", userId) { chatHits, chatMisses ->
            // Calculate hits and misses from memes
            calculateStatsFromCollection("NoBallZone/memes", userId) { memeHits, memeMisses ->
                // Calculate hits and misses from polls
                calculateStatsFromCollection("NoBallZone/polls", userId) { pollHits, pollMisses ->
                    // Sum up all hits and misses
                    userHits = chatHits + memeHits + pollHits
                    userMisses = chatMisses + memeMisses + pollMisses

                    // Update the user stats in Firebase
                    val updates = HashMap<String, Any>()
                    updates["hits"] = userHits
                    updates["misses"] = userMisses
                    userStatsRef.updateChildren(updates)

                    // Update UI
                    updateStatisticsDisplay()
                }
            }
        }
    }

    private fun calculateStatsFromCollection(path: String, userId: String, callback: (Int, Int) -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference(path)

        ref.orderByChild("senderId").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hits = 0
                var misses = 0

                for (itemSnapshot in snapshot.children) {
                    val itemHits = itemSnapshot.child("hit").getValue(Int::class.java) ?: 0
                    val itemMisses = itemSnapshot.child("miss").getValue(Int::class.java) ?: 0

                    hits += itemHits
                    misses += itemMisses
                }

                callback(hits, misses)
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("ProfileFragment", "Error calculating stats from $path", error.toException())
                callback(0, 0)
            }
        })
    }

    private fun updateStatisticsDisplay() {
        // Format counts with K suffix for thousands
        val formattedHits = if (userHits >= 1000) {
            String.format("%.1fK", userHits / 1000f)
        } else {
            userHits.toString()
        }

        val formattedMisses = if (userMisses >= 1000) {
            String.format("%.1fK", userMisses / 1000f)
        } else {
            userMisses.toString()
        }

        // Update the UI with the formatted values
        binding.hitsCount.text = "$formattedHits Hits"
        binding.missesCount.text = "$formattedMisses Hits" // This is intentionally "Hits" to match your UI
    }

    private fun loadUserActivities() {
        val userId = currentUser?.uid ?: return

        // Clear existing activities
        activities.clear()

        // Load user's chats, memes, polls, and comments
        loadUserChats(userId)
        loadUserMemes(userId)
        loadUserPolls(userId)
        loadUserComments(userId)
    }

    private fun loadUserChats(userId: String) {
        if (!isFragmentActive) return

        val chatsRef = FirebaseDatabase.getInstance().getReference("NoBallZone/chats")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFragmentActive) return

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
                        activities.add(activity)
                    }
                }

                // Sort and update the adapter
                sortActivitiesAndUpdateAdapter()
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("ProfileFragment", "Error loading user chats", error.toException())
            }
        }

        chatsRef.orderByChild("senderId").equalTo(userId).limitToLast(5).addListenerForSingleValueEvent(listener)
        valueEventListeners[chatsRef] = listener
    }

    private fun loadUserMemes(userId: String) {
        if (!isFragmentActive) return

        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFragmentActive) return

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
                        activities.add(activity)
                    }
                }

                // Sort and update the adapter
                sortActivitiesAndUpdateAdapter()
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("ProfileFragment", "Error loading user memes", error.toException())
            }
        }

        memesRef.orderByChild("senderId").equalTo(userId).limitToLast(5).addListenerForSingleValueEvent(listener)
        valueEventListeners[memesRef] = listener
    }

    private fun loadUserPolls(userId: String) {
        if (!isFragmentActive) return

        val pollsRef = FirebaseDatabase.getInstance().getReference("NoBallZone/polls")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFragmentActive) return

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
                        activities.add(activity)
                    }
                }

                // Sort and update the adapter
                sortActivitiesAndUpdateAdapter()
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("ProfileFragment", "Error loading user polls", error.toException())
            }
        }

        pollsRef.orderByChild("senderId").equalTo(userId).limitToLast(5).addListenerForSingleValueEvent(listener)
        valueEventListeners[pollsRef] = listener
    }

    private fun loadUserComments(userId: String) {
        if (!isFragmentActive) return

        loadCommentsFromCollection("NoBallZone/chats", userId, "chat")
        loadCommentsFromCollection("NoBallZone/memes", userId, "meme")
        loadCommentsFromCollection("NoBallZone/polls", userId, "poll")
    }

    private fun loadCommentsFromCollection(path: String, userId: String, parentType: String) {
        if (!isFragmentActive) return

        val ref = FirebaseDatabase.getInstance().getReference(path)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFragmentActive) return

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
                            activities.add(activity)
                        }
                    }
                }

                // Sort and update the adapter
                sortActivitiesAndUpdateAdapter()
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("ProfileFragment", "Error loading comments from $path", error.toException())
            }
        }

        ref.addListenerForSingleValueEvent(listener)
        valueEventListeners[ref] = listener
    }

    private fun sortActivitiesAndUpdateAdapter() {
        if (!isFragmentActive) return

        // Sort activities by timestamp (newest first)
        activities.sortByDescending { it.timestamp }

        // Limit to most recent 5 activities for the main view
        if (activities.size > 5) {
            val limitedActivities = activities.take(5)
            val tempList = ArrayList<UserActivity>()
            tempList.addAll(limitedActivities)

            // Save the full list but show limited in UI
            activities.clear()
            activities.addAll(tempList)
        }

        // Update adapter
        activitiesAdapter.notifyDataSetChanged()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun uploadProfileImage() {
        val userId = currentUser?.uid ?: return
        selectedImageUri?.let { uri ->

            // Show loading indicator
            val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
            val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)

            val progressDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(progressView)
                .setCancelable(false)
                .setTitle("Updating profile photo...")
                .create()

            progressDialog.show()

            // Create a reference to the image file in Firebase Storage
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("profile_images/${userId}_${System.currentTimeMillis()}.jpg")

            // Upload the image
            imageRef.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    // Get the download URL
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        // Update user profile with new image URL
                        updateUserProfilePhoto(downloadUrl.toString()) {
                            progressDialog.dismiss()

                            // Load the new image into the ImageView
                            Glide.with(requireContext())
                                .load(downloadUrl)
                                .placeholder(R.drawable.profile_empty)
                                .into(binding.profileImage)

                            Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                //    Log.e("ProfileFragment", "Error uploading profile image", e)
                    Toast.makeText(context, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserProfilePhoto(photoUrl: String, onComplete: () -> Unit) {
        val userId = currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId")

        val updates = HashMap<String, Any>()
        updates["profilePhoto"] = photoUrl

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                onComplete()
            }
            .addOnFailureListener { e ->
            //    Log.e("ProfileFragment", "Error updating profile photo URL", e)
                onComplete()
            }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogTheme)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                FirebaseAuth.getInstance().signOut()

                // Navigate to login screen
                val intent = Intent(requireContext(), SignIn::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            uploadProfileImage()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Set flag to false
        isFragmentActive = false

        // Remove all Firebase listeners
        for ((ref, listener) in valueEventListeners) {
            ref.removeEventListener(listener)
        }
        valueEventListeners.clear()

        // Clear adapter reference
        binding.activitiesRecyclerView.adapter = null
    }
}