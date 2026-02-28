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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val valueEventListeners = HashMap<DatabaseReference, ValueEventListener>()
    private val childEventListeners = HashMap<DatabaseReference, ChildEventListener>()
    private lateinit var adapter: ChatAdapter
    private lateinit var messages: ArrayList<Any>
    private lateinit var database: FirebaseDatabase
    private lateinit var storageRef: StorageReference
    private lateinit var moderationService: ChatModerationService
    private var currentUser = FirebaseAuth.getInstance().currentUser
    private var userTeam: String = "CSK"
    private val PICK_IMAGE_REQUEST = 1
    private val LOGIN_REQUEST_CODE = 1001
    private var selectedImageUri: Uri? = null
    private lateinit var safetyChecker: CloudVisionSafetyChecker
    private var highlightMessageId: String? = null
    private val messagePositions = mutableMapOf<String, Int>()
    private var selectedFilter = mutableSetOf<String>()

    // ── NEW: Room arguments ───────────────────────────────────────────────────
    // roomId:   "global" | "CSK" | "MI" | … | Firebase-push-key for LIVE rooms
    // roomType: "GLOBAL" | "TEAM" | "LIVE"
    private var roomId: String = "global"
    private var roomType: RoomType = RoomType.GLOBAL
    private var roomName: String = "No Ball Zone"

    /**
     * Firebase base path for the current room.
     *   GLOBAL → NoBallZone
     *   TEAM   → TeamRooms/CSK
     *   LIVE   → NoBallZone/liveRooms/<id>
     */
    private val roomBasePath: String
        get() = when (roomType) {
            RoomType.GLOBAL -> "NoBallZone"
            RoomType.TEAM   -> "TeamRooms/$roomId"
            RoomType.LIVE   -> "NoBallZone/liveRooms/$roomId"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        highlightMessageId = arguments?.getString("HIGHLIGHT_MESSAGE_ID")

        // Read room arguments supplied by ChatLobbyFragment
        roomId   = arguments?.getString("ROOM_ID")   ?: "global"
        roomType = RoomType.valueOf(arguments?.getString("ROOM_TYPE") ?: "GLOBAL")
        roomName = arguments?.getString("ROOM_NAME")  ?: "No Ball Zone"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val height = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation).height
            val bottomInsets = if (imeVisible) {
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - height
            } else {
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            }
            view.setPadding(0, 0, 0, bottomInsets)
            insets
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        moderationService = ChatModerationService(requireContext())
        safetyChecker = CloudVisionSafetyChecker(requireContext())
        database = FirebaseDatabase.getInstance()

        val storage = FirebaseStorage.getInstance()
        storageRef = storage.reference

        updateUIBasedOnLoginStatus()

        if (isUserLoggedIn()) {
            loadProfilePhoto()
            fetchUserTeam()
        } else {
            binding.profilePhoto.setImageResource(R.drawable.profile_icon)
        }

        messages = ArrayList()
        adapter = ChatAdapter(messages, roomBasePath)
        binding.recyclerViewMessages.apply {
            val layoutManager = LinearLayoutManager(context)
            layoutManager.orientation = LinearLayoutManager.VERTICAL
            layoutManager.reverseLayout = true
            layoutManager.stackFromEnd = true
            this.layoutManager = layoutManager
            adapter = this@ChatFragment.adapter
        }

        setupFilters()
        setupFirebaseListeners()

        binding.buttonSend.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to send messages"); return@setOnClickListener }
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) checkAndSendMessage(messageText)
        }

        binding.buttonMeme.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to share images"); return@setOnClickListener }
            openImagePicker()
        }

        binding.buttonPoll.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to create polls"); return@setOnClickListener }
            showCreatePollDialog()
        }

        binding.leaderBoardIcon.setOnClickListener {
            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.leaderboardIcon
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, LeaderboardFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.profilePhoto.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to view your profile"); return@setOnClickListener }
            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.profileIcon
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        if (!highlightMessageId.isNullOrEmpty()) {
            Handler(Looper.getMainLooper()).postDelayed({
                scrollToAndHighlightMessage(highlightMessageId!!)
            }, 500)
        }
    }

    // ── Room-aware Firebase paths ─────────────────────────────────────────────

    /** Chats reference for the current room */
    private fun chatsRef() = database.getReference("$roomBasePath/chats")

    /** Polls reference for the current room */
    private fun pollsRef() = database.getReference("$roomBasePath/polls")

    // ── Highlight ─────────────────────────────────────────────────────────────

    private fun scrollToAndHighlightMessage(messageId: String) {
        if (!isAdded || _binding == null) return
        val position = adapter.findPositionById(messageId)
        if (position != -1) {
            binding.recyclerViewMessages.scrollToPosition(position)
            val fragmentRef = WeakReference(this)
            Handler(Looper.getMainLooper()).postDelayed({
                val fragment = fragmentRef.get() ?: return@postDelayed
                if (!fragment.isAdded || fragment._binding == null) return@postDelayed
                fragment.binding.recyclerViewMessages
                    .findViewHolderForAdapterPosition(position)
                    ?.itemView?.let { fragment.applyHighlightAnimation(it) }
            }, 100)
        }
    }

    private fun applyHighlightAnimation(view: View) {
        val originalBackground = view.background
        view.setBackgroundResource(R.drawable.highlighted_item_background)
        Handler(Looper.getMainLooper()).postDelayed({ view.background = originalBackground }, 1500)
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    private fun isUserLoggedIn() = FirebaseAuth.getInstance().currentUser != null

    private fun showLoginPrompt(message: String) {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                startActivityForResult(Intent(requireContext(), SignIn::class.java), LOGIN_REQUEST_CODE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUIBasedOnLoginStatus() {
        val isLoggedIn = isUserLoggedIn()
        if (!isLoggedIn) {
            binding.editTextMessage.hint = "Login to send messages"
            binding.buttonSend.alpha = 0.5f
            binding.buttonMeme.alpha = 0.5f
            binding.buttonPoll.alpha = 0.5f
        } else {
            binding.editTextMessage.hint = "Type a message"
            binding.buttonSend.alpha = 1.0f
            binding.buttonMeme.alpha = 1.0f
            binding.buttonPoll.alpha = 1.0f
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    private fun loadProfilePhoto() {
        if (!isAdded || _binding == null) return
        val userId = currentUser?.uid ?: return
        val userRef = database.getReference("Users/$userId/profilePhoto")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                val photoUrl = snapshot.getValue(String::class.java)
                if (!photoUrl.isNullOrEmpty()) {
                    try {
                        context?.let { ctx ->
                            Glide.with(ctx).load(photoUrl)
                                .placeholder(R.drawable.profile_icon)
                                .into(_binding?.profilePhoto ?: return)
                        }
                    } catch (e: Exception) { }
                }
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        userRef.addListenerForSingleValueEvent(listener)
        valueEventListeners[userRef] = listener
    }

    private fun fetchUserTeam() {
        currentUser?.uid?.let { userId ->
            database.getReference("Users/$userId/iplTeam")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        userTeam = snapshot.getValue(String::class.java) ?: "No Team"
                    }
                    override fun onCancelled(error: DatabaseError) { }
                })
        }
    }

    // ── Image picker ──────────────────────────────────────────────────────────

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
        Glide.with(requireContext()).load(selectedImageUri).into(imagePreview)

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertForImagePreview)
            .setTitle("Send Image")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val caption = editTextCaption.text.toString().trim()
                val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
                val progressDialog = MaterialAlertDialogBuilder(requireContext())
                    .setView(progressView).setCancelable(false).setTitle("Uploading image...").create()
                progressDialog.show()
                uploadAndSendImage(caption) { success ->
                    progressDialog.dismiss()
                    if (!success) Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> selectedImageUri = null; dialog.dismiss() }
            .create().show()
    }

    // ── Moderation + send ─────────────────────────────────────────────────────

    private fun checkAndSendMessage(messageText: String) {
        binding.buttonSend.isEnabled = false
        binding.progressSending.visibility = View.VISIBLE
        moderationService.checkMessageContent(messageText, object : ChatModerationService.ModerationCallback {
            override fun onMessageApproved(message: String) {
                requireActivity().runOnUiThread {
                    sendMessageToFirebase(message)
                    binding.buttonSend.isEnabled = true
                    binding.progressSending.visibility = View.GONE
                }
            }
            override fun onMessageRejected(message: String, reason: String) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                    binding.buttonSend.isEnabled = true
                    binding.progressSending.visibility = View.GONE
                }
            }
            override fun onError(errorMessage: String) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Unable to check message content. Please try again.", Toast.LENGTH_SHORT).show()
                    binding.buttonSend.isEnabled = true
                    binding.progressSending.visibility = View.GONE
                }
            }
        })
    }

    private fun sendMessageToFirebase(messageText: String) {
        if (currentUser == null) { Toast.makeText(context, "You must be logged in to send messages", Toast.LENGTH_SHORT).show(); return }

        // ← uses room-aware chatsRef()
        val chatRef = chatsRef().push()
        val chatId = chatRef.key ?: return

        database.getReference("Users/${currentUser!!.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userName = snapshot.child("username").getValue(String::class.java)
                        ?: currentUser!!.displayName ?: "Anonymous"
                    val chatMessage = ChatMessage(
                        id = chatId, senderId = currentUser!!.uid, senderName = userName,
                        team = userTeam, message = messageText, timestamp = System.currentTimeMillis()
                    )
                    chatRef.setValue(chatMessage)
                        .addOnSuccessListener { binding.editTextMessage.text.clear() }
                        .addOnFailureListener { Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show() }
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun uploadAndSendImage(caption: String, onComplete: ((Boolean) -> Unit)? = null) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        selectedImageUri?.let { uri ->
            val imageRef = FirebaseStorage.getInstance().reference
                .child("chat_images/${System.currentTimeMillis()}_${currentUser.uid}.jpg")
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        if (caption.isNotEmpty()) {
                            moderationService.checkMessageContent(caption, object : ChatModerationService.ModerationCallback {
                                override fun onMessageApproved(message: String) {
                                    sendImageMessageToFirebase(message, downloadUrl.toString()); onComplete?.invoke(true)
                                }
                                override fun onMessageRejected(message: String, reason: String) {
                                    requireActivity().runOnUiThread {
                                        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                                        sendImageMessageToFirebase("", downloadUrl.toString()); onComplete?.invoke(true)
                                    }
                                }
                                override fun onError(errorMessage: String) {
                                    sendImageMessageToFirebase(caption, downloadUrl.toString()); onComplete?.invoke(true)
                                }
                            })
                        } else {
                            sendImageMessageToFirebase("", downloadUrl.toString()); onComplete?.invoke(true)
                        }
                        selectedImageUri = null
                    }
                }
                .addOnFailureListener { onComplete?.invoke(false) }
        }
    }

    private fun sendImageMessageToFirebase(message: String, imageUrl: String) {
        if (currentUser == null) return
        // ← uses room-aware chatsRef()
        val chatRef = chatsRef().push()
        val chatId = chatRef.key ?: return

        database.getReference("Users/${currentUser!!.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userName = snapshot.child("username").getValue(String::class.java)
                        ?: currentUser!!.displayName ?: "Anonymous"
                    val userTeam = snapshot.child("iplTeam").getValue(String::class.java) ?: "No Team"
                    val chatMessage = ChatMessage(
                        id = chatId, senderId = currentUser!!.uid, senderName = userName,
                        team = userTeam, message = message, imageUrl = imageUrl,
                        timestamp = System.currentTimeMillis()
                    )
                    chatRef.setValue(chatMessage)
                        .addOnSuccessListener { binding.editTextMessage.text.clear() }
                }
                override fun onCancelled(error: DatabaseError) { }
            })
    }

    private fun showContentBlockedDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) "This image has been blocked because our system detected prohibited content."
        else "This image has been blocked because our system detected:\n\n${issues.take(3).joinToString("\n")}"
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Content Blocked")
            .setMessage("$message\n\nOur community guidelines prohibit sharing content that contains adult material, violence, hate speech, or other potentially harmful imagery.")
            .setPositiveButton("Select Different Image") { dialog, _ -> dialog.dismiss(); selectedImageUri = null; openImagePicker() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss(); selectedImageUri = null }
            .show()
    }

    private fun showContentWarningDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) "This image may contain inappropriate content."
        else "This image may contain inappropriate content:\n\n${issues.take(3).joinToString("\n")}"
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Content Warning")
            .setMessage("$message\n\nWhile this image doesn't automatically violate our guidelines, it may be inappropriate for some viewers or contexts.")
            .setPositiveButton("Upload Anyway") { dialog, _ -> dialog.dismiss(); showImagePreviewDialog() }
            .setNeutralButton("Select Different Image") { dialog, _ -> dialog.dismiss(); selectedImageUri = null; openImagePicker() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss(); selectedImageUri = null }
            .show()
    }

    private fun checkImageSafetyAndShowPreview() {
        if (selectedImageUri == null || !isUserLoggedIn()) return
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView).setCancelable(false).setTitle("Checking content safety...").create()
        progressDialog.show()
        selectedImageUri?.let { uri ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = withContext(Dispatchers.IO) { safetyChecker.checkImageSafety(uri) }
                    progressDialog.dismiss()
                    when {
                        result.isSafe    -> showImagePreviewDialog()
                        result.autoBlock -> showContentBlockedDialog(result.issues)
                        else             -> showContentWarningDialog(result.issues)
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Error").setMessage("Failed to analyze image: ${e.message}")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }.show()
                }
            }
        } ?: run { progressDialog.dismiss(); Toast.makeText(context, "No image selected", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            selectedImageUri = data.data
            checkImageSafetyAndShowPreview()
        } else if (requestCode == LOGIN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto(); fetchUserTeam(); updateUIBasedOnLoginStatus()
            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIBasedOnLoginStatus()
        if (isUserLoggedIn() && currentUser != FirebaseAuth.getInstance().currentUser) {
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto(); fetchUserTeam()
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
            "TopHits" -> binding.chipTopHits; "TopMiss" -> binding.chipTopMiss
            "Polls"   -> binding.chipPolls;   "CSK"     -> binding.chipCSK
            "MI"      -> binding.chipMI;      "DC"      -> binding.chipDC
            "GT"      -> binding.chipGT;      "KKR"     -> binding.chipKKR
            "LSG"     -> binding.chipLSG;     "RCB"     -> binding.chipRCB
            "PBKS"    -> binding.chipPBKS;    "RR"      -> binding.chipRR
            "SRH"     -> binding.chipSRH;     else      -> null
        }
        val isSelected = selectedFilter.contains(filter)
        selectedFilter.clear()
        listOf(binding.chipTopHits, binding.chipTopMiss, binding.chipPolls, binding.chipCSK,
            binding.chipMI, binding.chipDC, binding.chipGT, binding.chipKKR, binding.chipLSG,
            binding.chipRCB, binding.chipPBKS, binding.chipRR, binding.chipSRH).forEach { c ->
            c.isChecked = false; c.chipStrokeWidth = 0f
            c.setTextColor(resources.getColor(R.color.white, null))
        }
        binding.chipAll.isChecked = false; binding.chipAll.chipStrokeWidth = 0f
        binding.chipAll.setTextColor(resources.getColor(R.color.white, null))
        if (isSelected) { resetFilters(); return }
        selectedFilter.add(filter)
        chip?.apply { isChecked = true; chipStrokeWidth = 2f; setTextColor(resources.getColor(R.color.grey, null)) }
        action.invoke()
    }

    private fun resetFilters() {
        selectedFilter.clear()
        binding.chipAll.apply { isChecked = true; chipStrokeWidth = 2f; setTextColor(resources.getColor(R.color.grey, null)) }
        listOf(binding.chipTopHits, binding.chipTopMiss, binding.chipPolls, binding.chipCSK,
            binding.chipMI, binding.chipDC, binding.chipGT, binding.chipKKR, binding.chipLSG,
            binding.chipRCB, binding.chipPBKS, binding.chipRR, binding.chipSRH).forEach { chip ->
            chip.isChecked = false; chip.chipStrokeWidth = 0f
            chip.setTextColor(resources.getColor(R.color.white, null))
        }
        messages.clear(); messagePositions.clear(); adapter.notifyDataSetChanged()
        loadMessages()
    }

    private fun setupFirebaseListeners() {
        messages.clear(); messagePositions.clear()
        valueEventListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        childEventListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        valueEventListeners.clear(); childEventListeners.clear()
        loadMessages()
    }

    private fun loadMessages() {
        messages.clear(); messagePositions.clear()
        // ← Both use room-aware refs
        setupChatListener(chatsRef())
        setupPollListener(pollsRef())
        loadInitialMessages()
    }

    private fun setupChatListener(chatsRef: DatabaseReference) {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isAdded || _binding == null) return
                val chatId = snapshot.key ?: return
                if (messagePositions.containsKey(chatId)) return
                val chat = FirebaseDataHelper.getChatMessageFromSnapshot(snapshot) ?: return
                messages.add(0, chat); updatePositionsMap(); adapter.notifyItemInserted(0)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isAdded || _binding == null) return
                val chatId = snapshot.key ?: return
                val position = messagePositions[chatId] ?: return
                val updatedChat = FirebaseDataHelper.getChatMessageFromSnapshot(snapshot) ?: return
                val currentChat = messages[position] as? ChatMessage ?: return
                if (currentChat.reactions != updatedChat.reactions) {
                    currentChat.reactions = updatedChat.reactions; adapter.notifyItemChanged(position, "reaction")
                }
                if (currentChat.hit != updatedChat.hit || currentChat.miss != updatedChat.miss) {
                    currentChat.hit = updatedChat.hit; currentChat.miss = updatedChat.miss
                    adapter.notifyItemChanged(position, "hit_miss")
                }
                if (snapshot.hasChild("commentCount")) {
                    val commentCount = snapshot.child("commentCount").getValue(Int::class.java) ?: 0
                    if (currentChat.commentCount != commentCount) {
                        currentChat.commentCount = commentCount; adapter.notifyItemChanged(position, "comments")
                    }
                } else if (currentChat.comments.size != updatedChat.comments.size) {
                    currentChat.comments = updatedChat.comments; adapter.notifyItemChanged(position, "comments")
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                val chatId = snapshot.key ?: return
                val position = messagePositions[chatId] ?: return
                messages.removeAt(position); messagePositions.remove(chatId)
                updatePositionsMap(); adapter.notifyItemRemoved(position)
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatFragment", "Error with chat listener", error.toException())
            }
        }
        chatsRef.addChildEventListener(listener)
        childEventListeners[chatsRef] = listener
    }

    private fun setupPollListener(pollsRef: DatabaseReference) {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isAdded || _binding == null) return
                val pollId = snapshot.key ?: return
                if (messagePositions.containsKey(pollId)) return
                val poll = FirebaseDataHelper.getPollMessageFromSnapshot(snapshot) ?: return
                messages.add(0, poll); updatePositionsMap(); adapter.notifyItemInserted(0)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isAdded || _binding == null) return
                val pollId = snapshot.key ?: return
                val position = messagePositions[pollId] ?: return
                val updatedPoll = FirebaseDataHelper.getPollMessageFromSnapshot(snapshot) ?: return
                val currentPoll = messages[position] as? PollMessage ?: return
                if (currentPoll.reactions != updatedPoll.reactions) {
                    currentPoll.reactions = updatedPoll.reactions; adapter.notifyItemChanged(position, "reaction")
                }
                if (currentPoll.hit != updatedPoll.hit || currentPoll.miss != updatedPoll.miss) {
                    currentPoll.hit = updatedPoll.hit; currentPoll.miss = updatedPoll.miss
                    adapter.notifyItemChanged(position, "hit_miss")
                }
                if (snapshot.hasChild("commentCount")) {
                    val commentCount = snapshot.child("commentCount").getValue(Int::class.java) ?: 0
                    if (currentPoll.commentCount != commentCount) {
                        currentPoll.commentCount = commentCount; adapter.notifyItemChanged(position, "comments")
                    }
                } else if (currentPoll.comments.size != updatedPoll.comments.size) {
                    currentPoll.comments = updatedPoll.comments; adapter.notifyItemChanged(position, "comments")
                }
                if (currentPoll.options != updatedPoll.options || currentPoll.voters != updatedPoll.voters) {
                    currentPoll.options = updatedPoll.options; currentPoll.voters = updatedPoll.voters
                    adapter.notifyItemChanged(position)
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                val pollId = snapshot.key ?: return
                val position = messagePositions[pollId] ?: return
                messages.removeAt(position); messagePositions.remove(pollId)
                updatePositionsMap(); adapter.notifyItemRemoved(position)
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        pollsRef.addChildEventListener(listener)
        childEventListeners[pollsRef] = listener
    }

    private fun loadInitialMessages() {
        binding.llAnime2.visibility = View.VISIBLE
        binding.recyclerViewMessages.visibility = View.GONE
        messages.clear(); messagePositions.clear()
        val allMessages = ArrayList<Any>()
        var chatsLoaded = false
        var pollsLoaded = false

        chatsRef().addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                for (chatSnapshot in snapshot.children) {
                    FirebaseDataHelper.getChatMessageFromSnapshot(chatSnapshot)?.let { allMessages.add(it) }
                }
                chatsLoaded = true
                if (pollsLoaded) finishLoadingMessages(allMessages)
            }
            override fun onCancelled(error: DatabaseError) {
                if (!isAdded || _binding == null) return
                chatsLoaded = true
                binding.llAnime2.visibility = View.GONE
                binding.recyclerViewMessages.visibility = View.VISIBLE
            }
        })

        pollsRef().addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                for (pollSnapshot in snapshot.children) {
                    FirebaseDataHelper.getPollMessageFromSnapshot(pollSnapshot)?.let { allMessages.add(it) }
                }
                pollsLoaded = true
                if (chatsLoaded) finishLoadingMessages(allMessages)
            }
            override fun onCancelled(error: DatabaseError) {
                if (!isAdded || _binding == null) return
                pollsLoaded = true
                binding.llAnime2.visibility = View.GONE
                binding.recyclerViewMessages.visibility = View.VISIBLE
            }
        })
    }

    private fun finishLoadingMessages(allMessages: ArrayList<Any>) {
        if (!isAdded || _binding == null) return
        allMessages.sortByDescending {
            when (it) { is ChatMessage -> it.timestamp; is PollMessage -> it.timestamp; else -> 0L }
        }
        messages.clear(); messages.addAll(allMessages)
        updatePositionsMap(); adapter.notifyDataSetChanged()
        binding.llAnime2.visibility = View.GONE
        binding.recyclerViewMessages.visibility = View.VISIBLE
        if (messages.isNotEmpty()) binding.recyclerViewMessages.scrollToPosition(0)
    }

    private fun updatePositionsMap() {
        messagePositions.clear()
        messages.forEachIndexed { index, message ->
            when (message) { is ChatMessage -> messagePositions[message.id] = index; is PollMessage -> messagePositions[message.id] = index }
        }
    }

    private fun loadTopHitMessages() {
        messages.clear(); messagePositions.clear()
        chatsRef().orderByChild("hit").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = ArrayList<Any>()
                for (s in snapshot.children) FirebaseDataHelper.getChatMessageFromSnapshot(s)?.let { temp.add(it) }
                messages.addAll(temp); updatePositionsMap(); loadTopHitPolls()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadTopHitPolls() {
        pollsRef().orderByChild("hit").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = ArrayList<Any>()
                for (s in snapshot.children) FirebaseDataHelper.getPollMessageFromSnapshot(s)?.let { temp.add(it) }
                messages.addAll(temp)
                messages.sortWith(compareByDescending<Any> {
                    when (it) { is ChatMessage -> it.hit; is PollMessage -> it.hit; else -> 0 }
                }.thenByDescending { when (it) { is ChatMessage -> it.timestamp; is PollMessage -> it.timestamp; else -> 0L } })
                updatePositionsMap(); adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) binding.recyclerViewMessages.scrollToPosition(0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadTopMissMessages() {
        messages.clear(); messagePositions.clear()
        chatsRef().orderByChild("miss").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = ArrayList<Any>()
                for (s in snapshot.children) FirebaseDataHelper.getChatMessageFromSnapshot(s)?.let { temp.add(it) }
                messages.addAll(temp); updatePositionsMap(); loadTopMissPolls()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadTopMissPolls() {
        pollsRef().orderByChild("miss").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = ArrayList<Any>()
                for (s in snapshot.children) FirebaseDataHelper.getPollMessageFromSnapshot(s)?.let { temp.add(it) }
                messages.addAll(temp)
                messages.sortWith(compareByDescending<Any> {
                    when (it) { is ChatMessage -> it.miss; is PollMessage -> it.miss; else -> 0 }
                }.thenByDescending { when (it) { is ChatMessage -> it.timestamp; is PollMessage -> it.timestamp; else -> 0L } })
                updatePositionsMap(); adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) binding.recyclerViewMessages.scrollToPosition(0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadTeamMessages(team: String) {
        messages.clear(); messagePositions.clear()
        chatsRef().orderByChild("team").equalTo(team).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = ArrayList<Any>()
                for (s in snapshot.children) FirebaseDataHelper.getChatMessageFromSnapshot(s)?.let { temp.add(it) }
                messages.addAll(temp); updatePositionsMap(); loadTeamPolls(team)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadTeamPolls(team: String) {
        pollsRef().orderByChild("team").equalTo(team).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = ArrayList<Any>()
                for (s in snapshot.children) FirebaseDataHelper.getPollMessageFromSnapshot(s)?.let { temp.add(it) }
                messages.addAll(temp)
                messages.sortByDescending { when (it) { is ChatMessage -> it.timestamp; is PollMessage -> it.timestamp; else -> 0L } }
                updatePositionsMap(); adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) binding.recyclerViewMessages.scrollToPosition(0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadPollsOnly() {
        messages.clear(); messagePositions.clear(); adapter.notifyDataSetChanged()
        pollsRef().addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = ArrayList<Any>()
                for (s in snapshot.children) FirebaseDataHelper.getPollMessageFromSnapshot(s)?.let { temp.add(it) }
                temp.sortByDescending { when (it) { is PollMessage -> it.timestamp; else -> 0L } }
                if (messages.isNotEmpty()) { messages.clear(); messagePositions.clear() }
                messages.addAll(temp); updatePositionsMap(); adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) binding.recyclerViewMessages.scrollToPosition(0)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showCreatePollDialog() {
        if (currentUser == null) return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.poll_create_dialog, null)
        val editTextQuestion = dialogView.findViewById<EditText>(R.id.editTextPollQuestion)
        val layoutOptions = dialogView.findViewById<LinearLayout>(R.id.layoutOptions)
        val layoutAdditionalOptions = dialogView.findViewById<LinearLayout>(R.id.layoutAdditionalOptions)
        val buttonAddOption = dialogView.findViewById<Button>(R.id.buttonAddOption)
        val optionLayouts = ArrayList<View>()
        val addOption = {
            val optionView = LayoutInflater.from(context).inflate(R.layout.item_creation_poll_option, null)
            layoutAdditionalOptions.addView(optionView); optionLayouts.add(optionView)
        }
        for (i in 0 until 2) {
            val optionView = LayoutInflater.from(context).inflate(R.layout.item_creation_poll_option, null)
            layoutOptions.addView(optionView); optionLayouts.add(optionView)
        }
        buttonAddOption.setOnClickListener {
            if (optionLayouts.size < 6) addOption()
            else Toast.makeText(context, "Maximum 6 options allowed", Toast.LENGTH_SHORT).show()
        }
        AlertDialog.Builder(requireContext(), R.style.CustomAlertForImagePreview)
            .setTitle("Create Poll")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ -> createPoll(editTextQuestion.text.toString(), optionLayouts) }
            .setNegativeButton("Cancel", null)
            .create().show()
    }

    private fun createPoll(question: String, optionViews: List<View>) {
        if (question.trim().isEmpty() || currentUser == null) return
        val options = mutableMapOf<String, Int>()
        for (optionView in optionViews) {
            val editTextOption = optionView.findViewById<EditText>(R.id.editTextOption)
            val optionText = editTextOption?.text?.toString()?.trim() ?: ""
            if (optionText.isNotEmpty()) options[optionText] = 0
        }
        if (options.size < 2) { Toast.makeText(context, "Please provide at least two options", Toast.LENGTH_SHORT).show(); return }
        moderationService.checkMessageContent(question, object : ChatModerationService.ModerationCallback {
            override fun onMessageApproved(message: String) { createPollInFirebase(message, options) }
            override fun onMessageRejected(message: String, reason: String) {
                requireActivity().runOnUiThread { Toast.makeText(context, reason, Toast.LENGTH_LONG).show() }
            }
            override fun onError(errorMessage: String) { createPollInFirebase(question, options) }
        })
    }

    private fun createPollInFirebase(question: String, options: MutableMap<String, Int>) {
        // ← uses room-aware pollsRef()
        val pollRef = pollsRef().push()
        val pollId = pollRef.key ?: return
        database.getReference("Users/${currentUser?.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userName = snapshot.child("username").getValue(String::class.java) ?: currentUser?.displayName ?: "Anonymous"
                    val userTeam = snapshot.child("iplTeam").getValue(String::class.java) ?: "No Team"
                    val pollMessage = PollMessage(
                        id = pollId, senderId = currentUser?.uid ?: "", senderName = userName, team = userTeam,
                        question = question, options = options.toMutableMap(),
                        timestamp = System.currentTimeMillis(), voters = mutableMapOf()
                    )
                    pollRef.setValue(pollMessage)
                        .addOnSuccessListener { Toast.makeText(context, "Poll created successfully", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { Toast.makeText(context, "Failed to create poll", Toast.LENGTH_SHORT).show() }
                }
                override fun onCancelled(error: DatabaseError) {
                    val pollMessage = PollMessage(
                        id = pollId, senderId = currentUser?.uid ?: "", senderName = currentUser?.displayName ?: "Anonymous",
                        team = userTeam, question = question, options = options.toMutableMap(),
                        timestamp = System.currentTimeMillis(), voters = mutableMapOf()
                    )
                    pollRef.setValue(pollMessage)
                        .addOnSuccessListener { Toast.makeText(context, "Poll created successfully", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { Toast.makeText(context, "Failed to create poll", Toast.LENGTH_SHORT).show() }
                }
            })
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        valueEventListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        childEventListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        valueEventListeners.clear(); childEventListeners.clear()
        Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
        _binding?.recyclerViewMessages?.adapter = null
        _binding = null
    }
}