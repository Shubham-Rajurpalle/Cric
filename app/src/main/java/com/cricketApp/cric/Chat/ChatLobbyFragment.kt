package com.cricketApp.cric.Chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cricketApp.cric.Leaderboard.LeaderboardFragment
import com.cricketApp.cric.LogIn.SignIn
import com.cricketApp.cric.Profile.ProfileFragment
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentChatLobbyBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatLobbyFragment : Fragment() {

    private var _binding: FragmentChatLobbyBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance()
    private val currentUser get() = FirebaseAuth.getInstance().currentUser

    // ── Static rooms ──────────────────────────────────────────────────────────

    private val globalRoom = listOf(
        ChatRoomItem(
            id = "global",
            name = "No Ball Zone",
            description = "Global cricket chat — everyone's welcome",
            type = RoomType.GLOBAL
        )
    )

    private val teamRooms = listOf(
        ChatRoomItem(id = "CSK",  name = "Chennai Super Kings",   description = "Whistle Podu! 🦁",          type = RoomType.TEAM, teamTag = "CSK"),
        ChatRoomItem(id = "MI",   name = "Mumbai Indians",        description = "One Family 💙",              type = RoomType.TEAM, teamTag = "MI"),
        ChatRoomItem(id = "RCB",  name = "Royal Challengers",     description = "Ee Sala Cup Namde! 🔴",     type = RoomType.TEAM, teamTag = "RCB"),
        ChatRoomItem(id = "KKR",  name = "Kolkata Knight Riders", description = "Korbo Lorbo Jeetbo! 💜",    type = RoomType.TEAM, teamTag = "KKR"),
        ChatRoomItem(id = "DC",   name = "Delhi Capitals",        description = "Roar Macha! 🔵",            type = RoomType.TEAM, teamTag = "DC"),
        ChatRoomItem(id = "GT",   name = "Gujarat Titans",        description = "Aava De! 🔷",               type = RoomType.TEAM, teamTag = "GT"),
        ChatRoomItem(id = "LSG",  name = "Lucknow Super Giants",  description = "Ab Apna Time Aayega! 🩵",  type = RoomType.TEAM, teamTag = "LSG"),
        ChatRoomItem(id = "PBKS", name = "Punjab Kings",          description = "Sada Punjab! 🔴",           type = RoomType.TEAM, teamTag = "PBKS"),
        ChatRoomItem(id = "RR",   name = "Rajasthan Royals",      description = "Halla Bol! 🩷",             type = RoomType.TEAM, teamTag = "RR"),
        ChatRoomItem(id = "SRH",  name = "Sunrisers Hyderabad",   description = "Orange Army! 🧡",           type = RoomType.TEAM, teamTag = "SRH")
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatLobbyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopBar()
        setupGlobalRoom()
        setupTeamRooms()
        observeLiveRooms()
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private fun setupTopBar() {
        // Profile photo
        currentUser?.uid?.let { uid ->
            database.getReference("Users/$uid/profilePhoto")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isAdded || _binding == null) return
                        val url = snapshot.getValue(String::class.java)
                        if (!url.isNullOrEmpty()) {
                            context?.let { ctx ->
                                Glide.with(ctx).load(url)
                                    .placeholder(R.drawable.profile_icon)
                                    .into(binding.profilePhoto)
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        } ?: run {
            binding.profilePhoto.setImageResource(R.drawable.profile_icon)
        }

        binding.profilePhoto.setOnClickListener {
            if (currentUser == null) {
                startActivity(Intent(requireContext(), SignIn::class.java))
                return@setOnClickListener
            }
            navigateTo(ProfileFragment(), R.id.profileIcon)
        }

        binding.leaderBoardIcon.setOnClickListener {
            navigateTo(LeaderboardFragment(), R.id.leaderboardIcon)
        }
    }

    // ── Room setup ────────────────────────────────────────────────────────────

    private fun setupGlobalRoom() {
        binding.rvGlobalRoom.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ChatLobbyAdapter(globalRoom) { room -> openRoom(room) }
        }
    }

    private fun setupTeamRooms() {
        binding.rvTeamRooms.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ChatLobbyAdapter(teamRooms) { room -> openRoom(room) }
        }
    }

    // ── Live rooms from Firebase ───────────────────────────────────────────────

    private fun observeLiveRooms() {
        database.getReference("NoBallZone/liveRooms")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || _binding == null) return

                    val liveRooms = mutableListOf<ChatRoomItem>()

                    for (matchSnapshot in snapshot.children) {
                        val scores = matchSnapshot.child("scores")
                        if (!scores.exists()) continue

                        // ── Only show active matches ───────────────────────────
                        val isLive = scores.child("live").getValue(Boolean::class.java) ?: false
                        val status = scores.child("status").getValue(String::class.java) ?: ""

                        val activeStatuses = listOf(
                            "Live", "Delayed", "Innings Break",
                            "Lunch", "Tea", "Rain",
                            "1st Innings", "2nd Innings"
                        )

                        // Skip if backend marked it finished (live=false)
                        // but still show if status is active (Delayed, Rain etc.)
                        if (!isLive && status !in activeStatuses) continue

                        // ── Team info ─────────────────────────────────────────
                        val localTeamName   = scores.child("localteam").child("name")
                            .getValue(String::class.java) ?: "Home"
                        val visitorTeamName = scores.child("visitorteam").child("name")
                            .getValue(String::class.java) ?: "Away"

                        // ── Banner: league logo → local logo → visitor logo ───
                        val leagueImagePath  = scores.child("league").child("imagePath")
                            .getValue(String::class.java) ?: ""
                        val localImagePath   = scores.child("localteam").child("imagePath")
                            .getValue(String::class.java) ?: ""
                        val visitorImagePath = scores.child("visitorteam").child("imagePath")
                            .getValue(String::class.java) ?: ""

                        val bannerImageUrl = when {
                            leagueImagePath.isNotEmpty()  -> leagueImagePath
                            localImagePath.isNotEmpty()   -> localImagePath
                            visitorImagePath.isNotEmpty() -> visitorImagePath
                            else -> ""
                        }

                        // ── Scores ────────────────────────────────────────────
                        val localRuns    = scores.child("localteamScore").child("runs")
                            .getValue(Int::class.java) ?: 0
                        val localWickets = scores.child("localteamScore").child("wickets")
                            .getValue(Int::class.java) ?: 0
                        val localOvers   = scores.child("localteamScore").child("overs")
                            .getValue(Double::class.java) ?: 0.0

                        val visitorRuns    = scores.child("visitorteamScore").child("runs")
                            .getValue(Int::class.java) ?: 0
                        val visitorWickets = scores.child("visitorteamScore").child("wickets")
                            .getValue(Int::class.java) ?: 0
                        val visitorOvers   = scores.child("visitorteamScore").child("overs")
                            .getValue(Double::class.java) ?: 0.0

                        // ── Match info ────────────────────────────────────────
                        val note      = scores.child("note").getValue(String::class.java)  ?: ""
                        val round     = scores.child("round").getValue(String::class.java) ?: ""
                        val type      = scores.child("type").getValue(String::class.java)  ?: ""
                        val matchName = scores.child("matchName").getValue(String::class.java)
                            ?: matchSnapshot.key ?: ""

                        // ── Description ───────────────────────────────────────
                        val description = buildString {
                            if (type.isNotEmpty())  append(type)
                            if (round.isNotEmpty()) append(" • $round")

                            // Prominent status for non-live states
                            if (status.isNotEmpty() && status != "Live") {
                                append("\n⚠️ $status")
                            }

                            // Rain/delay note
                            if (note.isNotEmpty()) {
                                append("\n$note")
                            }

                            // Scores only if match has started
                            if (localRuns > 0 || localWickets > 0) {
                                append("\n$localTeamName: $localRuns/$localWickets (${localOvers} ov)")
                            }
                            if (visitorRuns > 0 || visitorWickets > 0) {
                                append("\n$visitorTeamName: $visitorRuns/$visitorWickets (${visitorOvers} ov)")
                            }
                        }

                        val room = ChatRoomItem(
                            id             = matchSnapshot.key ?: continue,
                            name           = matchName,
                            description    = description,
                            type           = RoomType.LIVE,
                            bannerImageUrl = bannerImageUrl,
                            isLive         = isLive,
                            activeUsers    = matchSnapshot.child("activeUsers")
                                .getValue(Int::class.java) ?: 0,
                            createdAt      = System.currentTimeMillis()
                        )
                        liveRooms.add(room)
                    }

                    liveRooms.sortByDescending { it.createdAt }

                    if (liveRooms.isEmpty()) {
                        binding.sectionLive.visibility = View.GONE
                    } else {
                        binding.sectionLive.visibility = View.VISIBLE
                        binding.rvLiveRooms.apply {
                            layoutManager = LinearLayoutManager(context)
                            adapter = ChatLobbyAdapter(liveRooms) { room -> openRoom(room) }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("LIVEROOMS", "error: ${error.message}")
                }
            })
    }

    /**
     * Maps full team names from the API to your drawable/teamTag identifiers.
     * Add more mappings as needed based on what SportMonk returns.
     */
    private fun resolveTeamTag(teamName: String): String {
        val name = teamName.uppercase()
        return when {
            name.contains("CHENNAI")    -> "CSK"
            name.contains("MUMBAI")     -> "MI"
            name.contains("BANGALORE") || name.contains("ROYAL CHALLENGERS") -> "RCB"
            name.contains("KOLKATA")    -> "KKR"
            name.contains("DELHI")      -> "DC"
            name.contains("GUJARAT")    -> "GT"
            name.contains("LUCKNOW")    -> "LSG"
            name.contains("PUNJAB")     -> "PBKS"
            name.contains("RAJASTHAN")  -> "RR"
            name.contains("HYDERABAD") || name.contains("SUNRISERS") -> "SRH"
            name.contains("INDIA")      -> "IND"
            name.contains("AUSTRALIA")  -> "AUS"
            name.contains("ENGLAND")    -> "ENG"
            name.contains("PAKISTAN")   -> "PAK"
            name.contains("SOUTH AFRICA") -> "SA"
            name.contains("NEW ZEALAND") -> "NZ"
            name.contains("SRI LANKA")  -> "SL"
            name.contains("WEST INDIES") -> "WI"
            name.contains("BANGLADESH") -> "BAN"
            name.contains("AFGHANISTAN") -> "AFG"
            else -> ""
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Open ChatFragment, passing the selected room's id and type.
     * ChatFragment uses these args to point to the correct Firebase path.
     */
    private fun openRoom(room: ChatRoomItem) {
        val args = Bundle().apply {
            putString("ROOM_ID", room.id)
            putString("ROOM_TYPE", room.type.name)
            putString("ROOM_NAME", room.name)
        }
        val chatFragment = ChatFragment().apply { arguments = args }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.navHost, chatFragment, "ChatRoom")
            .commitAllowingStateLoss()
    }

    private fun navigateTo(fragment: Fragment, navItemId: Int) {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
            .selectedItemId = navItemId
        parentFragmentManager.beginTransaction()
            .replace(R.id.navHost, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}