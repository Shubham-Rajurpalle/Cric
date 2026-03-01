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
    private var isFragmentActive = false
    private var fragmentsInitialized = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentActive = true

        if (!fragmentsInitialized) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.add(binding.hitmissContainer.id, hitsFragment(), "hits")
            transaction.add(binding.hitmissContainer.id, missFragment(), "miss")
            transaction.hide(childFragmentManager.findFragmentByTag("miss") ?: missFragment())
            transaction.commitNow()
            fragmentsInitialized = true
        }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (!isFragmentActive) return
                val transaction = childFragmentManager.beginTransaction()
                when (tab?.position) {
                    0 -> {
                        childFragmentManager.findFragmentByTag("hits")?.let { transaction.show(it) }
                        childFragmentManager.findFragmentByTag("miss")?.let { transaction.hide(it) }
                    }
                    1 -> {
                        childFragmentManager.findFragmentByTag("miss")?.let { transaction.show(it) }
                        childFragmentManager.findFragmentByTag("hits")?.let { transaction.hide(it) }
                    }
                }
                transaction.commitAllowingStateLoss()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onPause() { super.onPause(); isFragmentActive = false }
    override fun onResume() { super.onResume(); isFragmentActive = true }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        _binding = null
    }
}