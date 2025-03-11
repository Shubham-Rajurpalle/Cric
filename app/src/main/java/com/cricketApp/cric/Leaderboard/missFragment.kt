package com.cricketApp.cric.Leaderboard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cricketApp.cric.databinding.FragmentMissBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class missFragment : Fragment() {
    private var _binding: FragmentMissBinding? = null
    private val binding get() = _binding!!

    private lateinit var databaseRef: DatabaseReference
    private lateinit var adapter: MissLeaderboardAdapter
    private val allTeams = mutableListOf<TeamData>()

    // Add a flag to track whether the fragment is attached
    private var isFragmentActive = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMissBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set flag to true when fragment is active
        isFragmentActive = true

        // Initialize Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference("teams")

        // Initialize RecyclerView
        binding.leaderboardList.layoutManager = LinearLayoutManager(requireContext())
        adapter = MissLeaderboardAdapter()
        binding.leaderboardList.adapter = adapter

        loadLeaderboardData()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Set flag to false when fragment view is destroyed
        isFragmentActive = false
        _binding = null
    }

    private fun loadLeaderboardData() {
        databaseRef.orderByChild("misses").limitToLast(10).addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if fragment is still active before processing data
                if (!isFragmentActive) return

                allTeams.clear()

                for (dataSnapshot in snapshot.children) {
                    val team = dataSnapshot.getValue(TeamData::class.java)
                    if (team != null) {
                        allTeams.add(team)
                    }
                }

                // Sort in descending order by misses
                allTeams.sortByDescending { it.misses }

                updateUI()
            }

            override fun onCancelled(error: DatabaseError) {
                // Check if fragment is still active before showing toast
                if (!isFragmentActive) return

                Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI() {
        // Check if binding is null or fragment is not active
        if (_binding == null || !isFragmentActive) return

        if (allTeams.size >= 3) {
            val firstPlace = allTeams[0]
            val secondPlace = allTeams[1]
            val thirdPlace = allTeams[2]

            binding.firstTeam.text = firstPlace.name
            binding.secondTeam.text = secondPlace.name
            binding.thirdTeam.text = thirdPlace.name
            binding.firstMisses.text = firstPlace.misses.toString()
            binding.secondMisses.text = secondPlace.misses.toString()
            binding.thirdMisses.text = thirdPlace.misses.toString()

            try {
                // Use requireView() to ensure we only load images when the view exists
                Glide.with(requireView())
                    .load(firstPlace.logoUrl)
                    .into(binding.firstTeamLogo)

                Glide.with(requireView())
                    .load(secondPlace.logoUrl)
                    .into(binding.secondTeamLogo)

                Glide.with(requireView())
                    .load(thirdPlace.logoUrl)
                    .into(binding.thirdTeamLogo)
            } catch (e: Exception) {
                // Handle any exceptions that might occur during image loading
                e.printStackTrace()
            }

            adapter.submitList(allTeams.subList(3, allTeams.size))
        } else {
            adapter.submitList(allTeams)
        }
    }
}