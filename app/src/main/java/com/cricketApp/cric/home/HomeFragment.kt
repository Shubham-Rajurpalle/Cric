package com.cricketApp.cric.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentHomeBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: FirebaseDatabase
    private var currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User successfully logged in
            currentUser = FirebaseAuth.getInstance().currentUser
            loadProfilePhoto()
            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabLayout()
        database = FirebaseDatabase.getInstance()

        binding.profilePhoto.setOnClickListener {
            if (!isUserLoggedIn()) {
                showLoginPrompt("Login to view your profile")
                return@setOnClickListener
            }

            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.profileIcon
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.navHost, ProfileFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }
    }
    private fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    private fun showLoginPrompt(message: String) {
        AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogTheme)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(requireContext(), SignIn::class.java)
                // Use the activity result launcher instead of deprecated startActivityForResult
                loginLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadProfilePhoto(){
        val userId=currentUser?.uid?:return
        val userRef = database.getReference("Users/$userId/profilePhoto")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val photoUrl=snapshot.getValue(String::class.java)
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(context ?: return)
                        .load(photoUrl)
                        .placeholder(R.drawable.profile_icon)
                        .into(binding.profilePhoto)
                } else {
                    Log.e("Profile", "No profile photo found")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatFragment", "Error loading profile photo", error.toException())
            }
        })
    }

    private fun setupTabLayout() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = true

        TabLayoutMediator(binding.tabsHomePage, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "CricShots"
                1 -> "Live"
                2 -> "Upcoming"
                else -> "CricShots"
            }
        }.attach()
    }
}
