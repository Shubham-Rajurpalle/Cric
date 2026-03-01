package com.cricketApp.cric.home.upcomingMatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.adapter.UpcomingMatchAdapter
import com.cricketApp.cric.databinding.FragmentUpcomingMatchesBinding
import com.cricketApp.cric.home.UpcomingMatches.UpcomingLeagueData
import com.cricketApp.cric.home.UpcomingMatches.UpcomingMatchData
import com.cricketApp.cric.home.UpcomingMatches.UpcomingStageData
import com.cricketApp.cric.home.UpcomingMatches.UpcomingTeamData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Upcoming_matches : Fragment() {

    private var _binding: FragmentUpcomingMatchesBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance()
    private var upcomingListener: ValueEventListener? = null

    private lateinit var adapter: UpcomingMatchAdapter
    private val matchList = mutableListOf<UpcomingMatchData>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpcomingMatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        fetchUpcomingMatches()
    }

    private fun setupRecyclerView() {
        adapter = UpcomingMatchAdapter(matchList)
        binding.upcomingMatchesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.upcomingMatchesRecyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            matchList.clear()
            adapter.notifyDataSetChanged()
            showLoadingState()
            fetchUpcomingMatches()
        }
    }

    private fun fetchUpcomingMatches() {
        showLoadingState()

        val ref = database.getReference("NoBallZone/upcoming")
        upcomingListener?.let { ref.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                val tempList = mutableListOf<UpcomingMatchData>()

                for (matchSnapshot in snapshot.children) {
                    try {
                        tempList.add(UpcomingMatchData(
                            id          = matchSnapshot.child("id").getValue(Any::class.java)?.toString() ?: "",
                            matchName   = matchSnapshot.child("matchName").getValue(String::class.java) ?: "",
                            status      = matchSnapshot.child("status").getValue(String::class.java) ?: "",
                            type        = matchSnapshot.child("type").getValue(String::class.java) ?: "",
                            round       = matchSnapshot.child("round").getValue(String::class.java) ?: "",
                            startingAt  = matchSnapshot.child("startingAt").getValue(String::class.java) ?: "",
                            league = UpcomingLeagueData(
                                id = matchSnapshot.child("league/id").getValue(Any::class.java)
                                    ?.toString() ?: "",
                                name = matchSnapshot.child("league/name")
                                    .getValue(String::class.java) ?: "",
                                imagePath = matchSnapshot.child("league/imagePath")
                                    .getValue(String::class.java) ?: ""
                            ),
                            stage = UpcomingStageData(
                                id = matchSnapshot.child("stage/id").getValue(Any::class.java)
                                    ?.toString() ?: "",
                                name = matchSnapshot.child("stage/name")
                                    .getValue(String::class.java) ?: ""
                            ),
                            localteam = UpcomingTeamData(
                                id = matchSnapshot.child("localteam/id").getValue(Any::class.java)
                                    ?.toString() ?: "",
                                name = matchSnapshot.child("localteam/name")
                                    .getValue(String::class.java) ?: "Team 1",
                                code = matchSnapshot.child("localteam/code")
                                    .getValue(String::class.java) ?: "",
                                imagePath = matchSnapshot.child("localteam/imagePath")
                                    .getValue(String::class.java) ?: ""
                            ),
                            visitorteam = UpcomingTeamData(
                                id        = matchSnapshot.child("visitorteam/id").getValue(Any::class.java)?.toString() ?: "",
                                name      = matchSnapshot.child("visitorteam/name").getValue(String::class.java) ?: "Team 2",
                                code      = matchSnapshot.child("visitorteam/code").getValue(String::class.java) ?: "",
                                imagePath = matchSnapshot.child("visitorteam/imagePath").getValue(String::class.java) ?: ""
                            )
                        ))
                    } catch (e: Exception) {
                        // skip malformed entry
                    }
                }

                val sdf = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.getDefault()
                )

                tempList.sortBy { match ->
                    try {
                        sdf.parse(match.startingAt)
                    } catch (e: Exception) {
                        null
                    }
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
        upcomingListener = listener
    }

    private fun showLoadingState() {
        binding.llAnime2.visibility                    = View.VISIBLE
        binding.upcomingMatchesRecyclerView.visibility = View.GONE
        binding.textViewNoMatches.visibility           = View.GONE
    }

    private fun showEmptyState() {
        binding.llAnime2.visibility                    = View.GONE
        binding.upcomingMatchesRecyclerView.visibility = View.GONE
        binding.textViewNoMatches.visibility           = View.VISIBLE
        binding.textViewNoMatches.text                 = "No upcoming matches"
    }

    private fun showMatchesData() {
        binding.llAnime2.visibility                    = View.GONE
        binding.upcomingMatchesRecyclerView.visibility = View.VISIBLE
        binding.textViewNoMatches.visibility           = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        upcomingListener?.let {
            database.getReference("NoBallZone/upcomingMatches").removeEventListener(it)
        }
        _binding?.upcomingMatchesRecyclerView?.adapter = null
        _binding = null
    }
}