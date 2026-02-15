package com.cricketApp.cric.home.upcomingMatch

import android.os.Bundle
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
    private var isFragmentActive = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpcomingMatchesBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[UpcomingMatchViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentActive = true

        setupRecyclerView()
        setupSwipeToRefresh()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = UpcomingMatchAdapter(mutableListOf())
        binding.upcomingMatchesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.upcomingMatchesRecyclerView.adapter = adapter
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.reloadMatches()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isFragmentActive) return@observe

            if (isLoading) {
                showLoading()
            } else {
                binding.llAnime2.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        viewModel.matches.observe(viewLifecycleOwner) { matches ->
            if (!isFragmentActive) return@observe

            if (matches.isNullOrEmpty()) {
                binding.textViewNoMatches.visibility = View.VISIBLE
                binding.upcomingMatchesRecyclerView.visibility = View.GONE
            } else {
                binding.textViewNoMatches.visibility = View.GONE
                binding.upcomingMatchesRecyclerView.visibility = View.VISIBLE
                adapter.updateData(matches)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (!isFragmentActive) return@observe

            binding.textViewNoMatches.text = errorMsg
            binding.textViewNoMatches.visibility = View.VISIBLE
            binding.upcomingMatchesRecyclerView.visibility = View.GONE
        }
    }

    private fun showLoading() {
        binding.llAnime2.visibility = View.VISIBLE
        binding.textViewNoMatches.visibility = View.GONE
        binding.upcomingMatchesRecyclerView.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        binding.upcomingMatchesRecyclerView.adapter = null
        _binding = null
    }
}