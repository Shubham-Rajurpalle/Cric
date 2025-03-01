package com.cricketApp.cric.Leaderboard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.FragmentHitsBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class hitsFragment : Fragment() {
    private var _binding: FragmentHitsBinding? = null
    private val binding get() = _binding!!

    private lateinit var databaseRef: DatabaseReference
    private lateinit var adapter: HitsLeaderboardAdapter
    private val allTeams = mutableListOf<TeamData>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference("teams")

        // Initialize RecyclerView
        binding.leaderboardList.layoutManager = LinearLayoutManager(requireContext())
        adapter = HitsLeaderboardAdapter()
        binding.leaderboardList.adapter = adapter

        // Load data
        loadLeaderboardData()
    }

    private fun loadLeaderboardData() {
        databaseRef.orderByChild("hits").limitToLast(10).addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTeams.clear()

                for (dataSnapshot in snapshot.children) {
                    val team = dataSnapshot.getValue(TeamData::class.java)
                    if (team != null) {
                        allTeams.add(team)
                    }
                }

                // Sort in descending order by hits
                allTeams.sortByDescending { it.hits }

                updateUI()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI() {
        if (allTeams.size >= 3) {
            val firstPlace = allTeams[0]
            val secondPlace = allTeams[1]
            val thirdPlace = allTeams[2]

            binding.firstTeam.text = firstPlace.name
            binding.secondTeam.text = secondPlace.name
            binding.thirdTeam.text = thirdPlace.name
            binding.firstHits.text = firstPlace.hits.toString()
            binding.secondHits.text = secondPlace.hits.toString()
            binding.thirdHits.text = thirdPlace.hits.toString()

            Glide.with(this)
                .load(firstPlace.logoUrl)
                .into(binding.firstTeamLogo)

            Glide.with(this)
                .load(secondPlace.logoUrl)
                .into(binding.secondTeamLogo)

            Glide.with(this)
                .load(thirdPlace.logoUrl)
                .into(binding.thirdTeamLogo)

            adapter.submitList(allTeams.subList(3, allTeams.size))
        } else {
            adapter.submitList(allTeams)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}