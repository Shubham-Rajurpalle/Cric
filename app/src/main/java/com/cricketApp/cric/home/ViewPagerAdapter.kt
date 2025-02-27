package com.cricketApp.cric.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cricketApp.cric.home.Shots.Cric_shots
import com.cricketApp.cric.home.UpcomingMatches.upcoming_matches
import com.cricketApp.cric.home.liveMatch.live_matches

class ViewPagerAdapter(activity: HomeFragment):FragmentStateAdapter(activity) {
    private val fragments= listOf(Cric_shots(), live_matches(), upcoming_matches())
    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}