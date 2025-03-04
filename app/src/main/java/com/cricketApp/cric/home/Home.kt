package com.cricketApp.cric.home

import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.ChatFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivityHomeBinding
import com.cricketApp.cric.Meme.memeFragment
import com.cricketApp.cric.Profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class Home : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        setupBottomNav()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHost)
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.lastOrNull()

                if (currentFragment !is HomeFragment) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.navHost, HomeFragment())
                        .addToBackStack(null)
                        .commit()

                    binding.bottomNavigation.post {
                        binding.bottomNavigation.selectedItemId = R.id.homeIcon
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            switchFragment(HomeFragment(), "Home")
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.itemIconTintList = null
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeIcon -> switchFragment(HomeFragment(), "Home")
                R.id.chatIcon -> switchFragment(ChatFragment(), "Chat")
                R.id.memeIcon -> switchFragment(memeFragment(), "Meme")
                R.id.leaderboardIcon -> switchFragment(LeaderboardFragment(), "Leaderboard")
                R.id.profileIcon -> switchFragment(ProfileFragment(), "Profile")
                else -> false
            }
            true
        }
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.findFragmentById(R.id.navHost)

        if (currentFragment != null && currentFragment.tag == tag) {
            return
        }

        val transaction = fragmentManager.beginTransaction()
        fragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

        transaction.replace(R.id.navHost, fragment, tag)
        if (tag != "Home") {
            transaction.addToBackStack("Home")
        }

        transaction.commit()
    }
}
