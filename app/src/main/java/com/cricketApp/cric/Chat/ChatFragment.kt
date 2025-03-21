package com.cricketApp.cric.Chat

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Meme.CloudVisionSafetyChecker
import com.cricketApp.cric.Moderation.ChatModerationService
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentChatBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatFragment : Fragment() {

    private lateinit var binding: FragmentChatBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var messages: ArrayList<Any> // Can hold both ChatMessage and PollMessage
    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: StorageReference
    private lateinit var moderationService: ChatModerationService
    private var currentUser = FirebaseAuth.getInstance().currentUser
    private var userTeam: String = "CSK" // Default team, will be updated from user profile
    private val PICK_IMAGE_REQUEST = 1
    private val LOGIN_REQUEST_CODE = 1001
    private var selectedImageUri: Uri? = null
    private lateinit var safetyChecker: CloudVisionSafetyChecker

    // Map to keep track of message positions for efficient updates
    private val messagePositions = mutableMapOf<String, Int>()

    // Selected filters
    private var selectedFilter = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) // Check if IME is visible
            val height = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation).height
            val bottomInsets = if (imeVisible) {
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - height // Use IME insets when keyboard is visible
            } else {
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom // Use system bar insets otherwise
            }

            view.setPadding(0, 0, 0, bottomInsets)

            insets
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize moderation service
        moderationService = ChatModerationService(requireContext())

        // Initialize safety checker for images
        safetyChecker = CloudVisionSafetyChecker(requireContext())

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()

        // Initialize Firebase Storage
        val storage = FirebaseStorage.getInstance()
        storageRef = storage.reference

        // Update UI based on login status
        updateUIBasedOnLoginStatus()

        // Load profile photo if logged in
        if (isUserLoggedIn()) {
            loadProfilePhoto()
            fetchUserTeam()
        } else {
            binding.profilePhoto.setImageResource(R.drawable.profile_icon)
        }

        // Setup RecyclerView
        messages = ArrayList()
        adapter = ChatAdapter(messages)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true)
            (layoutManager as LinearLayoutManager).stackFromEnd = true
            adapter = this@ChatFragment.adapter
        }

        // Setup filters
        setupFilters()

        // Setup Firebase listeners
        setupFirebaseListeners()

        // Setup send message button with moderation and login check
        binding.buttonSend.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to send messages")
                return@setOnClickListener
            }

            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                checkAndSendMessage(messageText)
            }
        }

        // Setup image attachment button with login check
        binding.buttonMeme.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to share images")
                return@setOnClickListener
            }

            openImagePicker()
        }

        // Setup poll button with login check
        binding.buttonPoll.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to create polls")
                return@setOnClickListener
            }

            showCreatePollDialog()
        }

        // Setup navigation buttons
        binding.leaderBoardIcon.setOnClickListener {
            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.leaderboardIcon
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.navHost, LeaderboardFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }

        binding.profilePhoto.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to view your profile")
                return@setOnClickListener
            }

            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.profileIcon
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.navHost, ProfileFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }
    }

    private fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    private fun showLoginPrompt(message: String) {
        AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(requireContext(), SignIn::class.java)
                startActivityForResult(intent, LOGIN_REQUEST_CODE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUIBasedOnLoginStatus() {
        val isLoggedIn = isUserLoggedIn()

        // Show hint text for non-logged in users in the message input
        if (!isLoggedIn) {
            binding.editTextMessage.hint = "Login to send messages"
            // Disable buttons for non-logged in users
            binding.buttonSend.alpha = 0.5f
            binding.buttonMeme.alpha = 0.5f
            binding.buttonPoll.alpha = 0.5f
        } else {
            binding.editTextMessage.hint = "Type a message"
            // Enable buttons for logged in users
            binding.buttonSend.alpha = 1.0f
            binding.buttonMeme.alpha = 1.0f
            binding.buttonPoll.alpha = 1.0f
        }
    }

    private fun loadProfilePhoto() {
        val userId = currentUser?.uid ?: return
        val userRef = database.getReference("Users/$userId/profilePhoto")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val photoUrl = snapshot.getValue(String::class.java)
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(context ?: return)
                        .load(photoUrl)
                        .placeholder(R.drawable.profile_icon)
                        .into(binding.profilePhoto)
                } else {
                    Log.e("Profile", "No profile photo found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatFragment", "Error loading profile photo", error.toException())
            }
        })
    }

    private fun fetchUserTeam() {
        currentUser?.uid?.let { userId ->
            val userRef = database.getReference("Users/$userId/iplTeam")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userTeam = snapshot.getValue(String::class.java) ?: "No Team"
                }

                override fun onCancelled(error: DatabaseError) {
                    // Keep default team
                }
            })
        }
    }

    private fun openImagePicker() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST)
    }

    private fun showImagePreviewDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_image_preview, null)
        val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
        val editTextCaption = dialogView.findViewById<EditText>(R.id.editTextCaption)

        // Load image into preview
        Glide.with(requireContext())
            .load(selectedImageUri)
            .into(imagePreview)

        // Show dialog with preview and send button
        MaterialAlertDialogBuilder(requireContext(),R.style.CustomAlertForImagePreview)
            .setTitle("Send Image")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val caption = editTextCaption.text.toString().trim()

                // Show loading indicator
                val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
                val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)

                val progressDialog = MaterialAlertDialogBuilder(requireContext())
                    .setView(progressView)
                    .setCancelable(false)
                    .setTitle("Uploading image...")
                    .create()

                progressDialog.show()

                // Proceed with upload
                uploadAndSendImage(caption) { success ->
                    progressDialog.dismiss()
                    if (!success) {
                        Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                selectedImageUri = null
                dialog.dismiss()
            }
            .create()
            .show()
    }

    // New method to check message and send if approved
    private fun checkAndSendMessage(messageText: String) {
        // Show loading indicator
        binding.buttonSend.isEnabled = false
        binding.progressSending.visibility = View.VISIBLE

        moderationService.checkMessageContent(messageText, object : ChatModerationService.ModerationCallback {
            override fun onMessageApproved(message: String) {
                // Run on UI thread as the callback might be on a background thread
                requireActivity().runOnUiThread {
                    // Message is approved, send it
                    sendMessageToFirebase(message)

                    // Reset UI
                    binding.buttonSend.isEnabled = true
                    binding.progressSending.visibility = View.GONE
                }
            }

            override fun onMessageRejected(message: String, reason: String) {
                requireActivity().runOnUiThread {
                    // Show feedback to user about rejection
                    Toast.makeText(context, reason, Toast.LENGTH_LONG).show()

                    // Reset UI
                    binding.buttonSend.isEnabled = true
                    binding.progressSending.visibility = View.GONE
                }
            }

            override fun onError(errorMessage: String) {
                requireActivity().runOnUiThread {
                    Log.e("ChatFragment", "Moderation error: $errorMessage")

                    // Show error message to user
                    Toast.makeText(
                        context,
                        "Unable to check message content. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Reset UI
                    binding.buttonSend.isEnabled = true
                    binding.progressSending.visibility = View.GONE
                }
            }
        })
    }

    private fun sendMessageToFirebase(messageText: String) {
        if (currentUser == null) {
            Log.e("ChatFragment", "User not logged in")
            Toast.makeText(context, "You must be logged in to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        val chatRef = database.getReference("NoBallZone/chats").push()
        val chatId = chatRef.key ?: return

        // Get user's display name and profile picture
        val userRef = database.getReference("Users/${currentUser!!.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java)
                    ?: currentUser!!.displayName
                    ?: "Anonymous"

                val chatMessage = ChatMessage(
                    id = chatId,
                    senderId = currentUser!!.uid,
                    senderName = userName,
                    team = userTeam,
                    message = messageText,
                    timestamp = System.currentTimeMillis()
                )

                chatRef.setValue(chatMessage)
                    .addOnSuccessListener {
                        binding.editTextMessage.text.clear()
                    }
                    .addOnFailureListener {
                        // Handle error
                        Log.e("ChatFragment", "Error sending message", it)
                        Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatFragment", "Error fetching user data", error.toException())
                Toast.makeText(context, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun uploadAndSendImage(caption: String, onComplete: ((Boolean) -> Unit)? = null) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        selectedImageUri?.let { uri ->
            // Create a reference to the image file in Firebase Storage
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("chat_images/${System.currentTimeMillis()}_${currentUser.uid}.jpg")

            // Upload the image
            imageRef.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    // Get the download URL
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        // Check caption for toxicity before sending
                        if (caption.isNotEmpty()) {
                            moderationService.checkMessageContent(caption, object : ChatModerationService.ModerationCallback {
                                override fun onMessageApproved(message: String) {
                                    // Send message with image and approved caption
                                    sendImageMessageToFirebase(message, downloadUrl.toString())
                                    onComplete?.invoke(true)
                                }

                                override fun onMessageRejected(message: String, reason: String) {
                                    requireActivity().runOnUiThread {
                                        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                                        // Send without caption
                                        sendImageMessageToFirebase("", downloadUrl.toString())
                                        onComplete?.invoke(true)
                                    }
                                }

                                override fun onError(errorMessage: String) {
                                    // On error, just send with original caption
                                    sendImageMessageToFirebase(caption, downloadUrl.toString())
                                    onComplete?.invoke(true)
                                }
                            })
                        } else {
                            // No caption to check, just send the image
                            sendImageMessageToFirebase("", downloadUrl.toString())
                            onComplete?.invoke(true)
                        }

                        // Clear selected image
                        selectedImageUri = null
                    }
                }
                .addOnFailureListener { e ->
                    // Handle error
                    Log.e("ChatFragment", "Image upload failed: ${e.message}")
                    onComplete?.invoke(false)
                }
        }
    }

    // Add these dialog methods for content safety feedback
    private fun showContentBlockedDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) {
            "This image has been blocked because our system detected prohibited content."
        } else {
            "This image has been blocked because our system detected:\n\n" +
                    issues.take(3).joinToString("\n")
        }

        MaterialAlertDialogBuilder(requireContext(),R.style.CustomAlertDialogTheme)
            .setTitle("Content Blocked")
            .setMessage("$message\n\nOur community guidelines prohibit sharing content that contains adult material, violence, hate speech, or other potentially harmful imagery.")
            .setPositiveButton("Select Different Image") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
                openImagePicker()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
            }
            .show()
    }

    private fun showContentWarningDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) {
            "This image may contain inappropriate content."
        } else {
            "This image may contain inappropriate content:\n\n" +
                    issues.take(3).joinToString("\n")
        }

        MaterialAlertDialogBuilder(requireContext(),R.style.CustomAlertDialogTheme)
            .setTitle("Content Warning")
            .setMessage("$message\n\nWhile this image doesn't automatically violate our guidelines, it may be inappropriate for some viewers or contexts.")
            .setPositiveButton("Upload Anyway") { dialog, _ ->
                dialog.dismiss()
                showImagePreviewDialog()
            }
            .setNeutralButton("Select Different Image") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
                openImagePicker()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
            }
            .show()
    }

    private fun sendImageMessageToFirebase(message: String, imageUrl: String) {
        if (currentUser == null) {
            return
        }

        val chatRef = database.getReference("NoBallZone/chats").push()
        val chatId = chatRef.key ?: return

        // Get user's display name and profile picture
        val userRef = database.getReference("Users/${currentUser!!.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java)
                    ?: currentUser!!.displayName
                    ?: "Anonymous"
                val userTeam = snapshot.child("iplTeam").getValue(String::class.java)
                    ?: "No Team"

                val chatMessage = ChatMessage(
                    id = chatId,
                    senderId = currentUser!!.uid,
                    senderName = userName,
                    team = userTeam,
                    message = message,
                    imageUrl = imageUrl,
                    timestamp = System.currentTimeMillis()
                )

                chatRef.setValue(chatMessage)
                    .addOnSuccessListener {
                        binding.editTextMessage.text.clear()
                    }
                    .addOnFailureListener {
                        // Handle error
                        Log.e("ChatFragment", "Error sending message with image", it)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatFragment", "User data fetch cancelled", error.toException())
            }
        })
    }

    private fun checkImageSafetyAndShowPreview() {
        if (selectedImageUri == null || !isUserLoggedIn()) {
            return
        }

        // Create loading dialog
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .setTitle("Checking content safety...")
            .create()

        progressDialog.show()

        // Check image content safety using Cloud Vision API
        selectedImageUri?.let { uri ->
            // Using coroutines for the async API call
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        safetyChecker.checkImageSafety(uri)
                    }

                    progressDialog.dismiss()

                    if (result.isSafe) {
                        // Image is safe, proceed with upload
                        showImagePreviewDialog()
                    } else if (result.autoBlock) {
                        // Image is NOT safe and should be automatically blocked
                        showContentBlockedDialog(result.issues)
                    } else {
                        // Image has potential issues but is not automatically blocked
                        showContentWarningDialog(result.issues)
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    Log.e("ChatFragment", "Error checking image safety: ${e.message}", e)
                    // Show error dialog
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Error")
                        .setMessage("Failed to analyze image: ${e.message}")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
        } ?: run {
            progressDialog.dismiss()
            Toast.makeText(context, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            // Use the safety checker before showing preview
            checkImageSafetyAndShowPreview()
        } else if (requestCode == LOGIN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // User has successfully logged in
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto()
            fetchUserTeam()
            updateUIBasedOnLoginStatus()

            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIBasedOnLoginStatus()

        // Refresh profile photo if user just logged in
        if (isUserLoggedIn() && currentUser != FirebaseAuth.getInstance().currentUser) {
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto()
            fetchUserTeam()
        }
    }


    private fun setupFilters() {
        binding.chipAll.setOnClickListener { resetFilters() }
        binding.chipTopHits.setOnClickListener { toggleFilter("TopHits") { loadTopHitMessages() } }
        binding.chipTopMiss.setOnClickListener { toggleFilter("TopMiss") { loadTopMissMessages() } }
        binding.chipPolls.setOnClickListener { toggleFilter("Polls") { loadPollsOnly() } }
        binding.chipCSK.setOnClickListener { toggleFilter("CSK") { loadTeamMessages("CSK") } }
        binding.chipMI.setOnClickListener { toggleFilter("MI") { loadTeamMessages("MI") } }
        binding.chipDC.setOnClickListener { toggleFilter("DC") { loadTeamMessages("DC") } }
        binding.chipGT.setOnClickListener { toggleFilter("GT") { loadTeamMessages("GT") } }
        binding.chipKKR.setOnClickListener { toggleFilter("KKR") { loadTeamMessages("KKR") } }
        binding.chipLSG.setOnClickListener { toggleFilter("LSG") { loadTeamMessages("LSG") } }
        binding.chipRCB.setOnClickListener { toggleFilter("RCB") { loadTeamMessages("RCB") } }
        binding.chipPBKS.setOnClickListener { toggleFilter("PBKS") { loadTeamMessages("PBKS") } }
        binding.chipRR.setOnClickListener { toggleFilter("RR") { loadTeamMessages("RR") } }
        binding.chipSRH.setOnClickListener { toggleFilter("SRH") { loadTeamMessages("SRH") } }

        binding.chipAll.isChecked = true
    }

    private fun toggleFilter(filter: String, action: () -> Unit) {
        val chip = when (filter) {
            "TopHits" -> binding.chipTopHits
            "TopMiss" -> binding.chipTopMiss
            "Polls" -> binding.chipPolls
            "CSK" -> binding.chipCSK
            "MI" -> binding.chipMI
            "DC" -> binding.chipDC
            "GT" -> binding.chipGT
            "KKR" -> binding.chipKKR
            "LSG" -> binding.chipLSG
            "RCB" -> binding.chipRCB
            "PBKS" -> binding.chipPBKS
            "RR" -> binding.chipRR
            "SRH" -> binding.chipSRH
            else -> null
        }

        val isSelected = selectedFilter.contains(filter)

        // First, clear all existing filters and reset all chips
        selectedFilter.clear()

        // Reset visual state of all chips
        val allChips = listOf(
            binding.chipTopHits, binding.chipTopMiss, binding.chipPolls,
            binding.chipCSK, binding.chipMI, binding.chipDC, binding.chipGT,
            binding.chipKKR, binding.chipLSG, binding.chipRCB, binding.chipPBKS,
            binding.chipRR, binding.chipSRH
        )

        allChips.forEach { c ->
            c.apply {
                isChecked = false
                chipStrokeWidth = 0f
                setTextColor(resources.getColor(R.color.white, null))
            }
        }

        // Set the All chip as not checked initially
        binding.chipAll.apply {
            isChecked = false
            chipStrokeWidth = 0f
            setTextColor(resources.getColor(R.color.white, null))
        }

        // If the same filter was already selected (double-click case), go back to "All"
        if (isSelected) {
            resetFilters()
            return
        }

        // Otherwise, add the new filter and update the chip
        selectedFilter.add(filter)
        chip?.apply {
            isChecked = true
            chipStrokeWidth = 2f
            setTextColor(resources.getColor(R.color.grey, null))
        }

        // Apply the filter
        if (selectedFilter.isEmpty()) {
            resetFilters() // Should never happen as we just added a filter, but just in case
        } else {
            action.invoke() // This will call the specific filter method (loadTopHitMessages, loadTeamMessages, etc.)
        }
    }


    private fun resetFilters() {
        // Clear the selected filters set
        selectedFilter.clear()

        // Set the All chip as checked
        binding.chipAll.apply {
            isChecked = true
            chipStrokeWidth = 2f
            setTextColor(resources.getColor(R.color.grey, null))
        }

        // Reset all other chips
        val allChips = listOf(
            binding.chipTopHits, binding.chipTopMiss, binding.chipPolls,
            binding.chipCSK, binding.chipMI, binding.chipDC, binding.chipGT,
            binding.chipKKR, binding.chipLSG, binding.chipRCB, binding.chipPBKS,
            binding.chipRR, binding.chipSRH
        )

        allChips.forEach { chip ->
            chip.apply {
                isChecked = false
                chipStrokeWidth = 0f
                setTextColor(resources.getColor(R.color.white, null))
            }
        }

        // Clear existing messages and adapter state
        messages.clear()
        messagePositions.clear()
        adapter.notifyDataSetChanged()

        // Load all messages without filtering
        loadMessages()
    }

    private fun setupFirebaseListeners() {
        // We'll use different listeners based on filter status
        loadMessages()
    }

    private fun loadMessages() {
        val chatsRef = database.getReference("NoBallZone/chats")
        val pollsRef = database.getReference("NoBallZone/polls")

        // Clear existing data
        messages.clear()
        messagePositions.clear()

        // Setup child event listeners for real-time updates
        setupChatListener(chatsRef)
        setupPollListener(pollsRef)

        // Initial load
        loadInitialMessages()
    }

    private fun setupChatListener(chatsRef: com.google.firebase.database.DatabaseReference) {
        chatsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Skip if already in our list (from initial load)
                val chatId = snapshot.key ?: return
                if (messagePositions.containsKey(chatId)) return

                // Use helper method to properly read the chat with comments
                val chat = FirebaseDataHelper.getChatMessageFromSnapshot(snapshot) ?: return

                // Add to the list (at the beginning for newest first)
                messages.add(0, chat)

                // Update positions map
                updatePositionsMap()

                adapter.notifyItemInserted(0)
                binding.recyclerViewMessages.scrollToPosition(0)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val chatId = snapshot.key ?: return
                val position = messagePositions[chatId] ?: return

                // Use helper method to properly read the updated chat with comments
                val updatedChat = FirebaseDataHelper.getChatMessageFromSnapshot(snapshot) ?: return

                val currentChat = messages[position] as? ChatMessage ?: return

                // Check what changed and update accordingly
                var needsFullUpdate = false

                // Only update reactions if they changed
                if (currentChat.reactions != updatedChat.reactions) {
                    currentChat.reactions = updatedChat.reactions
                    adapter.notifyItemChanged(position, "reaction")
                }

                // Only update hit/miss if they changed
                if (currentChat.hit != updatedChat.hit || currentChat.miss != updatedChat.miss) {
                    currentChat.hit = updatedChat.hit
                    currentChat.miss = updatedChat.miss
                    adapter.notifyItemChanged(position, "hit_miss")
                }

                // Check for comment count change (ADDED)
                if (snapshot.hasChild("commentCount")) {
                    val commentCount = snapshot.child("commentCount").getValue(Int::class.java) ?: 0
                    if (currentChat.commentCount != commentCount) {
                        currentChat.commentCount = commentCount
                        adapter.notifyItemChanged(position, "comments")
                    }
                } else if (currentChat.comments.size != updatedChat.comments.size) {
                    // Fallback to comments list size if commentCount field is not available
                    currentChat.comments = updatedChat.comments
                    adapter.notifyItemChanged(position, "comments")
                }

                // Handle other changes with a full update if needed
                if (needsFullUpdate) {
                    messages[position] = updatedChat
                    adapter.notifyItemChanged(position)
                }
            }

            // Other methods remain the same
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val chatId = snapshot.key ?: return
                val position = messagePositions[chatId] ?: return

                messages.removeAt(position)
                messagePositions.remove(chatId)

                // Update positions map
                updatePositionsMap()

                adapter.notifyItemRemoved(position)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not handling moves for this implementation
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error with chat listener", error.toException())
            }
        })
    }

    private fun setupPollListener(pollsRef: com.google.firebase.database.DatabaseReference) {
        pollsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val pollId = snapshot.key ?: return
                if (messagePositions.containsKey(pollId)) return

                // Use helper method to properly read the poll with comments
                val poll = FirebaseDataHelper.getPollMessageFromSnapshot(snapshot) ?: return

                // Add to the list (at the beginning for newest first)
                messages.add(0, poll)

                // Update positions map
                updatePositionsMap()

                adapter.notifyItemInserted(0)
                binding.recyclerViewMessages.scrollToPosition(0)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val pollId = snapshot.key ?: return
                val position = messagePositions[pollId] ?: return

                // Use helper method to properly read the updated poll with comments
                val updatedPoll = FirebaseDataHelper.getPollMessageFromSnapshot(snapshot) ?: return

                val currentPoll = messages[position] as? PollMessage ?: return

                // Check what changed and update accordingly
                var optionsChanged = false

                // Only update reactions if they changed
                if (currentPoll.reactions != updatedPoll.reactions) {
                    currentPoll.reactions = updatedPoll.reactions
                    adapter.notifyItemChanged(position, "reaction")
                }

                // Only update hit/miss if they changed
                if (currentPoll.hit != updatedPoll.hit || currentPoll.miss != updatedPoll.miss) {
                    currentPoll.hit = updatedPoll.hit
                    currentPoll.miss = updatedPoll.miss
                    adapter.notifyItemChanged(position, "hit_miss")
                }

                // Check for comment count change (ADDED)
                if (snapshot.hasChild("commentCount")) {
                    val commentCount = snapshot.child("commentCount").getValue(Int::class.java) ?: 0
                    if (currentPoll.commentCount != commentCount) {
                        currentPoll.commentCount = commentCount
                        adapter.notifyItemChanged(position, "comments")
                    }
                } else if (currentPoll.comments.size != updatedPoll.comments.size) {
                    // Fallback to comments list size if commentCount field is not available
                    currentPoll.comments = updatedPoll.comments
                    adapter.notifyItemChanged(position, "comments")
                }

                // Poll options require a full update if changed
                if (currentPoll.options != updatedPoll.options ||
                    currentPoll.voters != updatedPoll.voters) {
                    optionsChanged = true
                }

                // Do a full update if poll options changed
                if (optionsChanged) {
                    // Update the voters and options
                    currentPoll.options = updatedPoll.options
                    currentPoll.voters = updatedPoll.voters
                    adapter.notifyItemChanged(position)
                }
            }

            // Other methods remain the same
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val pollId = snapshot.key ?: return
                val position = messagePositions[pollId] ?: return

                messages.removeAt(position)
                messagePositions.remove(pollId)

                // Update positions map
                updatePositionsMap()

                adapter.notifyItemRemoved(position)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not handling moves for this implementation
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error with poll listener", error.toException())
            }
        })
    }

    private fun addMessageWithoutDuplication(newMessages: ArrayList<Any>) {
        // Create a set of message IDs that are already in the list
        val existingIds = messages.mapNotNull {
            when (it) {
                is ChatMessage -> it.id
                is PollMessage -> it.id
                else -> null
            }
        }.toSet()

        // Add only messages that aren't already in the list
        val uniqueNewMessages = newMessages.filter {
            val id = when (it) {
                is ChatMessage -> it.id
                is PollMessage -> it.id
                else -> ""
            }
            !existingIds.contains(id)
        }

        // Add the unique messages to the list
        messages.addAll(uniqueNewMessages)
    }

    private fun loadInitialMessages() {
        // Show progress before loading
        binding.progressSending.visibility = View.VISIBLE
        binding.recyclerViewMessages.visibility = View.GONE

        val chatsRef = database.getReference("NoBallZone/chats")
        val pollsRef = database.getReference("NoBallZone/polls")

        // Clear existing data
        messages.clear()
        messagePositions.clear()
        adapter.notifyDataSetChanged()

        // Load chats first
        chatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Existing code...

                // Now load poll messages
                pollsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Existing code for processing polls...

                        // Hide progress after all data is loaded
                        binding.progressSending.visibility = View.GONE
                        binding.recyclerViewMessages.visibility = View.VISIBLE

                        // Rest of your existing code...
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Hide progress on error
                        binding.progressSending.visibility = View.GONE
                        binding.recyclerViewMessages.visibility = View.VISIBLE

                        Log.e("ChatFragment", "Error loading polls", error.toException())
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                // Hide progress on error
                binding.progressSending.visibility = View.GONE
                binding.recyclerViewMessages.visibility = View.VISIBLE

                Log.e("ChatFragment", "Error loading chats", error.toException())
            }
        })
    }

    private fun updatePositionsMap() {
        // Update the positions map to reflect current positions in the list
        messagePositions.clear()
        messages.forEachIndexed { index, message ->
            when (message) {
                is ChatMessage -> messagePositions[message.id] = index
                is PollMessage -> messagePositions[message.id] = index
            }
        }
    }

    // Replace the loadTopHitMessages, loadTopMissMessages, and loadTeamMessages methods with these fixed versions

    private fun loadTopHitMessages() {
        val chatsRef = database.getReference("NoBallZone/chats")

        // Clear existing data
        messages.clear()
        messagePositions.clear()

        // Load chat messages with high hits (only messages with hits > 0)
        chatsRef.orderByChild("hit").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMessages = ArrayList<Any>()

                for (chatSnapshot in snapshot.children) {
                    // Use helper method to properly read the chat with comments
                    val chat = FirebaseDataHelper.getChatMessageFromSnapshot(chatSnapshot)
                    chat?.let {
                        tempMessages.add(it)
                    }
                }

                // Add to main list
                messages.addAll(tempMessages)

                // Update positions map
                updatePositionsMap()

                // Now load poll messages with high hits
                loadTopHitPolls()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error loading top hit messages", error.toException())
            }
        })
    }

    private fun loadTopHitPolls() {
        val pollsRef = database.getReference("NoBallZone/polls")

        pollsRef.orderByChild("hit").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempPolls = ArrayList<Any>()

                for (pollSnapshot in snapshot.children) {
                    // Use helper method to properly read the poll with comments
                    val poll = FirebaseDataHelper.getPollMessageFromSnapshot(pollSnapshot)
                    poll?.let {
                        tempPolls.add(it)
                    }
                }

                // Add to main list
                messages.addAll(tempPolls)

                // Sort all messages by hit count (descending) and then by timestamp
                messages.sortWith(compareByDescending<Any> {
                    when (it) {
                        is ChatMessage -> it.hit
                        is PollMessage -> it.hit
                        else -> 0
                    }
                }.thenByDescending {
                    when (it) {
                        is ChatMessage -> it.timestamp
                        is PollMessage -> it.timestamp
                        else -> 0L
                    }
                })

                // Update positions map
                updatePositionsMap()

                adapter.notifyDataSetChanged()

                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error loading top hit polls", error.toException())
            }
        })
    }

    private fun loadTopMissMessages() {
        val chatsRef = database.getReference("NoBallZone/chats")

        // Clear existing data
        messages.clear()
        messagePositions.clear()

        // Load chat messages with high misses (only messages with misses > 0)
        chatsRef.orderByChild("miss").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMessages = ArrayList<Any>()

                for (chatSnapshot in snapshot.children) {
                    // Use helper method to properly read the chat with comments
                    val chat = FirebaseDataHelper.getChatMessageFromSnapshot(chatSnapshot)
                    chat?.let {
                        tempMessages.add(it)
                    }
                }

                // Add to main list
                messages.addAll(tempMessages)

                // Update positions map
                updatePositionsMap()

                // Now load poll messages with high misses
                loadTopMissPolls()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error loading top miss messages", error.toException())
            }
        })
    }

    private fun loadTopMissPolls() {
        val pollsRef = database.getReference("NoBallZone/polls")

        pollsRef.orderByChild("miss").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempPolls = ArrayList<Any>()

                for (pollSnapshot in snapshot.children) {
                    // Use helper method to properly read the poll with comments
                    val poll = FirebaseDataHelper.getPollMessageFromSnapshot(pollSnapshot)
                    poll?.let {
                        tempPolls.add(it)
                    }
                }

                // Add to main list
                messages.addAll(tempPolls)

                // Sort all messages by miss count (descending) and then by timestamp
                messages.sortWith(compareByDescending<Any> {
                    when (it) {
                        is ChatMessage -> it.miss
                        is PollMessage -> it.miss
                        else -> 0
                    }
                }.thenByDescending {
                    when (it) {
                        is ChatMessage -> it.timestamp
                        is PollMessage -> it.timestamp
                        else -> 0L
                    }
                })

                // Update positions map
                updatePositionsMap()

                adapter.notifyDataSetChanged()

                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error loading top miss polls", error.toException())
            }
        })
    }

    private fun loadTeamMessages(team: String) {
        val chatsRef = database.getReference("NoBallZone/chats")

        // Clear existing data
        messages.clear()
        messagePositions.clear()

        // Load team chat messages
        chatsRef.orderByChild("team").equalTo(team).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMessages = ArrayList<Any>()

                for (chatSnapshot in snapshot.children) {
                    // Use helper method to properly read the chat with comments
                    val chat = FirebaseDataHelper.getChatMessageFromSnapshot(chatSnapshot)
                    chat?.let {
                        tempMessages.add(it)
                    }
                }

                // Add to main list
                messages.addAll(tempMessages)

                // Update positions map
                updatePositionsMap()

                // Load team poll messages
                loadTeamPolls(team)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error loading team messages", error.toException())
            }
        })
    }

    private fun loadTeamPolls(team: String) {
        val pollsRef = database.getReference("NoBallZone/polls")

        pollsRef.orderByChild("team").equalTo(team).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempPolls = ArrayList<Any>()

                for (pollSnapshot in snapshot.children) {
                    // Use helper method to properly read the poll with comments
                    val poll = FirebaseDataHelper.getPollMessageFromSnapshot(pollSnapshot)
                    poll?.let {
                        tempPolls.add(it)
                    }
                }

                // Add to main list
                messages.addAll(tempPolls)

                // Sort all messages by timestamp
                messages.sortByDescending {
                    when (it) {
                        is ChatMessage -> it.timestamp
                        is PollMessage -> it.timestamp
                        else -> 0L
                    }
                }

                // Update positions map
                updatePositionsMap()

                adapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error loading team polls", error.toException())
            }
        })
    }

    private fun loadPollsOnly() {
        // Clear existing data
        messages.clear()
        messagePositions.clear()
        adapter.notifyDataSetChanged() // Notify adapter about the clear to prevent issues

        val pollsRef = database.getReference("NoBallZone/polls")

        pollsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempPolls = ArrayList<Any>()

                for (pollSnapshot in snapshot.children) {
                    // Use helper method to properly read the poll with comments
                    val poll = FirebaseDataHelper.getPollMessageFromSnapshot(pollSnapshot)
                    poll?.let {
                        tempPolls.add(it)
                    }
                }

                // Sort all messages by timestamp (descending)
                tempPolls.sortByDescending {
                    when (it) {
                        is PollMessage -> it.timestamp
                        else -> 0L
                    }
                }

                // Make sure list is clear before adding
                if (messages.isNotEmpty()) {
                    messages.clear()
                    messagePositions.clear()
                }

                // Add to main list
                messages.addAll(tempPolls)

                // Update positions map
                updatePositionsMap()

                adapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                Log.e("ChatFragment", "Error loading polls", error.toException())
            }
        })
    }

    private fun showCreatePollDialog() {
        if (currentUser == null) {
            // Handle not logged in
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.poll_create_dialog, null)
        val editTextQuestion = dialogView.findViewById<EditText>(R.id.editTextPollQuestion)
        val layoutOptions = dialogView.findViewById<LinearLayout>(R.id.layoutOptions)
        val layoutAdditionalOptions = dialogView.findViewById<LinearLayout>(R.id.layoutAdditionalOptions)
        val buttonAddOption = dialogView.findViewById<Button>(R.id.buttonAddOption)

        // Set up the initial two options
        val optionLayouts = ArrayList<View>()
        val addOption = {
            // Use the poll creation option layout which has the EditText
            val optionView = LayoutInflater.from(context).inflate(R.layout.item_creation_poll_option, null)
            layoutAdditionalOptions.addView(optionView)
            optionLayouts.add(optionView)
        }

        // Add two options initially to the main options layout
        for (i in 0 until 2) {
            // Use the poll creation option layout which has the EditText
            val optionView = LayoutInflater.from(context).inflate(R.layout.item_creation_poll_option, null)
            layoutOptions.addView(optionView)
            optionLayouts.add(optionView)
        }

        // Add option button
        buttonAddOption.setOnClickListener {
            if (optionLayouts.size < 6) { // Limit to 6 options total
                addOption()
            } else {
                Toast.makeText(context, "Maximum 6 options allowed", Toast.LENGTH_SHORT).show()
            }
        }

        // Create and show dialog
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertForImagePreview)
            .setTitle("Create Poll")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                createPoll(editTextQuestion.text.toString(), optionLayouts)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun createPoll(question: String, optionViews: List<View>) {
        if (question.trim().isEmpty() || currentUser == null) return

        val options = mutableMapOf<String, Int>()

        // Collect options - using the correct ID from item_poll_creation_option.xml
        for (optionView in optionViews) {
            val editTextOption = optionView.findViewById<EditText>(R.id.editTextOption)
            val optionText = editTextOption?.text?.toString()?.trim() ?: ""

            if (optionText.isNotEmpty()) {
                options[optionText] = 0
            }
        }

        // Need at least two options
        if (options.size < 2) {
            Toast.makeText(context, "Please provide at least two options", Toast.LENGTH_SHORT).show()
            return
        }

        // Check question text for toxicity
        moderationService.checkMessageContent(question, object : ChatModerationService.ModerationCallback {
            override fun onMessageApproved(message: String) {
                // Question is approved, proceed with creating poll
                createPollInFirebase(message, options)
            }

            override fun onMessageRejected(message: String, reason: String) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(errorMessage: String) {
                // On error, allow poll creation to avoid blocking users
                createPollInFirebase(question, options)
            }
        })
    }

    private fun createPollInFirebase(question: String, options: MutableMap<String, Int>) {
        val pollRef = database.getReference("NoBallZone/polls").push()
        val pollId = pollRef.key ?: return

        // Get user's display name and team
        val userRef = database.getReference("Users/${currentUser?.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java) ?: currentUser?.displayName ?: "Anonymous"
                val userTeam = snapshot.child("iplTeam").getValue(String::class.java) ?: "No Team"

                val pollMessage = PollMessage(
                    id = pollId,
                    senderId = currentUser?.uid ?: "",
                    senderName = userName,
                    team = userTeam,
                    question = question,
                    options = options.toMutableMap(),
                    timestamp = System.currentTimeMillis(),
                    voters = mutableMapOf() // Initialize empty voters map
                )

                pollRef.setValue(pollMessage)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Poll created successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        // Handle error
                        Log.e("ChatFragment", "Error creating poll", it)
                        Toast.makeText(context, "Failed to create poll", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                // Fallback to default user info
                val pollMessage = PollMessage(
                    id = pollId,
                    senderId = currentUser?.uid ?: "",
                    senderName = currentUser?.displayName ?: "Anonymous",
                    team = userTeam, // Using class member
                    question = question,
                    options = options.toMutableMap(),
                    timestamp = System.currentTimeMillis(),
                    voters = mutableMapOf() // Initialize empty voters map
                )

                pollRef.setValue(pollMessage)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Poll created successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        // Handle error
                        Log.e("ChatFragment", "Error creating poll", it)
                        Toast.makeText(context, "Failed to create poll", Toast.LENGTH_SHORT).show()
                    }
            }
        })
    }

}