package com.cricketApp.cric.home.Shots

import android.content.Intent
import android.graphics.Color
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
import com.cricketApp.cric.LiveScoreStripAdapter
import com.cricketApp.cric.databinding.FragmentCricShotsBinding
import com.cricketApp.cric.home.liveMatch.LeagueData
import com.cricketApp.cric.home.liveMatch.MatchData
import com.cricketApp.cric.home.liveMatch.ScoreData
import com.cricketApp.cric.home.liveMatch.StageData
import com.cricketApp.cric.home.liveMatch.TeamData
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
    private val newsList  = mutableListOf<News>()
    private val memeList  = mutableListOf<MemeMessage>()
    private var firestore: FirebaseFirestore? = null

    private val database = FirebaseDatabase.getInstance()
    private var liveRoomListener: ValueEventListener? = null
    private var memesListener:    ValueEventListener? = null

    private lateinit var topMemeAdapter: HomeMemePreviewAdapter

    // ── Live score strip (same as Chat / Meme) ────────────────────────────────
    private val liveStripMatches = mutableListOf<MatchData>()
    private lateinit var liveStripAdapter: LiveScoreStripAdapter

    private val activeStatuses = setOf(
        "Live", "Delayed", "Innings Break",
        "Lunch", "Tea", "Rain", "1st Innings", "2nd Innings"
    )

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
        setupLiveScoreStrip()          // ← replaces observeLiveMatch()
        fetchVideos()
        fetchNews()
        fetchTopMemes()
    }

    // ── SwipeRefresh ──────────────────────────────────────────────────────────

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener { refreshData() }
    }

    private fun refreshData() {
        val b = _binding ?: return
        b.llAnime.visibility  = View.VISIBLE
        b.llAnime2.visibility = View.VISIBLE
        b.llAnime3.visibility = View.VISIBLE

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

    // ── Recycler setups ───────────────────────────────────────────────────────

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
        b.llAnime3.visibility   = View.VISIBLE
        b.rvTopMemes.visibility = View.GONE

        // Query index directly — no full scan
        database.getReference("NoBallZone/memesByHit")
            .orderByValue()
            .limitToLast(3)  // top 3 by hit count
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || _binding == null) return

                    // Get IDs with their hit counts, highest first
                    val idToHit = snapshot.children
                        .associate { it.key!! to (it.getValue(Int::class.java) ?: 0) }
                        .entries.sortedByDescending { it.value }

                    if (idToHit.isEmpty()) {
                        _binding?.llAnime3?.visibility   = View.GONE
                        _binding?.sectionTopMemes?.visibility = View.GONE
                        checkRefreshComplete()
                        return
                    }

                    // Fan-out fetch full meme data for each ID in parallel
                    val results = mutableListOf<MemeMessage?>()
                    var completed = 0
                    val total = idToHit.size

                    idToHit.forEach { (id, hitCount) ->
                        database.getReference("NoBallZone/memes/$id")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(memeSnap: DataSnapshot) {
                                    if (!isAdded || _binding == null) return
                                    try {
                                        val meme = MemeMessage(
                                            id         = memeSnap.key ?: "",
                                            senderId   = memeSnap.child("senderId").getValue(String::class.java) ?: "",
                                            senderName = memeSnap.child("senderName").getValue(String::class.java) ?: "",
                                            team       = memeSnap.child("team").getValue(String::class.java) ?: "",
                                            memeUrl    = memeSnap.child("memeUrl").getValue(String::class.java) ?: "",
                                            timestamp  = memeSnap.child("timestamp").getValue(Long::class.java) ?: 0L,
                                            hit        = hitCount,  // use index value — always accurate
                                            miss       = memeSnap.child("miss").getValue(Int::class.java) ?: 0
                                        )
                                        if (meme.memeUrl.isNotEmpty()) results.add(meme)
                                        else results.add(null)
                                    } catch (e: Exception) {
                                        results.add(null)
                                    }
                                    completed++
                                    if (completed == total) showTopMemes()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    results.add(null)
                                    completed++
                                    if (completed == total) showTopMemes()
                                }

                                private fun showTopMemes() {
                                    if (!isAdded || _binding == null) return
                                    val top3 = idToHit
                                        .mapNotNull { (id, _) -> results.filterNotNull().find { it.id == id } }
                                        .take(3)

                                    _binding?.llAnime3?.visibility = View.GONE
                                    _binding?.rvTopMemes?.visibility = View.VISIBLE
                                    if (top3.isNotEmpty()) {
                                        _binding?.sectionTopMemes?.visibility = View.VISIBLE
                                        topMemeAdapter.submitList(top3)
                                    } else {
                                        _binding?.sectionTopMemes?.visibility = View.GONE
                                    }
                                    checkRefreshComplete()
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _binding?.llAnime3?.visibility   = View.GONE
                    _binding?.rvTopMemes?.visibility = View.VISIBLE
                    checkRefreshComplete()
                }
            })
    }

    // ── Live Score Strip (identical pattern to Chat / Meme fragments) ─────────

    private fun setupLiveScoreStrip() {
        liveStripAdapter = LiveScoreStripAdapter(liveStripMatches) { match ->
            openMatchRoom(match)
        }

        binding.liveScoreStripRecycler.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = liveStripAdapter
            isNestedScrollingEnabled = false
        }

        fetchLiveScoreStrip()
    }

    private fun fetchLiveScoreStrip() {
        val ref = database.getReference("NoBallZone/liveRooms")
        liveRoomListener?.let { ref.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
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
                    )
                }

                liveStripAdapter.updateMatches(temp)

                // Show/hide the whole strip container
                _binding?.liveScoreStripContainer?.visibility =
                    if (temp.isEmpty()) View.GONE else View.VISIBLE
            }

            override fun onCancelled(error: DatabaseError) {
                _binding?.liveScoreStripContainer?.visibility = View.GONE
            }
        }

        ref.addValueEventListener(listener)
        liveRoomListener = listener
    }

    // ── Open match chat room from strip card tap ───────────────────────────────

    private fun openMatchRoom(match: MatchData) {
        val args = Bundle().apply {
            putString("ROOM_ID",   match.matchName)
            putString("ROOM_TYPE", RoomType.LIVE.name)
            putString("ROOM_NAME", match.matchName)
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
        } catch (e: Exception) { }

        parentFragmentManager.beginTransaction()
            .replace(R.id.navHost, MemeFragment())
            .addToBackStack(null)
            .commit()
    }

    // ── Fetch videos / news ───────────────────────────────────────────────────

    private fun fetchVideos() {
        val firestoreInstance = firestore ?: return
        if (!isAdded) return
        _binding?.shotsRecyclerView?.visibility = View.GONE

        firestoreInstance.collection("videos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                _binding?.llAnime?.visibility           = View.GONE
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
                _binding?.llAnime?.visibility           = View.GONE
                _binding?.shotsRecyclerView?.visibility = View.VISIBLE
                checkRefreshComplete()
            }
    }

    private fun fetchNews() {
        _binding?.newsRecycleView?.visibility = View.GONE

        firestore?.collection("NewsPosts")
            ?.orderBy("timestamp", Query.Direction.DESCENDING)
            ?.get()
            ?.addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                _binding?.llAnime2?.visibility        = View.GONE
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
                _binding?.llAnime2?.visibility        = View.GONE
                _binding?.newsRecycleView?.visibility = View.VISIBLE
                checkRefreshComplete()
            }
    }

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
        _binding?.shotsRecyclerView?.adapter        = null
        _binding?.newsRecycleView?.adapter          = null
        _binding?.rvTopMemes?.adapter               = null
        _binding?.liveScoreStripRecycler?.adapter   = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        firestore = null
    }
}