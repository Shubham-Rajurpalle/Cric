package com.cricketApp.cric.Chat

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.cache.ChatFilterKey
import com.cricketApp.cric.Chat.cache.ChatViewModel
import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Meme.CloudVisionSafetyChecker
import com.cricketApp.cric.Moderation.ChatModerationService
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentChatBinding
import com.cricketApp.cric.home.liveMatch.*
import com.cricketApp.cric.LiveScoreStripAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class ChatFragment : Fragment() {

    // ── Room args ─────────────────────────────────────────────────────────────
    private var roomId:   String   = "global"
    private var roomType: RoomType = RoomType.GLOBAL
    private var roomName: String   = "No Ball Zone"
    private var isFirstLoad = true


    private val roomBasePath get() = when (roomType) {
        RoomType.GLOBAL -> "NoBallZone"
        RoomType.TEAM   -> "TeamRooms/$roomId"
        RoomType.LIVE   -> "NoBallZone/liveRooms/$roomId"
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private val viewModel: ChatViewModel by viewModels(
        factoryProducer = {
            ChatViewModel.Factory(requireActivity().application, roomId, roomType)
        }
    )

    // ── Binding ───────────────────────────────────────────────────────────────
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    // ── Adapter ───────────────────────────────────────────────────────────────
    private lateinit var adapter: ChatAdapter

    // ── Upload ────────────────────────────────────────────────────────────────
    private var selectedImageUri: Uri? = null
    private lateinit var safetyChecker: CloudVisionSafetyChecker
    private lateinit var moderationService: ChatModerationService

    // ── Auth ──────────────────────────────────────────────────────────────────
    private var currentUser = FirebaseAuth.getInstance().currentUser
    private var userTeam = "CSK"

    // ── Live score strip ──────────────────────────────────────────────────────
    private val liveStripMatches = mutableListOf<MatchData>()
    private lateinit var liveStripAdapter: LiveScoreStripAdapter
    private var stripListener: ValueEventListener? = null

    // ── New message banner ────────────────────────────────────────────────────
    private var newMsgCount = 0
    private var initialLoadDone = false

    // ── Deep-link ────────────────────────────────────────────────────────────
    private var highlightMessageId: String? = null

    // ── Activity result launchers ─────────────────────────────────────────────
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri = it; checkImageSafetyAndShowPreview() }
    }
    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto(); fetchUserTeam(); updateUIBasedOnLoginStatus()
            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        highlightMessageId = arguments?.getString("HIGHLIGHT_MESSAGE_ID")
        roomId   = arguments?.getString("ROOM_ID")   ?: "global"
        roomType = RoomType.valueOf(arguments?.getString("ROOM_TYPE") ?: "GLOBAL")
        roomName = arguments?.getString("ROOM_NAME")  ?: "No Ball Zone"

        // Handle notification deep-link path
        arguments?.getString("ROOM_BASE_PATH")?.let { path ->
            when {
                path == "NoBallZone"                        -> { roomType = RoomType.GLOBAL; roomId = "global" }
                path.startsWith("TeamRooms/")               -> { roomType = RoomType.TEAM;   roomId = path.removePrefix("TeamRooms/") }
                path.startsWith("NoBallZone/liveRooms/")    -> { roomType = RoomType.LIVE;   roomId = path.removePrefix("NoBallZone/liveRooms/") }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        safetyChecker    = CloudVisionSafetyChecker(requireContext())
        moderationService = ChatModerationService(requireContext())

        setupAdapter()
        setupRecyclerView()
        setupFilters()
        setupButtons()
        setupLiveScoreStrip()
        observeViewModel()
        updateUIBasedOnLoginStatus()

        if (isUserLoggedIn()) { loadProfilePhoto(); fetchUserTeam() }
        else binding.profilePhoto.setImageResource(R.drawable.profile_icon)

        highlightMessageId?.takeIf { it.isNotEmpty() }?.let { id ->
            Handler(Looper.getMainLooper()).postDelayed({ scrollToAndHighlight(id) }, 600)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter + RecyclerView
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupAdapter() {
        adapter = ChatAdapter(
            items        = mutableListOf(),
            roomBasePath = roomBasePath
        )
    }

    private fun setupRecyclerView() {
        val lm = LinearLayoutManager(context).apply {
            reverseLayout = true; stackFromEnd = true
        }
        binding.recyclerViewMessages.apply {
            layoutManager = lm
            adapter = this@ChatFragment.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy >= 0) return  // only on upward scroll (reversed layout)
                    val total   = lm.itemCount
                    val visible = lm.findLastVisibleItemPosition()
                    if (visible >= total - 6) viewModel.loadNextPage()
                }
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observe ViewModel
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // ── Messages ──────────────────────────────────────────────────
                launch {
                    viewModel.messages.collect { list ->
                        val mutable = list.toMutableList()
                        // Sync adapter directly — preserve existing mutation state
                        syncAdapter(mutable)
                    }
                }

                // ── Pagination state ──────────────────────────────────────────
                launch {
                    viewModel.paginationState.collect { state ->
                        if (_binding == null) return@collect
                        if (state.isLoading && adapter.itemCount == 0) {
                            binding.llAnime2.visibility            = View.VISIBLE
                            binding.recyclerViewMessages.visibility = View.GONE
                        } else {
                            binding.llAnime2.visibility            = View.GONE
                            binding.recyclerViewMessages.visibility = View.VISIBLE
                            if (!state.isLoading && adapter.itemCount > 0 && !initialLoadDone) {
                                initialLoadDone = true
                                // Guarantee we're at the bottom after first load
                                binding.recyclerViewMessages.post {
                                    binding.recyclerViewMessages.scrollToPosition(0)
                                }
                            }
                        }
                        state.error?.let {
                            Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // ── New message banner ────────────────────────────────────────
                launch {
                    viewModel.newMessageEvent.collect { showNewMessageBanner() }
                }

                // ── Removed ───────────────────────────────────────────────────
                launch {
                    viewModel.removedId.collect { id ->
                        val pos = adapter.findPositionById(id)
                        if (pos != -1) adapter.removeMessage(pos, id)
                    }
                }
            }
        }
    }

    /** Push new list into the existing ChatAdapter (which is mutable-list based) */
    private fun syncAdapter(newList: MutableList<Any>) {
        val items = adapter.getItems()
        val wasEmpty = items.isEmpty()
        items.clear()
        items.addAll(newList)
        adapter.updatePositionsMap()
        adapter.notifyDataSetChanged()

        // Scroll to bottom (position 0 in reversed layout) on first load
        if (wasEmpty && newList.isNotEmpty()) {
            binding.recyclerViewMessages.post {
                binding.recyclerViewMessages.scrollToPosition(0)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter chips
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupFilters() {
        binding.chipAll.setOnClickListener     { applyFilter(ChatFilterKey.ALL) }
        binding.chipTopHits.setOnClickListener  { applyFilter(ChatFilterKey.TOP_HIT) }
        binding.chipTopMiss.setOnClickListener  { applyFilter(ChatFilterKey.TOP_MISS) }
        binding.chipPolls.setOnClickListener    { applyFilter(ChatFilterKey.POLLS_ONLY) }
        binding.chipCSK.setOnClickListener     { applyFilter(ChatFilterKey.team("CSK")) }
        binding.chipMI.setOnClickListener      { applyFilter(ChatFilterKey.team("MI"))  }
        binding.chipDC.setOnClickListener      { applyFilter(ChatFilterKey.team("DC"))  }
        binding.chipGT.setOnClickListener      { applyFilter(ChatFilterKey.team("GT"))  }
        binding.chipKKR.setOnClickListener     { applyFilter(ChatFilterKey.team("KKR")) }
        binding.chipLSG.setOnClickListener     { applyFilter(ChatFilterKey.team("LSG")) }
        binding.chipRCB.setOnClickListener     { applyFilter(ChatFilterKey.team("RCB")) }
        binding.chipPBKS.setOnClickListener    { applyFilter(ChatFilterKey.team("PBKS"))}
        binding.chipRR.setOnClickListener      { applyFilter(ChatFilterKey.team("RR"))  }
        binding.chipSRH.setOnClickListener     { applyFilter(ChatFilterKey.team("SRH")) }
        setChipChecked(ChatFilterKey.ALL)
    }

    private fun applyFilter(filterKey: String) {
        val effective = if (viewModel.activeFilter.value == filterKey && filterKey != ChatFilterKey.ALL)
            ChatFilterKey.ALL else filterKey
        setChipChecked(effective)
        hideNewMessageBanner()
        initialLoadDone = false
        viewModel.applyFilter(effective)
    }

    private fun setChipChecked(filterKey: String) {
        val allChips = listOf(
            binding.chipAll, binding.chipTopHits, binding.chipTopMiss, binding.chipPolls,
            binding.chipCSK, binding.chipMI, binding.chipDC, binding.chipGT,
            binding.chipKKR, binding.chipLSG, binding.chipRCB, binding.chipPBKS,
            binding.chipRR, binding.chipSRH
        )
        allChips.forEach { it.isChecked = false; it.chipStrokeWidth = 0f
            it.setTextColor(resources.getColor(R.color.white, null)) }

        val active = when (filterKey) {
            ChatFilterKey.ALL        -> binding.chipAll
            ChatFilterKey.TOP_HIT   -> binding.chipTopHits
            ChatFilterKey.TOP_MISS  -> binding.chipTopMiss
            ChatFilterKey.POLLS_ONLY -> binding.chipPolls
            ChatFilterKey.team("CSK")  -> binding.chipCSK
            ChatFilterKey.team("MI")   -> binding.chipMI
            ChatFilterKey.team("DC")   -> binding.chipDC
            ChatFilterKey.team("GT")   -> binding.chipGT
            ChatFilterKey.team("KKR")  -> binding.chipKKR
            ChatFilterKey.team("LSG")  -> binding.chipLSG
            ChatFilterKey.team("RCB")  -> binding.chipRCB
            ChatFilterKey.team("PBKS") -> binding.chipPBKS
            ChatFilterKey.team("RR")   -> binding.chipRR
            ChatFilterKey.team("SRH")  -> binding.chipSRH
            else -> binding.chipAll
        }
        active.isChecked = true; active.chipStrokeWidth = 2f
        active.setTextColor(resources.getColor(R.color.grey, null))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Buttons
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.buttonSend.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to send messages"); return@setOnClickListener }
            val text = binding.editTextMessage.text.toString().trim()
            if (text.isNotEmpty()) checkAndSendMessage(text)
        }

        binding.buttonMeme.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to share images"); return@setOnClickListener }
            imagePicker.launch("image/*")
        }

        binding.buttonPoll.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to create polls"); return@setOnClickListener }
            showCreatePollDialog()
        }

        binding.leaderBoardIcon.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.leaderboardIcon
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, LeaderboardFragment()).addToBackStack(null).commit()
        }

        binding.profilePhoto.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to view your profile"); return@setOnClickListener }
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.profileIcon
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, ProfileFragment()).addToBackStack(null).commit()
        }

        // New message banner
        binding.btnNewMessages.setOnClickListener {
            binding.recyclerViewMessages.scrollToPosition(0)
            hideNewMessageBanner()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // New message banner
    // ─────────────────────────────────────────────────────────────────────────

    private fun showNewMessageBanner() {
        if (_binding == null || !initialLoadDone) return
        newMsgCount++
        binding.btnNewMessages.text = if (newMsgCount == 1) "1 new message ↓" else "$newMsgCount new messages ↓"
        binding.btnNewMessages.visibility = View.VISIBLE
    }

    private fun hideNewMessageBanner() {
        newMsgCount = 0
        _binding?.btnNewMessages?.visibility = View.GONE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Live score strip
    // ─────────────────────────────────────────────────────────────────────────

    private val activeStatuses = setOf(
        "Live", "Delayed", "Innings Break", "Lunch", "Tea", "Rain", "1st Innings", "2nd Innings"
    )

    private fun setupLiveScoreStrip() {
        liveStripAdapter = LiveScoreStripAdapter(liveStripMatches) { match ->
            val args = Bundle().apply {
                putString("ROOM_ID", match.matchName); putString("ROOM_TYPE", "LIVE")
                putString("ROOM_NAME", match.matchName)
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, ChatFragment().apply { arguments = args })
                .addToBackStack(null).commit()
        }
        binding.liveScoreStripRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = liveStripAdapter
            isNestedScrollingEnabled = false
        }
        fetchLiveScoreStrip()
    }

    private fun fetchLiveScoreStrip() {
        val ref = FirebaseDatabase.getInstance().getReference("NoBallZone/liveRooms")
        stripListener?.let { ref.removeEventListener(it) }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                val temp = mutableListOf<MatchData>()
                for (room in snapshot.children) {
                    val matchName = room.key ?: continue
                    val scores = room.child("scores")
                    if (!scores.exists()) continue
                    val isLive = scores.child("live").getValue(Boolean::class.java) ?: false
                    val status = scores.child("status").getValue(String::class.java) ?: ""
                    if (!isLive && status !in activeStatuses) continue
                    temp.add(buildMatchData(matchName, scores))
                }
                liveStripAdapter.updateMatches(temp)
                binding.liveScoreStripContainer.visibility = if (temp.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun onCancelled(error: DatabaseError) {
                _binding?.liveScoreStripContainer?.visibility = View.GONE
            }
        }
        ref.addValueEventListener(listener)
        stripListener = listener
    }

    private fun buildMatchData(matchName: String, scores: DataSnapshot) = MatchData(
        matchId    = scores.child("matchId").getValue(Any::class.java)?.toString() ?: "",
        matchName  = matchName,
        status     = scores.child("status").getValue(String::class.java) ?: "",
        note       = scores.child("note").getValue(String::class.java) ?: "",
        live       = scores.child("live").getValue(Boolean::class.java) ?: false,
        type       = scores.child("type").getValue(String::class.java) ?: "",
        round      = scores.child("round").getValue(String::class.java) ?: "",
        startingAt = scores.child("startingAt").getValue(String::class.java) ?: "",
        updatedAt  = scores.child("updatedAt").getValue(String::class.java) ?: "",
        league     = LeagueData(
            id        = scores.child("league/id").getValue(Any::class.java)?.toString() ?: "",
            name      = scores.child("league/name").getValue(String::class.java) ?: "",
            imagePath = scores.child("league/imagePath").getValue(String::class.java) ?: ""
        ),
        stage = StageData(
            id   = scores.child("stage/id").getValue(Any::class.java)?.toString() ?: "",
            name = scores.child("stage/name").getValue(String::class.java) ?: ""
        ),
        localteam = TeamData(
            id        = scores.child("localteam/id").getValue(Any::class.java)?.toString() ?: "",
            name      = scores.child("localteam/name").getValue(String::class.java) ?: "Team 1",
            code      = scores.child("localteam/code").getValue(String::class.java) ?: "",
            imagePath = scores.child("localteam/imagePath").getValue(String::class.java) ?: ""
        ),
        visitorteam = TeamData(
            id        = scores.child("visitorteam/id").getValue(Any::class.java)?.toString() ?: "",
            name      = scores.child("visitorteam/name").getValue(String::class.java) ?: "Team 2",
            code      = scores.child("visitorteam/code").getValue(String::class.java) ?: "",
            imagePath = scores.child("visitorteam/imagePath").getValue(String::class.java) ?: ""
        ),
        localteamScore = ScoreData(
            runs    = scores.child("localteamScore/runs").getValue(Int::class.java) ?: 0,
            wickets = scores.child("localteamScore/wickets").getValue(Int::class.java) ?: 0,
            overs   = scores.child("localteamScore/overs").getValue(Double::class.java) ?: 0.0
        ),
        visitorteamScore = ScoreData(
            runs    = scores.child("visitorteamScore/runs").getValue(Int::class.java) ?: 0,
            wickets = scores.child("visitorteamScore/wickets").getValue(Int::class.java) ?: 0,
            overs   = scores.child("visitorteamScore/overs").getValue(Double::class.java) ?: 0.0
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Auth helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun isUserLoggedIn() = FirebaseAuth.getInstance().currentUser != null

    private fun updateUIBasedOnLoginStatus() {
        val in_ = isUserLoggedIn()
        binding.editTextMessage.hint = if (in_) "Type a message" else "Login to send messages"
        binding.buttonSend.alpha  = if (in_) 1f else 0.5f
        binding.buttonMeme.alpha  = if (in_) 1f else 0.5f
        binding.buttonPoll.alpha  = if (in_) 1f else 0.5f
    }

    private fun showLoginPrompt(message: String) {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Login Required").setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                loginLauncher.launch(Intent(requireContext(), SignIn::class.java))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun loadProfilePhoto() {
        if (!isAdded || _binding == null) return
        val uid = currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("Users/$uid/profilePhoto")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || _binding == null) return
                    val url = snapshot.getValue(String::class.java)
                    if (!url.isNullOrEmpty()) {
                        context?.let { Glide.with(it).load(url).placeholder(R.drawable.profile_icon)
                            .into(_binding?.profilePhoto ?: return) }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchUserTeam() {
        currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance().getReference("Users/$uid/iplTeam")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        userTeam = s.getValue(String::class.java) ?: "No Team"
                    }
                    override fun onCancelled(e: DatabaseError) {}
                })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send message
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkAndSendMessage(text: String) {
        binding.buttonSend.isEnabled = false
        binding.progressSending.visibility = View.VISIBLE
        moderationService.checkMessageContent(text, object : ChatModerationService.ModerationCallback {
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
            override fun onError(e: String) {
                requireActivity().runOnUiThread {
                    binding.buttonSend.isEnabled = true
                    binding.progressSending.visibility = View.GONE
                }
            }
        })
    }

    private fun sendMessageToFirebase(text: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ref  = FirebaseDatabase.getInstance().getReference("$roomBasePath/chats").push()
        val id   = ref.key ?: return
        FirebaseDatabase.getInstance().getReference("Users/${user.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val name = snap.child("username").getValue(String::class.java)
                        ?: user.displayName ?: "Anonymous"
                    val msg = ChatMessage(
                        id = id, senderId = user.uid, senderName = name,
                        team = userTeam, message = text, timestamp = System.currentTimeMillis()
                    )
                    ref.setValue(msg).addOnSuccessListener {
                        binding.editTextMessage.text.clear()
                        viewModel.onMessagePosted(msg)
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image send
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkImageSafetyAndShowPreview() {
        if (selectedImageUri == null) return
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView).setCancelable(false).setTitle("Checking content safety…").create()
        dialog.show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = safetyChecker.checkImageSafety(selectedImageUri!!)
                dialog.dismiss()
                when {
                    result.isSafe    -> showImagePreviewDialog()
                    result.autoBlock -> showContentBlockedDialog(result.issues)
                    else             -> showContentWarningDialog(result.issues)
                }
            } catch (e: Exception) { dialog.dismiss() }
        }
    }

    private fun showImagePreviewDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_image_preview, null)
        Glide.with(requireContext()).load(selectedImageUri).into(v.findViewById(R.id.imagePreview))
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertForImagePreview)
            .setTitle("Send Image").setView(v)
            .setPositiveButton("Send") { _, _ -> uploadAndSendImage() }
            .setNegativeButton("Cancel") { _, _ -> selectedImageUri = null }.show()
    }

    private fun showContentBlockedDialog(issues: List<String>) {
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Content Blocked")
            .setMessage("Image blocked:\n${issues.take(3).joinToString("\n")}")
            .setPositiveButton("OK") { d, _ -> d.dismiss(); selectedImageUri = null }.show()
    }

    private fun showContentWarningDialog(issues: List<String>) {
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Content Warning")
            .setMessage("Image may contain inappropriate content:\n${issues.take(3).joinToString("\n")}")
            .setPositiveButton("Upload Anyway") { d, _ -> d.dismiss(); showImagePreviewDialog() }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss(); selectedImageUri = null }.show()
    }

    private fun uploadAndSendImage() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uri  = selectedImageUri ?: return
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView).setCancelable(false).setTitle("Uploading image…").create()
        dialog.show()
        val ref = FirebaseStorage.getInstance().reference
            .child("chat_images/${System.currentTimeMillis()}_${user.uid}.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                dialog.dismiss()
                sendImageMessageToFirebase("", url.toString())
                selectedImageUri = null
            }
        }.addOnFailureListener { dialog.dismiss() }
    }

    private fun sendImageMessageToFirebase(message: String, imageUrl: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ref  = FirebaseDatabase.getInstance().getReference("$roomBasePath/chats").push()
        val id   = ref.key ?: return
        FirebaseDatabase.getInstance().getReference("Users/${user.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val name = snap.child("username").getValue(String::class.java)
                        ?: user.displayName ?: "Anonymous"
                    val team = snap.child("iplTeam").getValue(String::class.java) ?: "No Team"
                    val msg = ChatMessage(
                        id = id, senderId = user.uid, senderName = name,
                        team = team, message = message, imageUrl = imageUrl,
                        timestamp = System.currentTimeMillis()
                    )
                    ref.setValue(msg).addOnSuccessListener {
                        binding.editTextMessage.text.clear()
                        viewModel.onMessagePosted(msg)
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Poll creation
    // ─────────────────────────────────────────────────────────────────────────

    private fun showCreatePollDialog() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val v = LayoutInflater.from(context).inflate(R.layout.poll_create_dialog, null)
        val etQ  = v.findViewById<EditText>(R.id.editTextPollQuestion)
        val llOp = v.findViewById<LinearLayout>(R.id.layoutOptions)
        val llAd = v.findViewById<LinearLayout>(R.id.layoutAdditionalOptions)
        val optViews = mutableListOf<View>()
        repeat(2) {
            val ov = LayoutInflater.from(context).inflate(R.layout.item_creation_poll_option, null)
            llOp.addView(ov); optViews.add(ov)
        }
        v.findViewById<android.widget.Button>(R.id.buttonAddOption).setOnClickListener {
            if (optViews.size < 6) {
                val ov = LayoutInflater.from(context).inflate(R.layout.item_creation_poll_option, null)
                llAd.addView(ov); optViews.add(ov)
            } else Toast.makeText(context, "Maximum 6 options", Toast.LENGTH_SHORT).show()
        }
        AlertDialog.Builder(requireContext(), R.style.CustomAlertForImagePreview)
            .setTitle("Create Poll").setView(v)
            .setPositiveButton("Create") { _, _ -> createPoll(etQ.text.toString(), optViews) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun createPoll(question: String, optViews: List<View>) {
        if (question.trim().isEmpty()) return
        val options = mutableMapOf<String, Int>()
        for (v in optViews) {
            val text = v.findViewById<EditText>(R.id.editTextOption)?.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) options[text] = 0
        }
        if (options.size < 2) { Toast.makeText(context, "Please provide at least two options", Toast.LENGTH_SHORT).show(); return }

        moderationService.checkMessageContent(question, object : ChatModerationService.ModerationCallback {
            override fun onMessageApproved(msg: String) { createPollInFirebase(msg, options) }
            override fun onMessageRejected(msg: String, reason: String) {
                requireActivity().runOnUiThread { Toast.makeText(context, reason, Toast.LENGTH_LONG).show() }
            }
            override fun onError(e: String) { createPollInFirebase(question, options) }
        })
    }

    private fun createPollInFirebase(question: String, options: MutableMap<String, Int>) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ref  = FirebaseDatabase.getInstance().getReference("$roomBasePath/polls").push()
        val id   = ref.key ?: return
        FirebaseDatabase.getInstance().getReference("Users/${user.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val name = snap.child("username").getValue(String::class.java)
                        ?: user.displayName ?: "Anonymous"
                    val team = snap.child("iplTeam").getValue(String::class.java) ?: "No Team"
                    val poll = PollMessage(
                        id = id, senderId = user.uid, senderName = name, team = team,
                        question = question, options = options, timestamp = System.currentTimeMillis(),
                        voters = mutableMapOf()
                    )
                    ref.setValue(poll).addOnSuccessListener {
                        Toast.makeText(context, "Poll created successfully", Toast.LENGTH_SHORT).show()
                        viewModel.onMessagePosted(poll)
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Highlight helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun scrollToAndHighlight(messageId: String) {
        if (!isAdded || _binding == null) return
        val pos = adapter.findPositionById(messageId)
        if (pos == -1) return
        binding.recyclerViewMessages.scrollToPosition(pos)
        val ref = WeakReference(this)
        Handler(Looper.getMainLooper()).postDelayed({
            val f = ref.get() ?: return@postDelayed
            if (!f.isAdded || f._binding == null) return@postDelayed
            f.binding.recyclerViewMessages.findViewHolderForAdapterPosition(pos)
                ?.itemView?.let { f.applyHighlightAnimation(it) }
        }, 100)
    }

    private fun applyHighlightAnimation(view: View) {
        val bg = view.background
        view.setBackgroundResource(R.drawable.highlighted_item_background)
        Handler(Looper.getMainLooper()).postDelayed({ view.background = bg }, 1500)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        updateUIBasedOnLoginStatus()
        if (isUserLoggedIn() && currentUser != FirebaseAuth.getInstance().currentUser) {
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto(); fetchUserTeam()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stripListener?.let {
            FirebaseDatabase.getInstance().getReference("NoBallZone/liveRooms").removeEventListener(it)
        }
        binding.recyclerViewMessages.adapter = null
        _binding = null
    }
}