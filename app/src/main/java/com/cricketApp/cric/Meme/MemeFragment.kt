package com.cricketApp.cric.Meme

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.ChatAdapter
import com.cricketApp.cric.Chat.ChatFragment
import com.cricketApp.cric.Chat.CommentActivity
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentMemeBinding
import com.cricketApp.cric.home.liveMatch.MatchData
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
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import com.cricketApp.cric.LiveScoreStripAdapter
import com.cricketApp.cric.home.liveMatch.LeagueData
import com.cricketApp.cric.home.liveMatch.ScoreData
import com.cricketApp.cric.home.liveMatch.StageData
import com.cricketApp.cric.home.liveMatch.TeamData

class MemeFragment : Fragment() {
    private lateinit var memeAdapter: MemeAdapter
    private val memes = mutableListOf<MemeMessage>()
    private var selectedImageUri: Uri? = null
    private var currentUser = FirebaseAuth.getInstance().currentUser
    private var userTeam: String = "None"
    private val PICK_MEME_REQUEST = 1
    private val LOGIN_REQUEST_CODE = 1001
    private lateinit var safetyChecker: CloudVisionSafetyChecker
    private var highlightMemeId: String? = null
    private var _binding: FragmentMemeBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding not available")
    private val valueEventListeners = HashMap<DatabaseReference, ValueEventListener>()
    private val childEventListeners = HashMap<DatabaseReference, ChildEventListener>()


    // Map to keep track of meme positions for efficient updates
    private val memePositions = mutableMapOf<String, Int>()

    // Selected filters
    private var selectedFilter = mutableSetOf<String>()

    private val liveStripMatches = mutableListOf<MatchData>()
    private lateinit var liveStripAdapter: LiveScoreStripAdapter

    // Register activity result launchers
    private val memePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            showMemePreviewDialog()
        }
    }

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User successfully logged in
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto()
            fetchUserTeam()
            updateUIBasedOnLoginStatus()

            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get highlight meme ID from arguments
        highlightMemeId = arguments?.getString("HIGHLIGHT_MESSAGE_ID")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemeBinding.inflate(inflater, container, false)
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

        // Initialize safety checker
        safetyChecker = CloudVisionSafetyChecker(requireContext())

        // Check if user is logged in and update UI accordingly
        updateUIBasedOnLoginStatus()

        // Load profile photo if logged in
        if (isUserLoggedIn()) {
            loadProfilePhoto()
            fetchUserTeam()
        } else {
            // Set default profile photo for non-logged in users
            binding.profilePhoto.setImageResource(R.drawable.profile_icon)
        }

        // Setup RecyclerView with custom click handler for comments
        memeAdapter = MemeAdapter(memes) { meme ->
            openMemeComments(meme)
        }

        binding.recyclerViewMemes.apply {
            val layoutManager = LinearLayoutManager(context)
            layoutManager.orientation = LinearLayoutManager.VERTICAL
            layoutManager.reverseLayout = false
            layoutManager.stackFromEnd = false
            this.layoutManager = layoutManager
            adapter = this@MemeFragment.memeAdapter

            // Add this line to make sure content at the bottom is fully visible
            clipToPadding = false
            setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp))
        }

        // Setup filters - no login required for filters
        setupFilters()
        setupLiveScoreStrip()

        // Setup Firebase listeners
        setupFirebaseListeners()

        // Setup meme upload button - requires login
        binding.buttonUploadMeme.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to upload memes")
                return@setOnClickListener
            }

            openMemePicker()
        }

        // Setup navigation buttons - Leaderboard doesn't require login
        binding.leaderBoardIcon.setOnClickListener {
            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.leaderboardIcon
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.navHost, LeaderboardFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }

        // Profile button requires login
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

        if (!highlightMemeId.isNullOrEmpty()) {
            // Wait for memes to load before trying to scroll
            Handler(Looper.getMainLooper()).postDelayed({
                scrollToAndHighlightMeme(highlightMemeId!!)
            }, 500) // Small delay to allow memes to load
        }
    }

    private fun setupLiveScoreStrip() {
        liveStripAdapter = LiveScoreStripAdapter(liveStripMatches) { match ->
            // Open the match chat room on tap — same as in Live_matches.kt
            val args = Bundle().apply {
                putString("ROOM_ID",   match.matchName)
                putString("ROOM_TYPE", "LIVE")
                putString("ROOM_NAME", match.matchName)
            }
            val chatFragment = ChatFragment().apply { arguments = args }
            parentFragmentManager.beginTransaction()
                .replace(R.id.navHost, chatFragment)
                .addToBackStack(null)
                .commit()
        }

        binding.liveScoreStripRecycler.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter       = liveStripAdapter
            isNestedScrollingEnabled = false
        }

        fetchLiveScoreStrip()
    }

