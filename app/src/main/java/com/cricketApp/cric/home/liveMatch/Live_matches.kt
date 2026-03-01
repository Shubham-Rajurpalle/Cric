package com.cricketApp.cric.home.liveMatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.Chat.ChatFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.adapter.LiveMatchAdapter
import com.cricketApp.cric.databinding.FragmentLiveMatchesBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Live_matches : Fragment() {

    private var _binding: FragmentLiveMatchesBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance()
    private var liveRoomsListener: ValueEventListener? = null

    private lateinit var adapter: LiveMatchAdapter
    private val matchList = mutableListOf<MatchData>()

    private val activeStatuses = setOf(
        "Live", "Delayed", "Innings Break",
        "Lunch", "Tea", "Rain", "1st Innings", "2nd Innings"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveMatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        fetchLiveMatches()
    }

    private fun setupRecyclerView() {
        adapter = LiveMatchAdapter(matchList) { match -> openMatchRoom(match) }
        binding.liveMatchesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.liveMatchesRecyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            matchList.clear()
            adapter.notifyDataSetChanged()
            showLoadingState()
            fetchLiveMatches()
        }
    }

    private fun fetchLiveMatches() {
        showLoadingState()

        val ref = database.getReference("NoBallZone/liveRooms")
        liveRoomsListener?.let { ref.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                val tempList = mutableListOf<MatchData>()

                for (roomSnapshot in snapshot.children) {
                    val matchName = roomSnapshot.key ?: continue
                    val scores = roomSnapshot.child("scores")
                    if (!scores.exists()) continue

                    val isLive = scores.child("live").getValue(Boolean::class.java) ?: false
                    val status = scores.child("status").getValue(String::class.java) ?: ""
                    if (!isLive && status !in activeStatuses) continue

                    tempList.add(MatchData(
                        matchId = scores.child("matchId").getValue(Any::class.java)?.toString() ?: "",
                        matchName  = matchName,
                        status     = status,
                        note       = scores.child("note").getValue(String::class.java) ?: "",
                        live       = isLive,
                        type       = scores.child("type").getValue(String::class.java) ?: "",
                        round      = scores.child("round").getValue(String::class.java) ?: "",
                        startingAt = scores.child("startingAt").getValue(String::class.java) ?: "",
                        updatedAt  = scores.child("updatedAt").getValue(String::class.java) ?: "",
                        league = LeagueData(
                            id = scores.child("league/id").getValue(Any::class.java)?.toString() ?: "",
                            name      = scores.child("league/name").getValue(String::class.java) ?: "",
                            imagePath = scores.child("league/imagePath").getValue(String::class.java) ?: ""
                        ),
                        stage = StageData(
                            id = scores.child("stage/id").getValue(Any::class.java)?.toString() ?: "",
                            name = scores.child("stage/name").getValue(String::class.java) ?: ""
                        ),
                        localteam = TeamData(
                            id = scores.child("localteam/id").getValue(Any::class.java)?.toString() ?: "",
                            name      = scores.child("localteam/name").getValue(String::class.java) ?: "Team 1",
                            code      = scores.child("localteam/code").getValue(String::class.java) ?: "",
                            imagePath = scores.child("localteam/imagePath").getValue(String::class.java) ?: ""
                        ),
                        visitorteam = TeamData(
                            id = scores.child("visitorteam/id").getValue(Any::class.java)?.toString() ?: "",
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
                    ))
                }

                matchList.clear()
                matchList.addAll(tempList)
                adapter.notifyDataSetChanged()
                binding.swipeRefreshLayout.isRefreshing = false

                if (matchList.isEmpty()) showEmptyState() else showMatchesData()
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded || _binding == null) return
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
                if (matchList.isEmpty()) showEmptyState()
            }
        }

        ref.addValueEventListener(listener)
        liveRoomsListener = listener
    }

    private fun openMatchRoom(match: MatchData) {
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

    private fun showLoadingState() {
        binding.llAnime2.visibility                = View.VISIBLE
        binding.liveMatchesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility        = View.GONE
    }

    private fun showEmptyState() {
        binding.llAnime2.visibility                = View.GONE
        binding.liveMatchesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility        = View.VISIBLE
    }

    private fun showMatchesData() {
        binding.llAnime2.visibility                = View.GONE
        binding.liveMatchesRecyclerView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility        = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        liveRoomsListener?.let {
            database.getReference("NoBallZone/liveRooms").removeEventListener(it)
        }
        _binding?.liveMatchesRecyclerView?.adapter = null
        _binding = null
    }
}