package com.cricketApp.cric.home.UpcomingMatches


import android.util.Log
import com.google.firebase.database.*

object FirebaseConfigUpcoming {
    private val database = FirebaseDatabase.getInstance()
    private val configRef = database.getReference("apiConfigUpcoming")

    var authToken: String? = null
    var baseUrl: String? = null
    var upcomingMatchesEndpoint: String? = null

    fun loadConfig(onComplete: () -> Unit) {
        configRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                authToken = snapshot.child("authToken").getValue(String::class.java)
                baseUrl = snapshot.child("baseUrl").getValue(String::class.java)
                upcomingMatchesEndpoint = snapshot.child("upcomingMatchesEndpoint").getValue(String::class.java)
                onComplete()
            }

            override fun onCancelled(error: DatabaseError) {
            //    Log.e("FirebaseConfig", "Failed to load API config: ${error.message}")
            }
        })
    }
}
