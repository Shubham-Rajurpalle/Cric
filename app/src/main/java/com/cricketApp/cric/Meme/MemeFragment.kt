package com.cricketApp.cric.Meme

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.ChatFragment
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentMemeBinding
import com.cricketApp.cric.home.liveMatch.*
import com.cricketApp.cric.LiveScoreStripAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MemeFragment : Fragment() {

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private val viewModel: MemeViewModel by viewModels(
        factoryProducer = { MemeViewModel.Factory(requireActivity().application) }
    )

    // ── Binding ───────────────────────────────────────────────────────────────
    private var _binding: FragmentMemeBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding not available")

    // ── Adapter ───────────────────────────────────────────────────────────────
    private lateinit var memeAdapter: MemeAdapter

    // ── Upload ────────────────────────────────────────────────────────────────
    private var selectedImageUri: Uri? = null
    private lateinit var safetyChecker: CloudVisionSafetyChecker

    // ── Auth ──────────────────────────────────────────────────────────────────
    private var currentUser = FirebaseAuth.getInstance().currentUser

    // ── Live score strip ──────────────────────────────────────────────────────
    private val liveStripMatches = mutableListOf<MatchData>()
    private lateinit var liveStripAdapter: LiveScoreStripAdapter
    private var stripListener: ValueEventListener? = null

    // ── Deep-link highlight ───────────────────────────────────────────────────
    private var highlightMemeId: String? = null

    private var newMemeCount=0
    private var initialLoadDone = false

    // ── Activity result launchers ─────────────────────────────────────────────
    private val memePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri = it; showMemePreviewDialog() }
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto()
            fetchUserTeam()
            updateUIBasedOnLoginStatus()
            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        highlightMemeId = arguments?.getString("HIGHLIGHT_MESSAGE_ID")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        safetyChecker = CloudVisionSafetyChecker(requireContext())

        setupAdapter()
        setupRecyclerView()
        setupFilters()
        setupLiveScoreStrip()
        setupButtons()
        observeViewModel()

        updateUIBasedOnLoginStatus()

        if (isUserLoggedIn()) {
            loadProfilePhoto()
            fetchUserTeam()
        } else {
            binding.profilePhoto.setImageResource(R.drawable.profile_icon)
        }

        // Handle deep-link highlight
        highlightMemeId?.takeIf { it.isNotEmpty() }?.let { id ->
            Handler(Looper.getMainLooper()).postDelayed({
                scrollToAndHighlightMeme(id)
            }, 600)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter + RecyclerView
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupAdapter() {
        memeAdapter = MemeAdapter(
            onCommentClick = { meme -> openMemeComments(meme) },
            onHitMissUpdated = { memeId, hit, miss ->
                viewModel.onHitMissUpdated(memeId, hit, miss)
            }
        )
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(context)

        binding.recyclerViewMemes.apply {
            this.layoutManager = layoutManager
            adapter = memeAdapter
            clipToPadding = false
            setPadding(
                paddingLeft, paddingTop, paddingRight,
                resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
            )

            // ── Pagination scroll listener ────────────────────────────────────
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return  // only trigger on downward scroll
                    val totalItems    = layoutManager.itemCount
                    val lastVisible   = layoutManager.findLastVisibleItemPosition()
                    val threshold     = 4  // load next page when 4 items from bottom
                    if (lastVisible >= totalItems - threshold) {
                        viewModel.loadNextPage()
                    }
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

                // ── Meme list (from Room) ─────────────────────────────────────
                launch {
                    viewModel.memes.collect { entities ->
                        val messages = entities.map { it.toMemeMessage() }
                        memeAdapter.submitList(messages)
                    }
                }

                // ── Pagination state (loading footer / empty state) ───────────
                launch {
                    viewModel.paginationState.collect { state ->
                        if (_binding == null) return@collect

                        if (state.isLoading && memeAdapter.currentList.isEmpty()) {
                            binding.llAnime2.visibility = View.VISIBLE
                            binding.recyclerViewMemes.visibility = View.GONE
                        } else {
                            binding.llAnime2.visibility = View.GONE
                            binding.recyclerViewMemes.visibility = View.VISIBLE
                            if (!state.isLoading && memeAdapter.currentList.isNotEmpty()) {
                                initialLoadDone = true  // ← SET HERE
                            }
                        }

                        memeAdapter.showLoadingFooter =
                            state.isLoading && memeAdapter.currentList.isNotEmpty()

                        state.error?.let {
                            Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // ── New real-time meme → show "new memes" banner ─────────────
                launch {
                    viewModel.newMemeEvent.collect {
                        showNewMemeBanner()
                    }
                }

                // ── Meme removed — Room Flow auto-updates list ────────────────
                launch {
                    viewModel.removedMemeId.collect { /* list auto-updates via Room */ }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter chips
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupFilters() {
        binding.chipAll.setOnClickListener    { applyFilter(FilterKey.ALL) }
        binding.chipTopHits.setOnClickListener { applyFilter(FilterKey.TOP_HIT) }
        binding.chipTopMiss.setOnClickListener { applyFilter(FilterKey.TOP_MISS) }
        binding.chipCSK.setOnClickListener    { applyFilter(FilterKey.team("CSK")) }
        binding.chipMI.setOnClickListener     { applyFilter(FilterKey.team("MI"))  }
        binding.chipDC.setOnClickListener     { applyFilter(FilterKey.team("DC"))  }
        binding.chipGT.setOnClickListener     { applyFilter(FilterKey.team("GT"))  }
        binding.chipKKR.setOnClickListener    { applyFilter(FilterKey.team("KKR")) }
        binding.chipLSG.setOnClickListener    { applyFilter(FilterKey.team("LSG")) }
        binding.chipRCB.setOnClickListener    { applyFilter(FilterKey.team("RCB")) }
        binding.chipPBKS.setOnClickListener   { applyFilter(FilterKey.team("PBKS"))}
        binding.chipRR.setOnClickListener     { applyFilter(FilterKey.team("RR"))  }
        binding.chipSRH.setOnClickListener    { applyFilter(FilterKey.team("SRH")) }

        setChipChecked(FilterKey.ALL)  // default
    }

    private fun applyFilter(filterKey: String) {
        // If tapping the already-active filter, reset to ALL
        val effective = if (viewModel.activeFilter.value == filterKey && filterKey != FilterKey.ALL)
            FilterKey.ALL else filterKey

        setChipChecked(effective)
        initialLoadDone = false
        hideNewMemeBanner()   // reset banner when switching filters
        viewModel.applyFilter(effective)
    }

    private fun setChipChecked(filterKey: String) {
        // Reset all chips
        val allChips = listOf(
            binding.chipAll, binding.chipTopHits, binding.chipTopMiss,
            binding.chipCSK, binding.chipMI, binding.chipDC, binding.chipGT,
            binding.chipKKR, binding.chipLSG, binding.chipRCB, binding.chipPBKS,
            binding.chipRR, binding.chipSRH
        )
        allChips.forEach { chip ->
            chip.isChecked     = false
            chip.chipStrokeWidth = 0f
            chip.setTextColor(resources.getColor(R.color.white, null))
        }

        // Mark the active chip
        val activeChip = when (filterKey) {
            FilterKey.ALL      -> binding.chipAll
            FilterKey.TOP_HIT  -> binding.chipTopHits
            FilterKey.TOP_MISS -> binding.chipTopMiss
            FilterKey.team("CSK")  -> binding.chipCSK
            FilterKey.team("MI")   -> binding.chipMI
            FilterKey.team("DC")   -> binding.chipDC
            FilterKey.team("GT")   -> binding.chipGT
            FilterKey.team("KKR")  -> binding.chipKKR
            FilterKey.team("LSG")  -> binding.chipLSG
            FilterKey.team("RCB")  -> binding.chipRCB
            FilterKey.team("PBKS") -> binding.chipPBKS
            FilterKey.team("RR")   -> binding.chipRR
            FilterKey.team("SRH")  -> binding.chipSRH
            else -> binding.chipAll
        }
        activeChip.isChecked      = true
        activeChip.chipStrokeWidth = 2f
        activeChip.setTextColor(resources.getColor(R.color.grey, null))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Buttons
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.buttonUploadMeme.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to upload memes"); return@setOnClickListener }
            memePicker.launch("image/*")
        }

        // New memes banner — tap to scroll to top and hide
        binding.btnNewMemes.setOnClickListener {
            binding.recyclerViewMemes.scrollToPosition(0)
            hideNewMemeBanner()
        }

        binding.leaderBoardIcon.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.leaderboardIcon
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, LeaderboardFragment())
                .addToBackStack(null).commit()
        }

        binding.profilePhoto.setOnClickListener {
            if (!isUserLoggedIn()) { showLoginPrompt("Login to view your profile"); return@setOnClickListener }
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.profileIcon
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, ProfileFragment())
                .addToBackStack(null).commit()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Live score strip (unchanged from original)
    // ─────────────────────────────────────────────────────────────────────────

    private val activeStatuses = setOf(
        "Live", "Delayed", "Innings Break",
        "Lunch", "Tea", "Rain", "1st Innings", "2nd Innings"
    )

    private fun setupLiveScoreStrip() {
        liveStripAdapter = LiveScoreStripAdapter(liveStripMatches) { match ->
            val args = Bundle().apply {
                putString("ROOM_ID",   match.matchName)
                putString("ROOM_TYPE", "LIVE")
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
                    val scores   = room.child("scores")
                    if (!scores.exists()) continue
                    val isLive = scores.child("live").getValue(Boolean::class.java) ?: false
                    val status = scores.child("status").getValue(String::class.java) ?: ""
                    if (!isLive && status !in activeStatuses) continue
                    temp.add(buildMatchData(matchName, scores))
                }
                liveStripAdapter.updateMatches(temp)
                binding.liveScoreStripContainer.visibility =
                    if (temp.isEmpty()) View.GONE else View.VISIBLE
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
        league = LeagueData(
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
        binding.buttonUploadMeme.text =
            if (isUserLoggedIn()) "Upload Meme" else "Login to Upload"
    }

    private fun showLoginPrompt(message: String) {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                loginLauncher.launch(Intent(requireContext(), SignIn::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadProfilePhoto() {
        if (!isAdded || _binding == null) return
        val userId = currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")
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
                    override fun onDataChange(s: DataSnapshot) { /* stored in viewModel/repo if needed */ }
                    override fun onCancelled(e: DatabaseError) {}
                })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Comments
    // ─────────────────────────────────────────────────────────────────────────

    private fun openMemeComments(meme: MemeMessage) {
        if (!isUserLoggedIn()) { showLoginPrompt("Login to view and add comments"); return }
        try {
            startActivity(Intent(requireContext(), com.cricketApp.cric.Chat.CommentActivity::class.java).apply {
                putExtra("MESSAGE_ID",   meme.id)
                putExtra("MESSAGE_TYPE", "meme")
            })
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open comments", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Meme upload pipeline (unchanged logic, just calls viewModel.onMemePosted)
    // ─────────────────────────────────────────────────────────────────────────

    private fun showMemePreviewDialog() {
        if (!isAdded || _binding == null) return
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView).setCancelable(false)
            .setTitle("Checking content safety…").create()
        progressDialog.show()

        val fragmentRef = WeakReference(this)
        selectedImageUri?.let { uri ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = safetyChecker.checkImageSafety(uri)
                    val fragment = fragmentRef.get()
                    if (fragment == null || !fragment.isAdded || fragment._binding == null) {
                        progressDialog.dismiss(); return@launch
                    }
                    progressDialog.dismiss()
                    when {
                        result.isSafe    -> fragment.showUploadDialog()
                        result.autoBlock -> fragment.showContentBlockedDialog(result.issues)
                        else             -> fragment.showContentWarningDialog(result.issues)
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                }
            }
        } ?: run { progressDialog.dismiss() }
    }

    private fun showContentBlockedDialog(issues: List<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Content Blocked")
            .setMessage("This image has been blocked:\n\n${issues.take(3).joinToString("\n")}")
            .setPositiveButton("OK") { d, _ -> d.dismiss(); selectedImageUri = null }
            .show()
    }

    private fun showContentWarningDialog(issues: List<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Content Warning")
            .setMessage("This image may contain inappropriate content:\n\n${issues.take(3).joinToString("\n")}")
            .setPositiveButton("Upload Anyway") { d, _ -> d.dismiss(); showUploadDialog() }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss(); selectedImageUri = null }
            .show()
    }

    private fun showUploadDialog() {
        val progressView   = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView).setCancelable(false)
            .setTitle("Uploading meme…").create()
        progressDialog.show()
        uploadAndPostMeme("") { success ->
            progressDialog.dismiss()
            if (!success) Toast.makeText(context, "Meme upload failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAndPostMeme(caption: String, onComplete: ((Boolean) -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        selectedImageUri?.let { uri ->
            val ref = FirebaseStorage.getInstance().reference
                .child("memes/${System.currentTimeMillis()}_${user.uid}.jpg")
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { url ->
                        postMeme(caption, url.toString())
                        selectedImageUri = null
                        onComplete?.invoke(true)
                    }
                }
                .addOnFailureListener { onComplete?.invoke(false) }
        }
    }

    private fun postMeme(caption: String, imageUrl: String) {
        val user    = FirebaseAuth.getInstance().currentUser ?: return
        val memeRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes").push()
        val memeId  = memeRef.key ?: return

        FirebaseDatabase.getInstance().getReference("Users/${user.uid}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userName = snapshot.child("username").getValue(String::class.java)
                        ?: user.displayName ?: "Anonymous"
                    val userTeam = snapshot.child("iplTeam").getValue(String::class.java) ?: "No Team"

                    val meme = MemeMessage(
                        id         = memeId,
                        senderId   = user.uid,
                        senderName = userName,
                        team       = userTeam,
                        memeUrl    = imageUrl,
                        timestamp  = System.currentTimeMillis()
                    )

                    memeRef.setValue(meme)
                        .addOnSuccessListener {
                            // Write index nodes so filters work server-side
                            viewModel.onMemePosted(meme)
                            Toast.makeText(context, "Meme posted successfully", Toast.LENGTH_SHORT).show()
                        }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // New meme banner
    // ─────────────────────────────────────────────────────────────────────────

    private fun showNewMemeBanner() {
        if (_binding == null) return
        if (!initialLoadDone) return  // ← ignore events during initial load
        newMemeCount++
        binding.btnNewMemes.text = "New memes ↑"
        binding.btnNewMemes.visibility = View.VISIBLE
    }

    private fun hideNewMemeBanner() {
        newMemeCount = 0
        _binding?.btnNewMemes?.visibility = View.GONE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Highlight / scroll helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun scrollToAndHighlightMeme(memeId: String) {
        if (!isAdded || _binding == null) return
        val position = memeAdapter.findPositionById(memeId)
        if (position == -1) return
        binding.recyclerViewMemes.scrollToPosition(position)
        val ref = WeakReference(this)
        Handler(Looper.getMainLooper()).postDelayed({
            val f = ref.get() ?: return@postDelayed
            if (!f.isAdded || f._binding == null) return@postDelayed
            f.binding.recyclerViewMemes.findViewHolderForAdapterPosition(position)
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
            loadProfilePhoto()
            fetchUserTeam()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stripListener?.let {
            FirebaseDatabase.getInstance()
                .getReference("NoBallZone/liveRooms").removeEventListener(it)
        }
        binding.recyclerViewMemes.adapter = null
        _binding = null
    }
}