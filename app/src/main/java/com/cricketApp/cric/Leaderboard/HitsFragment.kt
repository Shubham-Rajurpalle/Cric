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

    private var databaseRef: DatabaseReference? = null
    private var valueEventListener: ValueEventListener? = null
    private lateinit var adapter: HitsLeaderboardAdapter
    private val allTeams = mutableListOf<TeamData>()


    // Add a flag to track whether the fragment is attached
    private var isFragmentActive = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHitsBinding.inflate(inflater, container, false)
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
        adapter = HitsLeaderboardAdapter()
        binding.leaderboardList.adapter = adapter

        // Load data
        loadLeaderboardData()
    }


    private fun loadLeaderboardData() {
        // If there's an existing listener, remove it first
        if (valueEventListener != null && databaseRef != null) {
            databaseRef?.removeEventListener(valueEventListener!!)
        }

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if fragment is still active before processing data
                if (!isFragmentActive || _binding == null) return

                allTeams.clear()

                for (dataSnapshot in snapshot.children) {
                    val team = dataSnapshot.getValue(TeamData::class.java)
                    team?.let {
                        // Ensure we have the key as the team ID
                        it.id = dataSnapshot.key ?: ""
                        allTeams.add(it)
                    }
                }

                // Sort in descending order by hits
                allTeams.sortByDescending { it.hits }

                updateUI()
            }

            override fun onCancelled(error: DatabaseError) {
                // Check if fragment is still active before showing toast
                if (!isFragmentActive || _binding == null) return

                Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        }

        // Use startAt(1) to only get teams with at least 1 hit
        databaseRef?.orderByChild("hits")?.startAt(1.0)?.addValueEventListener(valueEventListener!!)
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
            binding.firstHits.text = firstPlace.hits.toString()
            binding.secondHits.text = secondPlace.hits.toString()
            binding.thirdHits.text = thirdPlace.hits.toString()

            try {
                // Use context instead of requireView() for safer Glide loading
                context?.let { ctx ->
                    Glide.with(ctx)
                        .load(firstPlace.logoUrl)
                        .into(binding.firstTeamLogo)

                    Glide.with(ctx)
                        .load(secondPlace.logoUrl)
                        .into(binding.secondTeamLogo)

                    Glide.with(ctx)
                        .load(thirdPlace.logoUrl)
                        .into(binding.thirdTeamLogo)
                }
            } catch (e: Exception) {
                // Handle any exceptions that might occur during image loading
                e.printStackTrace()
            }

            adapter.submitList(allTeams.subList(3, allTeams.size))
        } else {
            adapter.submitList(allTeams)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Remove the Firebase listener to prevent memory leaks
        if (valueEventListener != null && databaseRef != null) {
            databaseRef?.removeEventListener(valueEventListener!!)
            valueEventListener = null
        }

        // Clear adapter reference
        binding.leaderboardList.adapter = null

        // Set flag to false when fragment view is destroyed
        isFragmentActive = false
        _binding = null
    }

}