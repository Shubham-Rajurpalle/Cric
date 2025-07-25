package com.cricketApp.cric.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cricketApp.cric.home.Shots.Cric_shots
import com.cricketApp.cric.home.liveMatch.Live_matches
import com.cricketApp.cric.home.upcomingMatch.Upcoming_matches

class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> Cric_shots()
            1 -> Live_matches()
            2 -> Upcoming_matches()
            else -> Cric_shots()
        }
    }
}
