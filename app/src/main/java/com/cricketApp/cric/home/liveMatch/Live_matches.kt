package com.cricketApp.cric.home.liveMatch

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.adapter.LiveMatchAdapter
import com.cricketApp.cric.databinding.FragmentLiveMatchesBinding

class Live_matches : Fragment() {
    private var _binding: FragmentLiveMatchesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MatchViewModel
    private lateinit var adapter: LiveMatchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveMatchesBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[MatchViewModel::class.java]

        setupRecyclerView()
        observeLiveMatches()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = LiveMatchAdapter(mutableListOf()) // Pass mutable list to allow updates
        binding.liveMatchesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.liveMatchesRecyclerView.adapter = adapter
    }

    private fun observeLiveMatches() {
        viewModel.matches.observe(viewLifecycleOwner) { matches ->
            if (matches.isNullOrEmpty()) {
                Log.d("LiveMatches", "No live matches available")
            }
            adapter.updateData(matches)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
