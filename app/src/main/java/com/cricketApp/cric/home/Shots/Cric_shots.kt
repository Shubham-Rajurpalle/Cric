package com.cricketApp.cric.home.Shots

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.ChatFragment
import com.cricketApp.cric.Chat.ChatRoomItem
import com.cricketApp.cric.Chat.RoomType
import com.cricketApp.cric.Meme.MemeFragment
import com.cricketApp.cric.Meme.MemeMessage
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentCricShotsBinding
import com.cricketApp.cric.databinding.ItemChatRoomCardBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Cric_shots : Fragment() {

    private var _binding: FragmentCricShotsBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var newsAdapter: NewsAdapter
    private val videoList = mutableListOf<Video>()
    private val newsList = mutableListOf<News>()
    private val memeList = mutableListOf<MemeMessage>()
    private var firestore: FirebaseFirestore? = null

    private val database = FirebaseDatabase.getInstance()
    private var liveRoomListener: ValueEventListener? = null
    private var memesListener: ValueEventListener? = null

    private lateinit var topMemeAdapter: HomeMemePreviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCricShotsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwipeRefreshLayout()
        setupCricShotsRecyclerView()
        setupNewsRecyclerView()
        setupTopMemesRecyclerView()
        fetchVideos()
        fetchNews()
        observeLiveMatch()
        fetchTopMemes()
    }

    // ── SwipeRefresh ──────────────────────────────────────────────────────────

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener { refreshData() }
    }

    private fun refreshData() {
        val b = _binding ?: return
        // Show all three loaders when refreshing
        b.llAnime.visibility  = View.VISIBLE   // shots loader
        b.llAnime2.visibility = View.VISIBLE   // news loader
        b.llAnime3.visibility = View.VISIBLE   // memes loader

        // Hide recyclers while refreshing so loaders are visible
        b.shotsRecyclerView.visibility = View.GONE
        b.newsRecycleView.visibility   = View.GONE
        b.rvTopMemes.visibility        = View.GONE

        videoList.clear()
        newsList.clear()
        memeList.clear()
        videoAdapter.notifyDataSetChanged()
        newsAdapter.notifyDataSetChanged()
        topMemeAdapter.submitList(emptyList())

        fetchVideos()
        fetchNews()
        fetchTopMemes()
    }

    // ── Recycler setup ────────────────────────────────────────────────────────

    private fun setupNewsRecyclerView() {
        binding.newsRecycleView.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.VERTICAL, false
        )
        newsAdapter = NewsAdapter(newsList, requireContext())
        binding.newsRecycleView.adapter = newsAdapter
    }

    private fun setupCricShotsRecyclerView() {
        val viewPager2 = requireActivity().findViewById<ViewPager2>(R.id.viewPager)
        binding.shotsRecyclerView.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            (this as? InterceptableRecyclerView)?.viewPager2 = viewPager2
        }
        videoAdapter = VideoAdapter(videoList) { video -> openVideoPlayer(video) }
        binding.shotsRecyclerView.adapter = videoAdapter
    }

    // ── Top Memes ─────────────────────────────────────────────────────────────

    private fun setupTopMemesRecyclerView() {
        topMemeAdapter = HomeMemePreviewAdapter { _ -> navigateToMemeSection() }
        binding.rvTopMemes.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = topMemeAdapter
        }
        binding.tvSeeAllMemes.setOnClickListener { navigateToMemeSection() }
    }

    private fun fetchTopMemes() {
        val b = _binding ?: return

        // Show meme lottie loader, hide RecyclerView while fetching
        b.llAnime3.visibility = View.VISIBLE
        b.rvTopMemes.visibility = View.GONE

        val ref = database.getReference("NoBallZone/memes")
        memesListener?.let { ref.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                val tempMemes = mutableListOf<MemeMessage>()
                for (child in snapshot.children) {
                    val meme = try {
                        MemeMessage(
                            id         = child.key ?: continue,
                            senderId   = child.child("senderId").getValue(String::class.java) ?: "",
                            senderName = child.child("senderName").getValue(String::class.java) ?: "",
                            team       = child.child("team").getValue(String::class.java) ?: "",
                            memeUrl    = child.child("memeUrl").getValue(String::class.java) ?: "",
                            timestamp  = child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            hit        = child.child("hit").getValue(Int::class.java) ?: 0,
                            miss       = child.child("miss").getValue(Int::class.java) ?: 0
                        )
                    } catch (e: Exception) { continue }
                    if (meme.memeUrl.isNotEmpty()) tempMemes.add(meme)
                }

                val top3 = tempMemes
                    .sortedByDescending { it.hit - it.miss }
                    .take(3)

                // Hide loader, show RecyclerView with results
                _binding?.llAnime3?.visibility   = View.GONE
                _binding?.rvTopMemes?.visibility = View.VISIBLE

                if (top3.isNotEmpty()) {
                    _binding?.sectionTopMemes?.visibility = View.VISIBLE
                    topMemeAdapter.submitList(top3)
                } else {
                    _binding?.sectionTopMemes?.visibility = View.GONE
                }

                checkRefreshComplete()
            }

            override fun onCancelled(error: DatabaseError) {
                // Hide loader even on failure
                _binding?.llAnime3?.visibility   = View.GONE
                _binding?.rvTopMemes?.visibility = View.VISIBLE
                checkRefreshComplete()
            }
        }

        ref.addListenerForSingleValueEvent(listener)
        memesListener = listener
    }

    // ── Live Match Banner ─────────────────────────────────────────────────────

    private fun observeLiveMatch() {
        val ref = database.getReference("NoBallZone/liveRooms")
        liveRoomListener?.let { ref.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                val activeStatuses = setOf(
                    "Live", "Delayed", "Innings Break",
                    "Lunch", "Tea", "Rain", "1st Innings", "2nd Innings"
                )

                var activeRoom: ChatRoomItem? = null

                for (matchSnapshot in snapshot.children) {
                    val scores = matchSnapshot.child("scores")
                    if (!scores.exists()) continue

                    val isLive = scores.child("live").getValue(Boolean::class.java) ?: false
                    val status = scores.child("status").getValue(String::class.java) ?: ""
                    if (!isLive && status !in activeStatuses) continue

                    val localTeamName   = scores.child("localteam").child("name").getValue(String::class.java) ?: "Home"
                    val visitorTeamName = scores.child("visitorteam").child("name").getValue(String::class.java) ?: "Away"
                    val matchName       = scores.child("matchName").getValue(String::class.java) ?: matchSnapshot.key ?: ""

                    val leagueImagePath  = scores.child("league").child("imagePath").getValue(String::class.java) ?: ""
                    val localImagePath   = scores.child("localteam").child("imagePath").getValue(String::class.java) ?: ""
                    val visitorImagePath = scores.child("visitorteam").child("imagePath").getValue(String::class.java) ?: ""
                    val bannerImageUrl   = when {
                        leagueImagePath.isNotEmpty()  -> leagueImagePath
                        localImagePath.isNotEmpty()   -> localImagePath
                        visitorImagePath.isNotEmpty() -> visitorImagePath
                        else -> ""
                    }

                    val localRuns      = scores.child("localteamScore").child("runs").getValue(Int::class.java) ?: 0
                    val localWickets   = scores.child("localteamScore").child("wickets").getValue(Int::class.java) ?: 0
                    val localOvers     = scores.child("localteamScore").child("overs").getValue(Double::class.java) ?: 0.0
                    val visitorRuns    = scores.child("visitorteamScore").child("runs").getValue(Int::class.java) ?: 0
                    val visitorWickets = scores.child("visitorteamScore").child("wickets").getValue(Int::class.java) ?: 0
                    val visitorOvers   = scores.child("visitorteamScore").child("overs").getValue(Double::class.java) ?: 0.0

                    val note        = scores.child("note").getValue(String::class.java)  ?: ""
                    val round       = scores.child("round").getValue(String::class.java) ?: ""
                    val type        = scores.child("type").getValue(String::class.java)  ?: ""
                    val activeUsers = matchSnapshot.child("activeUsers").getValue(Int::class.java) ?: 0

                    val description = buildString {
                        if (type.isNotEmpty())  append(type)
                        if (round.isNotEmpty()) append(" • $round")
                        if (status.isNotEmpty() && status != "Live") append(" • ⚠️ $status")
                        if (note.isNotEmpty()) append("\n$note")
                        append("\n$localTeamName: $localRuns/$localWickets (${localOvers} ov)")
                        append("\n$visitorTeamName: $visitorRuns/$visitorWickets (${visitorOvers} ov)")
                    }

                    activeRoom = ChatRoomItem(
                        id             = matchSnapshot.key ?: continue,
                        name           = matchName,
                        description    = description,
                        type           = RoomType.LIVE,
                        bannerImageUrl = bannerImageUrl,
                        isLive         = isLive,
                        activeUsers    = activeUsers,
                        createdAt      = System.currentTimeMillis()
                    )
                    break
                }

                if (activeRoom != null) {
                    binding.sectionLiveMatch.visibility = View.VISIBLE
                    bindLiveMatchCard(activeRoom!!)
                } else {
                    binding.sectionLiveMatch.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)
        liveRoomListener = listener
    }

    private fun bindLiveMatchCard(room: ChatRoomItem) {
        val cardBinding = ItemChatRoomCardBinding.bind(binding.liveMatchCard.root)
        with(cardBinding) {
            roomName.text        = room.name
            roomDescription.text = room.description
            chipLive.visibility  = if (room.isLive) View.VISIBLE else View.GONE

            if (room.activeUsers > 0) {
                activeUsersText.visibility = View.VISIBLE
                activeUsersText.text       = "${room.activeUsers} online"
            } else {
                activeUsersText.visibility = View.GONE
            }

            if (room.bannerImageUrl.isNotEmpty()) {
                context?.let { ctx ->
                    Glide.with(ctx)
                        .load(room.bannerImageUrl)
                        .placeholder(R.drawable.loading)
                        .into(bannerImage)
                }
            } else {
                bannerImage.setImageResource(R.drawable.icc_logo)
            }

            root.setOnClickListener { openMatchRoom(room) }
        }
        binding.tvJoinChat.setOnClickListener { openMatchRoom(room) }
    }

    private fun openMatchRoom(room: ChatRoomItem) {
        val args = Bundle().apply {
            putString("ROOM_ID",   room.id)
            putString("ROOM_TYPE", room.type.name)
            putString("ROOM_NAME", room.name)
        }
        val chatFragment = ChatFragment().apply { arguments = args }
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.navHost, chatFragment, "ChatRoom")
            .commitAllowingStateLoss()
    }

    // ── Navigate to memes ─────────────────────────────────────────────────────

    private fun navigateToMemeSection() {
        try {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.memeIcon
        } catch (e: Exception) { /* nav item may differ */ }

        parentFragmentManager.beginTransaction()
            .replace(R.id.navHost, MemeFragment())
            .addToBackStack(null)
            .commit()
    }

    // ── Fetch methods ─────────────────────────────────────────────────────────

    private fun fetchVideos() {
        val firestoreInstance = firestore ?: return
        if (!isAdded) return

        // Loader already visible from XML / refreshData(), just ensure recycler is hidden
        _binding?.shotsRecyclerView?.visibility = View.GONE

        firestoreInstance.collection("videos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                // Hide loader, reveal recycler
                _binding?.llAnime?.visibility        = View.GONE
                _binding?.shotsRecyclerView?.visibility = View.VISIBLE
                snapshot?.let {
                    if (!it.isEmpty) {
                        videoList.clear()
                        videoList.addAll(it.toObjects(Video::class.java))
                        videoAdapter.notifyDataSetChanged()
                    }
                }
                checkRefreshComplete()
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                _binding?.llAnime?.visibility        = View.GONE
                _binding?.shotsRecyclerView?.visibility = View.VISIBLE
                checkRefreshComplete()
            }
    }

    private fun fetchNews() {
        // Loader already visible from XML / refreshData(), just ensure recycler is hidden
        _binding?.newsRecycleView?.visibility = View.GONE

        firestore?.collection("NewsPosts")
            ?.orderBy("timestamp", Query.Direction.DESCENDING)
            ?.get()
            ?.addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                // Hide loader, reveal recycler
                _binding?.llAnime2?.visibility      = View.GONE
                _binding?.newsRecycleView?.visibility = View.VISIBLE
                snapshot?.let {
                    if (!it.isEmpty) {
                        newsList.clear()
                        newsList.addAll(it.toObjects(News::class.java))
                        newsAdapter.notifyDataSetChanged()
                    }
                }
                checkRefreshComplete()
            }
            ?.addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                _binding?.llAnime2?.visibility      = View.GONE
                _binding?.newsRecycleView?.visibility = View.VISIBLE
                checkRefreshComplete()
            }
    }

    // All three loaders must be gone before swipe-refresh spinner is dismissed
    private fun checkRefreshComplete() {
        val b = _binding ?: return
        if (b.llAnime.visibility  == View.GONE &&
            b.llAnime2.visibility == View.GONE &&
            b.llAnime3.visibility == View.GONE
        ) {
            b.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun openVideoPlayer(video: Video) {
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra("videoUrl", video.videoUrl)
            putExtra("id", video.id)
        }
        startActivity(intent)
    }

    // ── Lifecycle cleanup ─────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        liveRoomListener?.let {
            database.getReference("NoBallZone/liveRooms").removeEventListener(it)
        }
        memesListener?.let {
            database.getReference("NoBallZone/memes").removeEventListener(it)
        }
        _binding?.shotsRecyclerView?.adapter = null
        _binding?.newsRecycleView?.adapter   = null
        _binding?.rvTopMemes?.adapter        = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        firestore = null
    }
}