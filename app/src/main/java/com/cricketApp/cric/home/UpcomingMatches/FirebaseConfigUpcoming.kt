package com.cricketApp.cric.home.UpcomingMatches

import android.util.Log
import com.google.firebase.database.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object FirebaseConfigUpcoming {
    private val database = FirebaseDatabase.getInstance()
    private val configRef = database.getReference("apiConfigUpcoming")

    @Volatile
    var authToken: String? = null
        private set

    @Volatile
    var baseUrl: String? = null
        private set

    @Volatile
    var upcomingMatchesEndpoint: String? = null
        private set

    @Volatile
    private var isLoaded = false
    private val loadLock = Any()

    fun loadConfig(onComplete: () -> Unit) {
        // Check if already loaded
        if (isLoaded && authToken != null && baseUrl != null) {
            onComplete()
            return
        }

        synchronized(loadLock) {
            // Check again in case another thread loaded while we were waiting
            if (isLoaded && authToken != null && baseUrl != null) {
                onComplete()
                return
            }

            configRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    authToken = snapshot.child("authToken").getValue(String::class.java)
                    baseUrl = snapshot.child("baseUrl").getValue(String::class.java)
                    upcomingMatchesEndpoint = snapshot.child("upcomingMatchesEndpoint").getValue(String::class.java)

                    isLoaded = true
                    onComplete()
                }

                override fun onCancelled(error: DatabaseError) {
                    // Provide default fallback values
                    if (authToken == null) authToken = "your_default_token" // Replace with a default token
                    if (baseUrl == null) baseUrl = "https://cricket.sportmonks.com/api/v2.0/"
                    if (upcomingMatchesEndpoint == null) upcomingMatchesEndpoint = "fixtures"

                    isLoaded = true
                    onComplete()
                }
            })
        }
    }
}