// ── Firebase fetch (mirrors the filter in Live_matches.kt) ───────────

    private val activeStatuses = setOf(
        "Live", "Delayed", "Innings Break",
        "Lunch", "Tea", "Rain", "1st Innings", "2nd Innings"
    )
    private var stripListener: com.google.firebase.database.ValueEventListener? = null

    private fun fetchLiveScoreStrip() {
        val ref = FirebaseDatabase.getInstance().getReference("NoBallZone/liveRooms")
        stripListener?.let { ref.removeEventListener(it) }

        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!isAdded || _binding == null) return

                val temp = mutableListOf<MatchData>()

                for (roomSnapshot in snapshot.children) {
                    val matchName = roomSnapshot.key ?: continue
                    val scores    = roomSnapshot.child("scores")
                    if (!scores.exists()) continue

                    val isLive = scores.child("live").getValue(Boolean::class.java) ?: false
                    val status = scores.child("status").getValue(String::class.java) ?: ""
                    if (!isLive && status !in activeStatuses) continue

                    temp.add(
                        MatchData(
                            matchId    = scores.child("matchId").getValue(Any::class.java)?.toString() ?: "",
                            matchName  = matchName,
                            status     = status,
                            note       = scores.child("note").getValue(String::class.java) ?: "",
                            live       = isLive,
                            type       = scores.child("type").getValue(String::class.java) ?: "",
                            round      = scores.child("round").getValue(String::class.java) ?: "",
                            startingAt = scores.child("startingAt").getValue(String::class.java) ?: "",
                            updatedAt  = scores.child("updatedAt").getValue(String::class.java) ?: "",
                            league = LeagueData(
                                id = scores.child("league/id").getValue(Any::class.java)?.toString()
                                    ?: "",
                                name = scores.child("league/name").getValue(String::class.java)
                                    ?: "",
                                imagePath = scores.child("league/imagePath")
                                    .getValue(String::class.java) ?: ""
                            ),
                            stage = StageData(
                                id = scores.child("stage/id").getValue(Any::class.java)?.toString()
                                    ?: "",
                                name = scores.child("stage/name").getValue(String::class.java) ?: ""
                            ),
                            localteam = TeamData(
                                id = scores.child("localteam/id").getValue(Any::class.java)
                                    ?.toString() ?: "",
                                name = scores.child("localteam/name").getValue(String::class.java)
                                    ?: "Team 1",
                                code = scores.child("localteam/code").getValue(String::class.java)
                                    ?: "",
                                imagePath = scores.child("localteam/imagePath")
                                    .getValue(String::class.java) ?: ""
                            ),
                            visitorteam = TeamData(
                                id        = scores.child("visitorteam/id").getValue(Any::class.java)?.toString() ?: "",
                                name      = scores.child("visitorteam/name").getValue(String::class.java) ?: "Team 2",
                                code      = scores.child("visitorteam/code").getValue(String::class.java) ?: "",
                                imagePath = scores.child("visitorteam/imagePath").getValue(String::class.java) ?: ""
                            ),
                            localteamScore = ScoreData(
                                runs = scores.child("localteamScore/runs").getValue(Int::class.java)
                                    ?: 0,
                                wickets = scores.child("localteamScore/wickets")
                                    .getValue(Int::class.java) ?: 0,
                                overs = scores.child("localteamScore/overs")
                                    .getValue(Double::class.java) ?: 0.0
                            ),
                            visitorteamScore = ScoreData(
                                runs    = scores.child("visitorteamScore/runs").getValue(Int::class.java) ?: 0,
                                wickets = scores.child("visitorteamScore/wickets").getValue(Int::class.java) ?: 0,
                                overs   = scores.child("visitorteamScore/overs").getValue(Double::class.java) ?: 0.0
                            )
                        )
                    )
                }

                liveStripAdapter.updateMatches(temp)

                // Show/hide the whole container based on whether there are live matches
                binding.liveScoreStripContainer.visibility =
                    if (temp.isEmpty()) View.GONE else View.VISIBLE
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                binding.liveScoreStripContainer.visibility = View.GONE
            }
        }

        ref.addValueEventListener(listener)
        stripListener = listener
    }

    private fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    private fun scrollToAndHighlightMeme(memeId: String) {
        if (!isAdded || _binding == null) return

        // Find the position of the meme in the adapter
        val position = memeAdapter.findPositionById(memeId)

        if (position != -1) {
            // Scroll to the position
            binding.recyclerViewMemes.scrollToPosition(position)

            // Create weak reference to the fragment
            val fragmentRef = WeakReference(this)

            // Get the item view and apply highlight animation
            Handler(Looper.getMainLooper()).postDelayed({
                val fragment = fragmentRef.get() ?: return@postDelayed
                if (!fragment.isAdded || fragment._binding == null) return@postDelayed

                val viewHolder = fragment.binding.recyclerViewMemes.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.let { view ->
                    fragment.applyHighlightAnimation(view)
                }
            }, 100) // Small delay to ensure view is available
        }
    }
    /**
     * Apply a highlight animation to the given view
     */
    private fun applyHighlightAnimation(view: View) {
        // Create a flash animation effect
        val originalBackground = view.background
        view.setBackgroundResource(R.drawable.highlighted_item_background)

        // Reset background after animation
        Handler(Looper.getMainLooper()).postDelayed({
            view.background = originalBackground
        }, 1500) // 1.5 seconds highlight
    }

    private fun showLoginPrompt(message: String) {
        AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(requireContext(), SignIn::class.java)
                // Use the activity result launcher instead of deprecated startActivityForResult
                loginLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUIBasedOnLoginStatus() {
        val isLoggedIn = isUserLoggedIn()

        // Update upload button visibility or text based on login status
        if (!isLoggedIn) {
            // Use direct string instead of resource if resource doesn't exist
            binding.buttonUploadMeme.text = "Login to Upload"
        } else {
            binding.buttonUploadMeme.text = "Upload Meme"
        }
    }

    private fun loadProfilePhoto() {
        if (!isAdded || _binding == null) return // Safety check

        val userId = currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Importantly, check if binding is still available
                if (!isAdded || _binding == null) return

                val photoUrl = snapshot.getValue(String::class.java)
                if (!photoUrl.isNullOrEmpty()) {
                    try {
                        context?.let { ctx ->
                            Glide.with(ctx)
                                .load(photoUrl)
                                .placeholder(R.drawable.profile_icon)
                                .into(_binding?.profilePhoto ?: return)
                        }
                    } catch (e: Exception) {
                        // Handle Glide exceptions
                    //    Log.e("MemeFragment", "Error loading profile image", e)
                    }
                } else {
                //    Log.e("Profile", "No profile photo found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("MemeFragment", "Error loading profile photo", error.toException())
            }
        }

        // Store the listener for cleanup
        userRef.addListenerForSingleValueEvent(listener)
        valueEventListeners[userRef] = listener
    }

    private fun fetchUserTeam() {
        currentUser?.uid?.let { userId ->
            val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/iplTeam")
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

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { resetFilters() }
        binding.chipTopHits.setOnClickListener { toggleFilter("TopHits") { loadTopHitMemes() } }
        binding.chipTopMiss.setOnClickListener { toggleFilter("TopMiss") { loadTopMissMemes() } }
        binding.chipCSK.setOnClickListener { toggleFilter("CSK") { loadTeamMemes("CSK") } }
        binding.chipMI.setOnClickListener { toggleFilter("MI") { loadTeamMemes("MI") } }
        binding.chipDC.setOnClickListener { toggleFilter("DC") { loadTeamMemes("DC") } }
        binding.chipGT.setOnClickListener { toggleFilter("GT") { loadTeamMemes("GT") } }
        binding.chipKKR.setOnClickListener { toggleFilter("KKR") { loadTeamMemes("KKR") } }
        binding.chipLSG.setOnClickListener { toggleFilter("LSG") { loadTeamMemes("LSG") } }
        binding.chipRCB.setOnClickListener { toggleFilter("RCB") { loadTeamMemes("RCB") } }
        binding.chipPBKS.setOnClickListener { toggleFilter("PBKS") { loadTeamMemes("PBKS") } }
        binding.chipRR.setOnClickListener { toggleFilter("RR") { loadTeamMemes("RR") } }
        binding.chipSRH.setOnClickListener { toggleFilter("SRH") { loadTeamMemes("SRH") } }

        binding.chipAll.isChecked = true
    }

    private fun toggleFilter(filter: String, action: () -> Unit) {
        val chip = when (filter) {
            "TopHits" -> binding.chipTopHits
            "TopMiss" -> binding.chipTopMiss
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
            binding.chipTopHits, binding.chipTopMiss,
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
            resetFilters()
        } else {
            action.invoke()
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
            binding.chipTopHits, binding.chipTopMiss,
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

        // Clear existing memes and adapter state
        memes.clear()
        memePositions.clear()
        memeAdapter.notifyDataSetChanged()

        // Load all memes without filtering
        loadMemes()
    }

    private fun setupFirebaseListeners() {
        // We'll use different listeners based on filter status
        loadMemes()
    }

    private fun loadMemes() {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Setup child event listener for real-time updates
        setupMemeListener(memesRef)

        // Initial load
        loadInitialMemes()
    }

    // In MemeFragment.kt, modify the loadInitialMemes() method:

    private fun loadInitialMemes() {
        // Only proceed if the fragment is attached and binding is available
        if (!isAdded) return

        // Show progress before loading
        _binding?.llAnime2?.visibility = View.VISIBLE
        _binding?.recyclerViewMemes?.visibility = View.GONE

        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        memesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if fragment is still attached and binding exists
                if (!isAdded || _binding == null) return

                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by timestamp (newest first)
                tempMemes.sortByDescending { it.timestamp }

                // Make sure our lists are clear
                memes.clear()
                memePositions.clear()

                // Add all memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Hide progress after loading
                _binding?.llAnime2?.visibility = View.GONE
                _binding?.recyclerViewMemes?.visibility = View.VISIBLE

                // Notify adapter of changes
                memeAdapter.notifyDataSetChanged()

                // Add more robust scrolling behavior with multiple attempts
                if (memes.isNotEmpty() && _binding != null) {
                    // First immediate scroll
                    _binding?.recyclerViewMemes?.scrollToPosition(0)

                    // Second attempt after layout
                    _binding?.recyclerViewMemes?.post {
                        // Check again before posting
                        if (_binding != null) {
                            _binding?.recyclerViewMemes?.scrollToPosition(0)
                        }
                    }

                    // Third attempt with delay for safety
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Check again if fragment is still attached
                        if (isAdded && _binding != null) {
                            _binding?.recyclerViewMemes?.scrollToPosition(0)
                        }
                    }, 200)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Check if fragment is still attached and binding exists
                if (!isAdded || _binding == null) return

                // Hide progress on error
                _binding?.llAnime2?.visibility = View.GONE
                _binding?.recyclerViewMemes?.visibility = View.VISIBLE

            //    Log.e("MemeFragment", "Error loading initial memes", error.toException())
            }
        })
    }

    private fun setupMemeListener(memesRef: DatabaseReference) {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isAdded || _binding == null) return

                // Skip if already in our list
                val memeId = snapshot.key ?: return
                if (memePositions.containsKey(memeId)) return

                // Use helper method to properly read the meme
                val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(snapshot) ?: return

                // Add to the list (at the beginning for newest first)
                memes.add(0, meme)

                // Update positions map
                updatePositionsMap()

                memeAdapter.notifyItemInserted(0)
                binding.recyclerViewMemes.scrollToPosition(0)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isAdded || _binding == null) return

                val memeId = snapshot.key ?: return
                val position = memePositions[memeId] ?: return

                // Use helper method to properly read the updated meme
                val updatedMeme = FirebaseDataHelper.getMemeMessageFromSnapshot(snapshot) ?: return

                val currentMeme = memes[position]

                // Check what changed and update accordingly
                // Update reactions if they changed
                if (currentMeme.reactions != updatedMeme.reactions) {
                    currentMeme.reactions = updatedMeme.reactions
                    memeAdapter.notifyItemChanged(position, "reaction")
                }

                // Update hit/miss if they changed
                if (currentMeme.hit != updatedMeme.hit || currentMeme.miss != updatedMeme.miss) {
                    currentMeme.hit = updatedMeme.hit
                    currentMeme.miss = updatedMeme.miss
                    memeAdapter.notifyItemChanged(position, "hit_miss")
                }

                // Check for comment count change
                if (snapshot.hasChild("commentCount")) {
                    val commentCount = snapshot.child("commentCount").getValue(Int::class.java) ?: 0
                    if (currentMeme.commentCount != commentCount) {
                        currentMeme.commentCount = commentCount
                        memeAdapter.notifyItemChanged(position, "comments")
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                val memeId = snapshot.key ?: return
                val position = memePositions[memeId] ?: return

                memes.removeAt(position)
                memePositions.remove(memeId)

                // Update positions map
                updatePositionsMap()

                memeAdapter.notifyItemRemoved(position)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("MemeFragment", "Error with meme listener", error.toException())
            }
        }

        // Add and track the listener
        memesRef.addChildEventListener(listener)
        childEventListeners[memesRef] = listener
    }

    private fun updatePositionsMap() {
        // Update the positions map to reflect current positions in the list
        memePositions.clear()
        memes.forEachIndexed { index, meme ->
            memePositions[meme.id] = index
        }
    }

    private fun loadTopHitMemes() {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Load memes with high hits
        memesRef.orderByChild("hit").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by hit count (descending) and timestamp
                tempMemes.sortWith(compareByDescending<MemeMessage> { it.hit }
                    .thenByDescending { it.timestamp })

                // Clear existing memes
                memes.clear()
                memePositions.clear()

                // Add sorted memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Notify adapter
                memeAdapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (memes.isNotEmpty()) {
                    binding.recyclerViewMemes.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("MemeFragment", "Error loading top hit memes", error.toException())
            }
        })
    }

    private fun loadTopMissMemes() {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Load memes with high misses
        memesRef.orderByChild("miss").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by miss count (descending) and timestamp
                tempMemes.sortWith(compareByDescending<MemeMessage> { it.miss }
                    .thenByDescending { it.timestamp })

                // Clear existing memes
                memes.clear()
                memePositions.clear()

                // Add sorted memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Notify adapter
                memeAdapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (memes.isNotEmpty()) {
                    binding.recyclerViewMemes.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("MemeFragment", "Error loading top miss memes", error.toException())
            }
        })
    }

    private fun loadTeamMemes(team: String) {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Load memes for specific team
        memesRef.orderByChild("team").equalTo(team).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by timestamp (newest first)
                tempMemes.sortByDescending { it.timestamp }

                // Clear existing memes
                memes.clear()
                memePositions.clear()

                // Add sorted memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Notify adapter
                memeAdapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (memes.isNotEmpty()) {
                    binding.recyclerViewMemes.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
           //     Log.e("MemeFragment", "Error loading team memes", error.toException())
            }
        })
    }

    private fun openMemeComments(meme: MemeMessage) {
        if (!isUserLoggedIn()) {
            showLoginPrompt("Login to view and add comments")
            return
        }

        try {
        //    Log.d("MemeFragment", "Opening comments for meme ID: ${meme.id}")
            val intent = Intent(requireContext(), CommentActivity::class.java).apply {
                putExtra("MESSAGE_ID", meme.id)
                putExtra("MESSAGE_TYPE", "meme")
            }
            startActivity(intent)
        } catch (e: Exception) {
        //    Log.e("MemeFragment", "Error opening comment activity: ${e.message}", e)
            Toast.makeText(requireContext(), "Unable to open comments", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMemePicker() {
        // Use the activity result launcher instead of deprecated startActivityForResult
        memePicker.launch("image/*")
    }

    private fun showMemePreviewDialog() {
        if (!isAdded || _binding == null) return

        // Create loading dialog
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .setTitle("Checking content safety...")
            .create()

        progressDialog.show()

        // Create weak reference to fragment
        val fragmentRef = WeakReference(this)

        // Check image content safety using Cloud Vision API
        selectedImageUri?.let { uri ->
            // Using viewLifecycleOwner for coroutine scope to prevent leaks
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = safetyChecker.checkImageSafety(uri)

                    // Check if fragment is still valid
                    val fragment = fragmentRef.get()
                    if (fragment == null || !fragment.isAdded || fragment._binding == null) {
                        progressDialog.dismiss()
                        return@launch
                    }

                    progressDialog.dismiss()

                    if (result.isSafe) {
                        // Image is safe, proceed with upload
                        fragment.showUploadDialog()
                    } else if (result.autoBlock) {
                        // Image is NOT safe and should be automatically blocked
                        fragment.showContentBlockedDialog(result.issues)
                    } else {
                        // Image has potential issues but is not automatically blocked
                        fragment.showContentWarningDialog(result.issues)
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()

                    // Check if fragment is still valid
                    val fragment = fragmentRef.get()
                    if (fragment == null || !fragment.isAdded) return@launch

               //     Log.e("MemeFragment", "Error checking image safety: ${e.message}", e)
                    // Show error dialog
                    MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle("Error")
                        .setMessage("Failed to analyze image: ${e.message}")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
        } ?: run {
            progressDialog.dismiss()
            if (isAdded) {
                Toast.makeText(context, "No image selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showContentBlockedDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) {
            "This image has been blocked because our system detected prohibited content."
        } else {
            "This image has been blocked because our system detected:\n\n" +
                    issues.take(3).joinToString("\n")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Content Blocked")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
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

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Content Warning")
            .setMessage(message)
            .setPositiveButton("Upload Anyway") { dialog, _ ->
                dialog.dismiss()
                showUploadDialog()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
            }
            .show()
    }

    private fun showUploadDialog() {
        // Skip caption dialog and directly show loading indicator
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)

        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .setTitle("Uploading meme...")
            .create()

        progressDialog.show()

        // Proceed with upload (empty caption)
        uploadAndPostMeme("") { success ->
            progressDialog.dismiss()
            if (!success) {
                Toast.makeText(context, "Meme upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadAndPostMeme(caption: String, onComplete: ((Boolean) -> Unit)? = null) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        selectedImageUri?.let { uri ->
            // Create a reference to the image file in Firebase Storage
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("memes/${System.currentTimeMillis()}_${currentUser.uid}.jpg")

            // Upload the image
            imageRef.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    // Get the download URL
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        // Post meme with image
                        postMeme(caption, downloadUrl.toString())

                        // Clear selected image
                        selectedImageUri = null

                        // Callback success
                        onComplete?.invoke(true)
                    }
                }
                .addOnFailureListener { e ->
                    // Handle error
                //    Log.e("MemeFragment", "Meme upload failed: ${e.message}")
                    onComplete?.invoke(false)
                }
        }
    }

    private fun postMeme(caption: String, imageUrl: String) {
        if (currentUser == null) {
            return
        }

        val memeRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes").push()
        val memeId = memeRef.key ?: return

        // Get user's display name and profile picture
        val userRef = FirebaseDatabase.getInstance().getReference("Users/${currentUser!!.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java)
                    ?: currentUser!!.displayName
                    ?: "Anonymous"
                val userTeam = snapshot.child("iplTeam").getValue(String::class.java)
                    ?: "No Team"

                val memeMessage = MemeMessage(
                    id = memeId,
                    senderId = currentUser!!.uid,
                    senderName = userName,
                    team = userTeam,
                    memeUrl = imageUrl,
                    timestamp = System.currentTimeMillis()
                )

                memeRef.setValue(memeMessage)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Meme posted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        // Handle error
                    //    Log.e("MemeFragment", "Error posting meme", it)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("MemeFragment", "User data fetch cancelled", error.toException())
            }
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOGIN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // User successfully logged in
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto()
            fetchUserTeam()
            updateUIBasedOnLoginStatus()

            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        } else if (requestCode == PICK_MEME_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            showMemePreviewDialog()
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

    override fun onDestroyView() {
        super.onDestroyView()

        stripListener?.let {
            FirebaseDatabase.getInstance()
                .getReference("NoBallZone/liveRooms").removeEventListener(it)
        }
        // Remove all value event listeners
        for ((ref, listener) in valueEventListeners) {
            ref.removeEventListener(listener)
        }
        valueEventListeners.clear()

        // Remove all child event listeners
        for ((ref, listener) in childEventListeners) {
            ref.removeEventListener(listener)
        }
        childEventListeners.clear()

        // Cancel all handler callbacks
        Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)

        // Remove adapter to prevent leaks
        _binding?.recyclerViewMemes?.adapter = null

        // Clear the binding
        _binding = null
    }
}