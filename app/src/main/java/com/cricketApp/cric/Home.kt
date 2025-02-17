package com.cricketApp.cric

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.cricketApp.cric.databinding.ActivityHomeBinding
import com.google.android.material.tabs.TabLayoutMediator

class Home : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState==null){
            switchFragment(homeFragment(),"Home")
        }
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.itemIconTintList = null
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeIcon -> switchFragment(homeFragment(), "Home")
                R.id.chatIcon -> switchFragment(chatFragment(), "Chat")
                R.id.memeIcon -> switchFragment(memeFragment(), "Meme")
                R.id.leaderboardIcon -> switchFragment(leaderboardFragment(), "Leaderboard")
                R.id.profileIcon -> switchFragment(profileFragment(), "Profile")
                else -> false
            }
            true
        }
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        val fragmentManager = supportFragmentManager
        val currentFragment=fragmentManager.findFragmentById(R.id.navHost)

        if(currentFragment!=null&&currentFragment.tag==tag){
            return
        }

        val transaction=fragmentManager.beginTransaction()

        if(tag=="Home"){
            fragmentManager.popBackStack(null,androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            transaction.replace(R.id.navHost,fragment,tag)
        }else{
            fragmentManager.popBackStack(null,androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            transaction.replace(R.id.navHost,fragment,tag)
            transaction.addToBackStack("Home")
        }

        transaction.commit()
    }

    override fun onBackPressed() {
        val fragmentManager = supportFragmentManager

        // Check if we are not already on the home fragment
        if (fragmentManager.backStackEntryCount > 0) {
            // Simulate clicking on the "Home" icon by resetting the selected item
            binding.bottomNavigation.selectedItemId = R.id.homeIcon

            // Replace the current fragment with the Home fragment
            switchFragment(homeFragment(), "Home")
        } else {
            // If there's nothing in the back stack, use the default back press behavior
            super.onBackPressed()
        }
    }
}
