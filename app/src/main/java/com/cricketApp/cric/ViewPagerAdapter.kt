package com.cricketApp.cric

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: homeFragment):FragmentStateAdapter(activity) {
    private val fragments= listOf(cric_shots(),live_matches(),upcoming_matches())
    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}