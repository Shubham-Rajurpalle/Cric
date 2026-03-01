package com.cricketApp.cric.Profile

import android.animation.Animator
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.LogIn.UserInfo
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var activitiesAdapter: profileActivityAdapter
    private val activities = ArrayList<UserActivity>()

    private val currentUser = FirebaseAuth.getInstance().currentUser
    private val valueEventListeners = HashMap<DatabaseReference, ValueEventListener>()
    private var isFragmentActive = false

    // Cache flags — prevent reloading on revisit
    private var profileLoaded = false
    private var activitiesLoaded = false
    private var statsLoaded = false

    // Tracks how many critical loads (profile + stats) have completed
    // Only when BOTH finish does the full-screen overlay disappear
    private var initialLoadCount = 0
    private val INITIAL_LOAD_TOTAL = 2

    private var userHits: Int = 0
    private var userMisses: Int = 0

    // Tracks how many of the 4 activity sources have returned
    private var activitiesSourcesDone = 0
    private val ACTIVITIES_SOURCES_TOTAL = 4

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentActive = true

        // Show full-screen overlay immediately on open
        showLoadingOverlay()

        binding.backButton.setOnClickListener { requireActivity().onBackPressed() }
        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(requireContext(), UserInfo::class.java))
        }

        setupActivitiesRecyclerView()

        // Load data — skip if already cached
        if (!profileLoaded) loadUserProfile() else onInitialLoadDone()
        if (!statsLoaded) calculateUserStatistics() else onInitialLoadDone()
        if (!activitiesLoaded) loadUserActivities()

        binding.seeAllButton.setOnClickListener {
            startActivity(Intent(requireContext(), AllActivitiesActivity::class.java))
        }
        binding.faqSection.setOnClickListener { openUrl("https://sites.google.com/view/cricpolicies/home") }
        binding.termsSection.setOnClickListener { openUrl("https://sites.google.com/view/cricpolicies/terms-and-conditions") }
        binding.privacySection.setOnClickListener { openUrl("https://sites.google.com/view/cricpolicies/privacy-policy") }
        binding.logoutButton.setOnClickListener { showLogoutConfirmationDialog() }
    }

    // ── Loading overlay ──────────────────────────────────────

    private fun showLoadingOverlay() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingOverlay.alpha = 1f
        binding.mainContent.visibility = View.GONE
        binding.lottieLoading.playAnimation()
    }

    private fun hideLoadingOverlay() {
        if (!isFragmentActive || _binding == null) return

        // Fade in main content
        binding.mainContent.visibility = View.VISIBLE
        binding.mainContent.alpha = 0f
        binding.mainContent.animate()
            .alpha(1f)
            .setDuration(350)
            .start()

        // Fade out overlay
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    if (_binding == null) return
                    binding.loadingOverlay.visibility = View.GONE
                    binding.lottieLoading.cancelAnimation()
                }
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            .start()
    }

    /** Called when profile or stats finishes. When both done → hide overlay. */
    private fun onInitialLoadDone() {
        initialLoadCount++
        if (initialLoadCount >= INITIAL_LOAD_TOTAL) {
            hideLoadingOverlay()
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun openUrl(url: String) {
        context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // ── RecyclerView ─────────────────────────────────────────

    private fun setupActivitiesRecyclerView() {
        activitiesAdapter = profileActivityAdapter(mutableListOf()) { activity ->
            val intent = Intent(context, com.cricketApp.cric.Chat.CommentActivity::class.java)
            when (activity.type) {
                UserActivityType.COMMENT -> {
                    intent.putExtra("ITEM_ID", activity.additionalData?.get("parentId") as? String ?: "")
                    intent.putExtra("ITEM_TYPE", activity.additionalData?.get("parentType") as? String ?: "chat")
                }
                else -> {
                    intent.putExtra("ITEM_ID", activity.id)
                    intent.putExtra("ITEM_TYPE", activity.type.name.lowercase())
                }
            }
            startActivity(intent)
        }

        binding.activitiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = activitiesAdapter
        }
    }

    // ── Profile load ─────────────────────────────────────────

    private fun loadUserProfile() {
        if (!isFragmentActive) return
        val userId = currentUser?.uid ?: run { onInitialLoadDone(); return }
        val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFragmentActive || _binding == null) return

                binding.userName.text = snapshot.child("username").getValue(String::class.java) ?: "User"

                val photoUrl = snapshot.child("profilePhoto").getValue(String::class.java)
                if (!photoUrl.isNullOrEmpty()) {
                    try {
                        Glide.with(this@ProfileFragment)
                            .load(photoUrl)
                            .placeholder(R.drawable.profile_empty)
                            .into(binding.profileImage)
                    } catch (e: Exception) { }
                }

                if (snapshot.hasChild("hits")) userHits = snapshot.child("hits").getValue(Int::class.java) ?: 0
                if (snapshot.hasChild("misses")) userMisses = snapshot.child("misses").getValue(Int::class.java) ?: 0

                updateStatisticsDisplay()
                profileLoaded = true
                onInitialLoadDone()
            }

            override fun onCancelled(error: DatabaseError) {
                onInitialLoadDone() // Unblock UI even on failure
            }
        }

        userRef.addListenerForSingleValueEvent(listener)
        valueEventListeners[userRef] = listener
    }

    // ── Stats ────────────────────────────────────────────────

    private fun calculateUserStatistics() {
        val userId = currentUser?.uid ?: run { onInitialLoadDone(); return }
        val userStatsRef = FirebaseDatabase.getInstance().getReference("Users/$userId")

        calculateStatsFromCollection("NoBallZone/chats", userId) { chatHits, chatMisses ->
            calculateStatsFromCollection("NoBallZone/memes", userId) { memeHits, memeMisses ->
                calculateStatsFromCollection("NoBallZone/polls", userId) { pollHits, pollMisses ->
                    userHits = chatHits + memeHits + pollHits
                    userMisses = chatMisses + memeMisses + pollMisses
                    userStatsRef.updateChildren(hashMapOf<String, Any>("hits" to userHits, "misses" to userMisses))
                    updateStatisticsDisplay()
                    statsLoaded = true
                    onInitialLoadDone()
                }
            }
        }
    }

    private fun calculateStatsFromCollection(path: String, userId: String, callback: (Int, Int) -> Unit) {
        FirebaseDatabase.getInstance().getReference(path)
            .orderByChild("senderId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var hits = 0; var misses = 0
                    for (item in snapshot.children) {
                        hits += item.child("hit").getValue(Int::class.java) ?: 0
                        misses += item.child("miss").getValue(Int::class.java) ?: 0
                    }
                    callback(hits, misses)
                }
                override fun onCancelled(error: DatabaseError) { callback(0, 0) }
            })
    }

    private fun updateStatisticsDisplay() {
        if (!isFragmentActive || _binding == null) return
        fun fmt(n: Int) = if (n >= 1000) String.format("%.1fK", n / 1000f) else n.toString()
        binding.hitsCount.text = fmt(userHits)
        binding.missesCount.text = fmt(userMisses)
    }

    // ── Activities load ──────────────────────────────────────

    private fun loadUserActivities() {
        val userId = currentUser?.uid ?: return
        activities.clear()
        activitiesSourcesDone = 0

        // Hide both list and empty state while loading
        binding.activitiesRecyclerView.visibility = View.GONE
        binding.activitiesEmptyState.visibility = View.GONE

        loadUserChats(userId)
        loadUserMemes(userId)
        loadUserPolls(userId)
        loadUserComments(userId)
    }

    private fun onActivitySourceLoaded() {
        activitiesSourcesDone++
        if (activitiesSourcesDone >= ACTIVITIES_SOURCES_TOTAL) {
            refreshActivitiesUI()
        }
    }

    private fun loadUserChats(userId: String) {
        if (!isFragmentActive) return
        FirebaseDatabase.getInstance().getReference("NoBallZone/chats")
            .orderByChild("senderId").equalTo(userId).limitToLast(15)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isFragmentActive) return
                    for (s in snapshot.children) {
                        FirebaseDataHelper.getChatMessageFromSnapshot(s)?.let {
                            activities.add(UserActivity(it.id, UserActivityType.CHAT, it.senderName, it.senderId, it.team, it.message, it.imageUrl ?: "", it.timestamp, it.hit, it.miss, it.reactions))
                        }
                    }
                    onActivitySourceLoaded()
                }
                override fun onCancelled(error: DatabaseError) { onActivitySourceLoaded() }
            })
    }

    private fun loadUserMemes(userId: String) {
        if (!isFragmentActive) return
        FirebaseDatabase.getInstance().getReference("NoBallZone/memes")
            .orderByChild("senderId").equalTo(userId).limitToLast(15)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isFragmentActive) return
                    for (s in snapshot.children) {
                        FirebaseDataHelper.getMemeMessageFromSnapshot(s)?.let {
                            activities.add(UserActivity(it.id, UserActivityType.MEME, it.senderName, it.senderId, it.team, "", it.memeUrl, it.timestamp, it.hit, it.miss, it.reactions))
                        }
                    }
                    onActivitySourceLoaded()
                }
                override fun onCancelled(error: DatabaseError) { onActivitySourceLoaded() }
            })
    }

    private fun loadUserPolls(userId: String) {
        if (!isFragmentActive) return
        FirebaseDatabase.getInstance().getReference("NoBallZone/polls")
            .orderByChild("senderId").equalTo(userId).limitToLast(15)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isFragmentActive) return
                    for (s in snapshot.children) {
                        FirebaseDataHelper.getPollMessageFromSnapshot(s)?.let {
                            activities.add(UserActivity(it.id, UserActivityType.POLL, it.senderName, it.senderId, it.team, it.question, "", it.timestamp, it.hit, it.miss, it.reactions,
                                mapOf("options" to (it.options as Any), "voters" to ((it.voters ?: mapOf<String, String>()) as Any))))
                        }
                    }
                    onActivitySourceLoaded()
                }
                override fun onCancelled(error: DatabaseError) { onActivitySourceLoaded() }
            })
    }

    private fun loadUserComments(userId: String) {
        if (!isFragmentActive) return
        FirebaseDatabase.getInstance()
            .getReference("userComments/$userId")
            .orderByChild("timestamp")
            .limitToLast(15)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isFragmentActive) return
                    for (s in snapshot.children) {
                        activities.add(UserActivity(
                            id = s.key ?: continue,
                            type = UserActivityType.COMMENT,
                            username = s.child("senderName").getValue(String::class.java) ?: "Anonymous",
                            userId = userId,
                            team = s.child("team").getValue(String::class.java) ?: "",
                            content = s.child("message").getValue(String::class.java) ?: "",
                            imageUrl = s.child("imageUrl").getValue(String::class.java) ?: "",
                            timestamp = s.child("timestamp").getValue(Long::class.java) ?: 0L,
                            additionalData = mapOf(
                                "parentId" to (s.child("parentId").getValue(String::class.java) ?: ""),
                                "parentType" to (s.child("parentType").getValue(String::class.java) ?: "chat")
                            )
                        ))
                    }
                    onActivitySourceLoaded()
                }
                override fun onCancelled(error: DatabaseError) { onActivitySourceLoaded() }
            })
    }

    private fun refreshActivitiesUI() {
        if (!isFragmentActive || _binding == null) return

        activities.sortByDescending { it.timestamp }
        val preview = activities.take(15)

        if (preview.isEmpty()) {
            binding.activitiesRecyclerView.visibility = View.GONE
            binding.activitiesEmptyState.visibility = View.VISIBLE
        } else {
            binding.activitiesEmptyState.visibility = View.GONE
            binding.activitiesRecyclerView.visibility = View.VISIBLE
            activitiesAdapter.updateData(ArrayList(preview))
        }

        activitiesLoaded = true
    }

    // ── Logout ───────────────────────────────────────────────

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogTheme)
            .setTitle("Logout").setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(requireContext(), SignIn::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            }.setNegativeButton("No", null).show()
    }

    // ── Lifecycle ────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        _binding?.lottieLoading?.cancelAnimation()
        for ((ref, listener) in valueEventListeners) ref.removeEventListener(listener)
        valueEventListeners.clear()
        _binding?.activitiesRecyclerView?.adapter = null
        _binding = null
    }
}