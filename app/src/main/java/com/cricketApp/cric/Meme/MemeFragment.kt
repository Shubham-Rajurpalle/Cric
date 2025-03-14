package com.cricketApp.cric.Meme

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cricketApp.cric.Chat.CommentActivity
import com.cricketApp.cric.Chat.FirebaseDataHelper
import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentMemeBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch

class MemeFragment : Fragment() {
    private lateinit var binding: FragmentMemeBinding
    private lateinit var memeAdapter: MemeAdapter
    private val memes = mutableListOf<MemeMessage>()
    private var selectedImageUri: Uri? = null
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private var userTeam: String = "CSK" // Default team, will be updated from user profile
    private lateinit var safetyChecker: CloudVisionSafetyChecker

    // Map to keep track of meme positions for efficient updates
    private val memePositions = mutableMapOf<String, Int>()

    // Selected filters
    private var selectedFilter = mutableSetOf<String>()

    private val PICK_MEME_REQUEST = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMemeBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val height = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation).height
            val bottomInsets = if (imeVisible) {
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - height
            } else {
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            }

            view.setPadding(0, 0, 0, bottomInsets)
            insets
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize safety checker
        safetyChecker = CloudVisionSafetyChecker(requireContext())

        // Load profile photo
        loadProfilePhoto()

        // Fetch user's team
        fetchUserTeam()

        // Setup RecyclerView
        memeAdapter = MemeAdapter(memes) { meme ->
            openMemeComments(meme)
        }

        binding.recyclerViewMemes.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true)
            (layoutManager as LinearLayoutManager).stackFromEnd = true
            adapter = memeAdapter
        }

        // Setup filters
        setupFilters()

        // Setup Firebase listeners
        setupFirebaseListeners()

        // Setup meme upload button
        binding.buttonUploadMeme.setOnClickListener {
            openMemePicker()
        }

        // Setup navigation buttons
        binding.leaderBoardIcon.setOnClickListener {
            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.leaderboardIcon
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.navHost, LeaderboardFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }

        binding.profilePhoto.setOnClickListener {
            val bottomNavigation: BottomNavigationView = requireActivity().findViewById(R.id.bottomNavigation)
            bottomNavigation.selectedItemId = R.id.profileIcon
            val fragmentManager = parentFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.navHost, ProfileFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }
    }

    private fun loadProfilePhoto() {
        val userId = currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/profilePhoto")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val photoUrl = snapshot.getValue(String::class.java)
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
                Log.e("MemeFragment", "Error loading profile photo", error.toException())
            }
        })
    }

    private fun fetchUserTeam() {
        currentUser?.uid?.let { userId ->
            val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId/iplTeam")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userTeam = snapshot.getValue(String::class.java) ?: "No Team"
                }

                override fun onCancelled(error: DatabaseError) {
                    // Keep default team
                }
            })
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { resetFilters() }
        binding.chipTopHits.setOnClickListener { toggleFilter("TopHits") { loadTopHitMemes() } }
        binding.chipTopMiss.setOnClickListener { toggleFilter("TopMiss") { loadTopMissMemes() } }
        binding.chipCSK.setOnClickListener { toggleFilter("CSK") { loadTeamMemes("CSK") } }
        binding.chipMI.setOnClickListener { toggleFilter("MI") { loadTeamMemes("MI") } }
        binding.chipDC.setOnClickListener { toggleFilter("DC") { loadTeamMemes("DC") } }
        binding.chipGT.setOnClickListener { toggleFilter("GT") { loadTeamMemes("GT") } }
        binding.chipKKR.setOnClickListener { toggleFilter("KKR") { loadTeamMemes("KKR") } }
        binding.chipLSG.setOnClickListener { toggleFilter("LSG") { loadTeamMemes("LSG") } }
        binding.chipRCB.setOnClickListener { toggleFilter("RCB") { loadTeamMemes("RCB") } }
        binding.chipPBKS.setOnClickListener { toggleFilter("PBKS") { loadTeamMemes("PBKS") } }
        binding.chipRR.setOnClickListener { toggleFilter("RR") { loadTeamMemes("RR") } }
        binding.chipSRH.setOnClickListener { toggleFilter("SRH") { loadTeamMemes("SRH") } }

        binding.chipAll.isChecked = true
    }

    private fun toggleFilter(filter: String, action: () -> Unit) {
        val chip = when (filter) {
            "TopHits" -> binding.chipTopHits
            "TopMiss" -> binding.chipTopMiss
            "CSK" -> binding.chipCSK
            "MI" -> binding.chipMI
            "DC" -> binding.chipDC
            "GT" -> binding.chipGT
            "KKR" -> binding.chipKKR
            "LSG" -> binding.chipLSG
            "RCB" -> binding.chipRCB
            "PBKS" -> binding.chipPBKS
            "RR" -> binding.chipRR
            "SRH" -> binding.chipSRH
            else -> null
        }

        val isSelected = selectedFilter.contains(filter)

        // First, clear all existing filters and reset all chips
        selectedFilter.clear()

        // Reset visual state of all chips
        val allChips = listOf(
            binding.chipTopHits, binding.chipTopMiss,
            binding.chipCSK, binding.chipMI, binding.chipDC, binding.chipGT,
            binding.chipKKR, binding.chipLSG, binding.chipRCB, binding.chipPBKS,
            binding.chipRR, binding.chipSRH
        )

        allChips.forEach { c ->
            c.apply {
                isChecked = false
                chipStrokeWidth = 0f
                setTextColor(resources.getColor(R.color.white, null))
            }
        }

        // Set the All chip as not checked initially
        binding.chipAll.apply {
            isChecked = false
            chipStrokeWidth = 0f
            setTextColor(resources.getColor(R.color.white, null))
        }

        // If the same filter was already selected (double-click case), go back to "All"
        if (isSelected) {
            resetFilters()
            return
        }

        // Otherwise, add the new filter and update the chip
        selectedFilter.add(filter)
        chip?.apply {
            isChecked = true
            chipStrokeWidth = 2f
            setTextColor(resources.getColor(R.color.grey, null))
        }

        // Apply the filter
        if (selectedFilter.isEmpty()) {
            resetFilters()
        } else {
            action.invoke()
        }
    }

    private fun resetFilters() {
        // Clear the selected filters set
        selectedFilter.clear()

        // Set the All chip as checked
        binding.chipAll.apply {
            isChecked = true
            chipStrokeWidth = 2f
            setTextColor(resources.getColor(R.color.grey, null))
        }

        // Reset all other chips
        val allChips = listOf(
            binding.chipTopHits, binding.chipTopMiss,
            binding.chipCSK, binding.chipMI, binding.chipDC, binding.chipGT,
            binding.chipKKR, binding.chipLSG, binding.chipRCB, binding.chipPBKS,
            binding.chipRR, binding.chipSRH
        )

        allChips.forEach { chip ->
            chip.apply {
                isChecked = false
                chipStrokeWidth = 0f
                setTextColor(resources.getColor(R.color.white, null))
            }
        }

        // Clear existing memes and adapter state
        memes.clear()
        memePositions.clear()
        memeAdapter.notifyDataSetChanged()

        // Load all memes without filtering
        loadMemes()
    }

    private fun setupFirebaseListeners() {
        // We'll use different listeners based on filter status
        loadMemes()
    }

    private fun loadMemes() {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Setup child event listener for real-time updates
        setupMemeListener(memesRef)

        // Initial load
        loadInitialMemes()
    }

    private fun loadInitialMemes() {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        memesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by timestamp (newest first)
                tempMemes.sortByDescending { it.timestamp }

                // Make sure our lists are clear
                memes.clear()
                memePositions.clear()

                // Add all memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Notify adapter of changes
                memeAdapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (memes.isNotEmpty()) {
                    binding.recyclerViewMemes.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MemeFragment", "Error loading initial memes", error.toException())
            }
        })
    }

    private fun setupMemeListener(memesRef: com.google.firebase.database.DatabaseReference) {
        memesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Skip if already in our list
                val memeId = snapshot.key ?: return
                if (memePositions.containsKey(memeId)) return

                // Use helper method to properly read the meme
                val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(snapshot) ?: return

                // Add to the list (at the beginning for newest first)
                memes.add(0, meme)

                // Update positions map
                updatePositionsMap()

                memeAdapter.notifyItemInserted(0)
                binding.recyclerViewMemes.scrollToPosition(0)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val memeId = snapshot.key ?: return
                val position = memePositions[memeId] ?: return

                // Use helper method to properly read the updated meme
                val updatedMeme = FirebaseDataHelper.getMemeMessageFromSnapshot(snapshot) ?: return

                val currentMeme = memes[position]

                // Check what changed and update accordingly
                // Update reactions if they changed
                if (currentMeme.reactions != updatedMeme.reactions) {
                    currentMeme.reactions = updatedMeme.reactions
                    memeAdapter.notifyItemChanged(position, "reaction")
                }

                // Update hit/miss if they changed
                if (currentMeme.hit != updatedMeme.hit || currentMeme.miss != updatedMeme.miss) {
                    currentMeme.hit = updatedMeme.hit
                    currentMeme.miss = updatedMeme.miss
                    memeAdapter.notifyItemChanged(position, "hit_miss")
                }

                // Check for comment count change
                if (snapshot.hasChild("commentCount")) {
                    val commentCount = snapshot.child("commentCount").getValue(Int::class.java) ?: 0
                    if (currentMeme.commentCount != commentCount) {
                        currentMeme.commentCount = commentCount
                        memeAdapter.notifyItemChanged(position, "comments")
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val memeId = snapshot.key ?: return
                val position = memePositions[memeId] ?: return

                memes.removeAt(position)
                memePositions.remove(memeId)

                // Update positions map
                updatePositionsMap()

                memeAdapter.notifyItemRemoved(position)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("MemeFragment", "Error with meme listener", error.toException())
            }
        })
    }

    private fun updatePositionsMap() {
        // Update the positions map to reflect current positions in the list
        memePositions.clear()
        memes.forEachIndexed { index, meme ->
            memePositions[meme.id] = index
        }
    }

    private fun loadTopHitMemes() {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Load memes with high hits
        memesRef.orderByChild("hit").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by hit count (descending) and timestamp
                tempMemes.sortWith(compareByDescending<MemeMessage> { it.hit }
                    .thenByDescending { it.timestamp })

                // Clear existing memes
                memes.clear()
                memePositions.clear()

                // Add sorted memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Notify adapter
                memeAdapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (memes.isNotEmpty()) {
                    binding.recyclerViewMemes.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MemeFragment", "Error loading top hit memes", error.toException())
            }
        })
    }

    private fun loadTopMissMemes() {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Load memes with high misses
        memesRef.orderByChild("miss").startAt(1.0).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by miss count (descending) and timestamp
                tempMemes.sortWith(compareByDescending<MemeMessage> { it.miss }
                    .thenByDescending { it.timestamp })

                // Clear existing memes
                memes.clear()
                memePositions.clear()

                // Add sorted memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Notify adapter
                memeAdapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (memes.isNotEmpty()) {
                    binding.recyclerViewMemes.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MemeFragment", "Error loading top miss memes", error.toException())
            }
        })
    }

    private fun loadTeamMemes(team: String) {
        val memesRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes")

        // Clear existing data
        memes.clear()
        memePositions.clear()

        // Load memes for specific team
        memesRef.orderByChild("team").equalTo(team).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMemes = ArrayList<MemeMessage>()

                for (memeSnapshot in snapshot.children) {
                    val meme = FirebaseDataHelper.getMemeMessageFromSnapshot(memeSnapshot)
                    meme?.let {
                        tempMemes.add(it)
                    }
                }

                // Sort memes by timestamp (newest first)
                tempMemes.sortByDescending { it.timestamp }

                // Clear existing memes
                memes.clear()
                memePositions.clear()

                // Add sorted memes
                memes.addAll(tempMemes)

                // Update positions map
                updatePositionsMap()

                // Notify adapter
                memeAdapter.notifyDataSetChanged()

                // Scroll to top after loading
                if (memes.isNotEmpty()) {
                    binding.recyclerViewMemes.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MemeFragment", "Error loading team memes", error.toException())
            }
        })
    }

    private fun openMemePicker() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Meme"),
            PICK_MEME_REQUEST
        )
    }

    private fun showMemePreviewDialog() {
        // Create loading dialog
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .setTitle("Checking content safety...")
            .create()

        progressDialog.show()

        // Check image content safety using Cloud Vision API
        selectedImageUri?.let { uri ->
            // Using coroutines for the async API call
            lifecycleScope.launch {
                try {
                    val result = safetyChecker.checkImageSafety(uri)

                    // Process the result on main thread
                    progressDialog.dismiss()

                    if (result.isSafe) {
                        // Image is safe, proceed with upload
                        showUploadDialog()
                    } else if (result.autoBlock) {
                        // Image is NOT safe and should be automatically blocked
                        showContentBlockedDialog(result.issues)
                    } else {
                        // Image has potential issues but is not automatically blocked
                        showContentWarningDialog(result.issues)
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    Log.e("MemeFragment", "Error checking image safety: ${e.message}", e)
                    // Show error dialog
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Error")
                        .setMessage("Failed to analyze image: ${e.message}")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
        } ?: run {
            progressDialog.dismiss()
            Toast.makeText(context, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContentBlockedDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) {
            "This image has been blocked because our system detected prohibited content."
        } else {
            "This image has been blocked because our system detected:\n\n" +
                    issues.take(3).joinToString("\n")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Content Blocked")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
            }
            .show()
    }

    private fun showContentWarningDialog(issues: List<String>) {
        val message = if (issues.isEmpty()) {
            "This image may contain inappropriate content."
        } else {
            "This image may contain inappropriate content:\n\n" +
                    issues.take(3).joinToString("\n")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Content Warning")
            .setMessage(message)
            .setPositiveButton("Upload Anyway") { dialog, _ ->
                dialog.dismiss()
                showUploadDialog()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                selectedImageUri = null
            }
            .show()
    }

    private fun showUploadDialog() {
        // Skip caption dialog and directly show loading indicator
        val progressView = View.inflate(requireContext(), R.layout.loading_indicator, null)
        val progressIndicator = progressView.findViewById<CircularProgressIndicator>(R.id.progressIndicator)

        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .setTitle("Uploading meme...")
            .create()

        progressDialog.show()

        // Proceed with upload (empty caption)
        uploadAndPostMeme("") { success ->
            progressDialog.dismiss()
            if (!success) {
                Toast.makeText(context, "Meme upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadAndPostMeme(caption: String, onComplete: ((Boolean) -> Unit)? = null) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        selectedImageUri?.let { uri ->
            // Create a reference to the image file in Firebase Storage
            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("memes/${System.currentTimeMillis()}_${currentUser.uid}.jpg")

            // Upload the image
            imageRef.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    // Get the download URL
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        // Post meme with image
                        postMeme(caption, downloadUrl.toString())

                        // Clear selected image
                        selectedImageUri = null

                        // Callback success
                        onComplete?.invoke(true)
                    }
                }
                .addOnFailureListener { e ->
                    // Handle error
                    Log.e("MemeFragment", "Meme upload failed: ${e.message}")
                    onComplete?.invoke(false)
                }
        }
    }

    private fun postMeme(caption: String, imageUrl: String) {
        if (currentUser == null) {
            return
        }

        val memeRef = FirebaseDatabase.getInstance().getReference("NoBallZone/memes").push()
        val memeId = memeRef.key ?: return

        // Get user's display name and profile picture
        val userRef = FirebaseDatabase.getInstance().getReference("Users/${currentUser.uid}")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userName = snapshot.child("username").getValue(String::class.java)
                    ?: currentUser.displayName
                    ?: "Anonymous"
                val userTeam = snapshot.child("iplTeam").getValue(String::class.java)
                    ?: "No Team"

                val memeMessage = MemeMessage(
                    id = memeId,
                    senderId = currentUser.uid,
                    senderName = userName,
                    team = userTeam,
                    memeUrl = imageUrl,
                    timestamp = System.currentTimeMillis()
                )

                memeRef.setValue(memeMessage)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Meme posted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        // Handle error
                        Log.e("MemeFragment", "Error posting meme", it)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MemeFragment", "User data fetch cancelled", error.toException())
            }
        })
    }

    private fun openMemeComments(meme: MemeMessage) {
        val intent = Intent(context, CommentActivity::class.java).apply {
            putExtra("MESSAGE_ID", meme.id)
            putExtra("MESSAGE_TYPE", "meme")
        }
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_MEME_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            showMemePreviewDialog()
        }
    }
}