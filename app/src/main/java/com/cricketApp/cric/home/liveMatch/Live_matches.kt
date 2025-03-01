package com.cricketApp.cric.home.liveMatch

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
        setupSwipeRefresh()
        observeViewModel()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = LiveMatchAdapter(mutableListOf())
        binding.liveMatchesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.liveMatchesRecyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        // If you have a SwipeRefreshLayout in your fragment layout
        binding.root.findViewById<SwipeRefreshLayout>(
            binding.root.resources.getIdentifier("swipe_refresh", "id", requireContext().packageName)
        )?.setOnRefreshListener {
            viewModel.refreshMatches()
        }
    }

    private fun observeViewModel() {
        // Observe matches data
        viewModel.matches.observe(viewLifecycleOwner) { matches ->
            if (matches.isNullOrEmpty()) {
                Log.d("LiveMatches", "No live matches available")
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.root.findViewById<View>(
                    binding.root.resources.getIdentifier("empty_state", "id", requireContext().packageName)
                )?.visibility = View.GONE
            }
            adapter.updateData(matches)
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.root.findViewById<View>(
                binding.root.resources.getIdentifier("progress_bar", "id", requireContext().packageName)
            )?.visibility = if (isLoading) View.VISIBLE else View.GONE

            // If you have a SwipeRefreshLayout, update its refresh state
            binding.root.findViewById<SwipeRefreshLayout>(
                binding.root.resources.getIdentifier("swipe_refresh", "id", requireContext().packageName)
            )?.isRefreshing = isLoading
        }

        // Observe error state
        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}