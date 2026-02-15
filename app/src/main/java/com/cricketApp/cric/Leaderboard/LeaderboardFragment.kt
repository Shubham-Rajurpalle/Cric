package com.cricketApp.cric.Leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cricketApp.cric.databinding.FragmentLeaderboardBinding
import com.google.android.material.tabs.TabLayout

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!

    // Map to keep track of all child fragments
    private val childFragments = mutableMapOf<Int, Fragment>()

    // Flag to track if fragment is attached to prevent memory leaks
    private var isFragmentActive = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set flag to true when fragment is active
        isFragmentActive = true

        // Create child fragments only once and store them
        if (childFragments.isEmpty()) {
            childFragments[0] = hitsFragment()
            childFragments[1] = missFragment()
        }

        // Show default fragment
        replaceFragment(childFragments[0]!!)

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Check if fragment is still attached
                if (!isFragmentActive) return

                val position = tab?.position ?: 0
                val fragment = childFragments[position] ?: return
                replaceFragment(fragment)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        // Only proceed if fragment is active
        if (!isFragmentActive) return

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(binding.hitmissContainer.id, fragment, fragment::class.java.simpleName)
        transaction.commitAllowingStateLoss()  // Use commitAllowingStateLoss to prevent IllegalStateException
    }

    override fun onPause() {
        super.onPause()

        // Make sure we don't commit transactions after onSaveInstanceState
        isFragmentActive = false
    }

    override fun onResume() {
        super.onResume()

        // Reactivate fragment
        isFragmentActive = true
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Set the flag to false when view is destroyed
        isFragmentActive = false

        // Remove all child fragments explicitly
        if (!childFragmentManager.isDestroyed) {
            val transaction = childFragmentManager.beginTransaction()
            for (fragment in childFragmentManager.fragments) {
                transaction.remove(fragment)
            }
            transaction.commitAllowingStateLoss()
        }

        // Clear binding reference
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clear child fragments map
        childFragments.clear()
    }
}