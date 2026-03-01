package com.cricketApp.cric.home.liveMatch

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MatchRepository {

    private val database = FirebaseDatabase.getInstance()
    private val liveRoomsRef = database.getReference("NoBallZone/liveRooms")
    private var liveListener: ValueEventListener? = null

    private val activeStatuses = setOf(
        "Live", "Delayed", "Innings Break",
        "Lunch", "Tea", "Rain", "1st Innings", "2nd Innings"
    )

    fun listenToLiveMatches(
        onResult: (List<MatchData>) -> Unit,
        onError: (String) -> Unit
    ) {
        liveListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val matches = mutableListOf<MatchData>()

                for (roomSnapshot in snapshot.children) {
                    val matchName = roomSnapshot.key ?: continue
                    val scores = roomSnapshot.child("scores")
                    if (!scores.exists()) continue

                    val isLive = scores.child("live").getValue(Boolean::class.java) ?: false
                    val status = scores.child("status").getValue(String::class.java) ?: ""
                    if (!isLive && status !in activeStatuses) continue

                    matches.add(MatchData(
                        matchId    = scores.child("matchId").getValue(String::class.java) ?: "",
                        matchName  = matchName,
                        status     = status,
                        note       = scores.child("note").getValue(String::class.java) ?: "",
                        live       = isLive,
                        type       = scores.child("type").getValue(String::class.java) ?: "",
                        round      = scores.child("round").getValue(String::class.java) ?: "",
                        startingAt = scores.child("startingAt").getValue(String::class.java) ?: "",
                        updatedAt  = scores.child("updatedAt").getValue(String::class.java) ?: "",
                        league = LeagueData(
                            id        = scores.child("league/id").getValue(String::class.java) ?: "",
                            name      = scores.child("league/name").getValue(String::class.java) ?: "",
                            imagePath = scores.child("league/imagePath").getValue(String::class.java) ?: ""
                        ),
                        stage = StageData(
                            id   = scores.child("stage/id").getValue(String::class.java) ?: "",
                            name = scores.child("stage/name").getValue(String::class.java) ?: ""
                        ),
                        localteam = TeamData(
                            id        = scores.child("localteam/id").getValue(String::class.java) ?: "",
                            name      = scores.child("localteam/name").getValue(String::class.java) ?: "Team 1",
                            code      = scores.child("localteam/code").getValue(String::class.java) ?: "",
                            imagePath = scores.child("localteam/imagePath").getValue(String::class.java) ?: ""
                        ),
                        visitorteam = TeamData(
                            id        = scores.child("visitorteam/id").getValue(String::class.java) ?: "",
                            name      = scores.child("visitorteam/name").getValue(String::class.java) ?: "Team 2",
                            code      = scores.child("visitorteam/code").getValue(String::class.java) ?: "",
                            imagePath = scores.child("visitorteam/imagePath").getValue(String::class.java) ?: ""
                        ),
                        localteamScore = ScoreData(
                            runs    = scores.child("localteamScore/runs").getValue(Int::class.java) ?: 0,
                            wickets = scores.child("localteamScore/wickets").getValue(Int::class.java) ?: 0,
                            overs   = scores.child("localteamScore/overs").getValue(Double::class.java) ?: 0.0
                        ),
                        visitorteamScore = ScoreData(
                            runs    = scores.child("visitorteamScore/runs").getValue(Int::class.java) ?: 0,
                            wickets = scores.child("visitorteamScore/wickets").getValue(Int::class.java) ?: 0,
                            overs   = scores.child("visitorteamScore/overs").getValue(Double::class.java) ?: 0.0
                        )
                    ))
                }

                onResult(matches)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        liveRoomsRef.addValueEventListener(liveListener!!)
    }

    fun stopListening() {
        liveListener?.let { liveRoomsRef.removeEventListener(it) }
        liveListener = null
    }
}