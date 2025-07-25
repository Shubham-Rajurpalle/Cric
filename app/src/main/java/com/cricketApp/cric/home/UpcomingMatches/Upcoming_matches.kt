package com.cricketApp.cric.home.upcomingMatch

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.adapter.UpcomingMatchAdapter
import com.cricketApp.cric.databinding.FragmentUpcomingMatchesBinding

class Upcoming_matches : Fragment() {
    private var _binding: FragmentUpcomingMatchesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: UpcomingMatchViewModel
    private lateinit var adapter: UpcomingMatchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUpcomingMatchesBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[UpcomingMatchViewModel::class.java]

        setupRecyclerView()
        observeUpcomingMatches()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = UpcomingMatchAdapter(mutableListOf())
        binding.upcomingMatchesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.upcomingMatchesRecyclerView.adapter = adapter
    }

    private fun observeUpcomingMatches() {
        viewModel.matches.observe(viewLifecycleOwner) { matches ->
            adapter.updateData(matches ?: emptyList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
