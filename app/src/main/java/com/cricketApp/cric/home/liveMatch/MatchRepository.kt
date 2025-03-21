package com.cricketApp.cric.home.liveMatch

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class MatchRepository(private val context: Context) {
    private val api = RetrofitInstance.getApi()
    private val teamCache = ConcurrentHashMap<Int, Team>()
    private val leagueCache = ConcurrentHashMap<Int, League>()
    private val gson = Gson()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("cricket_app_cache", Context.MODE_PRIVATE)

    // Rate limiting - allow only 1 API call at a time with a 500ms delay between calls
    private val apiSemaphore = Semaphore(1)
    private val apiCallDelay = 500L // 500ms delay between API calls

    init {
        // Load cached data on initialization
        loadCachedData()
    }

    private fun loadCachedData() {
        // Load team cache
        val cachedTeams = sharedPrefs.getString("teams_cache", null)
        if (!cachedTeams.isNullOrEmpty()) {
            try {
                val teamsMap: Map<String, Team> = gson.fromJson(cachedTeams, Map::class.java) as Map<String, Team>
                teamsMap.forEach { (key, value) ->
                    teamCache[key.toInt()] = value
                }
                Log.d("CacheInfo", "Loaded ${teamCache.size} teams from cache")
            } catch (e: Exception) {
                Log.e("CacheError", "Failed to load teams cache: ${e.message}")
            }
        }

        // Load league cache
        val cachedLeagues = sharedPrefs.getString("leagues_cache", null)
        if (!cachedLeagues.isNullOrEmpty()) {
            try {
                val leaguesMap: Map<String, League> = gson.fromJson(cachedLeagues, Map::class.java) as Map<String, League>
                leaguesMap.forEach { (key, value) ->
                    leagueCache[key.toInt()] = value
                }
                Log.d("CacheInfo", "Loaded ${leagueCache.size} leagues from cache")
            } catch (e: Exception) {
                Log.e("CacheError", "Failed to load leagues cache: ${e.message}")
            }
        }
    }

    private fun saveCacheToPrefs() {
        // Save team cache
        val teamsJson = gson.toJson(teamCache)
        sharedPrefs.edit().putString("teams_cache", teamsJson).apply()

        // Save league cache
        val leaguesJson = gson.toJson(leagueCache)
        sharedPrefs.edit().putString("leagues_cache", leaguesJson).apply()

        Log.d("CacheInfo", "Saved ${teamCache.size} teams and ${leagueCache.size} leagues to cache")
    }

    // Wrapper for API calls with rate limiting
    private suspend fun <T> makeApiCall(call: () -> T): T? = withContext(Dispatchers.IO) {
        try {
            apiSemaphore.acquire()
            val result = call()
            delay(apiCallDelay) // Wait before releasing semaphore to pace API calls
            apiSemaphore.release()
            return@withContext result
        } catch (e: Exception) {
            apiSemaphore.release() // Make sure to release on error
            Log.e("API_ERROR", "API call failed: ${e.message}")
            return@withContext null
        }
    }

    fun fetchLiveMatches(onResult: (List<MatchData>?) -> Unit) {
        FirebaseConfig.loadConfig {
            val endpoint = FirebaseConfig.liveMatchesEndpoint
            val authToken = FirebaseConfig.authToken

            if (endpoint.isNullOrEmpty() || authToken.isNullOrEmpty()) {
                Log.e("API_ERROR", "Missing API endpoint or auth token")
                onResult(null)
                return@loadConfig
            }

            val call: Call<SportMonksResponse> = api.getLiveMatches(
                endpoint,
                authToken,
                // Request all the data we need in one call to reduce API requests
                "localteam,visitorteam,league,runs,stage"
            )

            call.enqueue(object : Callback<SportMonksResponse> {
                override fun onResponse(
                    call: Call<SportMonksResponse>,
                    response: Response<SportMonksResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val matches = response.body()?.data ?: emptyList()
                        Log.d("LiveMatches", "Received ${matches.size} matches")

                        if (matches.isNotEmpty()) {
                            // Process matches to ensure we have all needed data
                            completeMatchData(matches) { completedMatches ->
                                onResult(completedMatches)
                                // Save updated cache
                                saveCacheToPrefs()
                            }
                        } else {
                            onResult(emptyList())
                        }
                    } else {
                        Log.e("API_ERROR", "Response unsuccessful: ${response.errorBody()?.string()}")
                        onResult(null)
                    }
                }

                override fun onFailure(call: Call<SportMonksResponse>, t: Throwable) {
                    Log.e("API_ERROR", "Network call failed: ${t.message}")
                    onResult(null)
                }
            })
        }
    }

    private fun completeMatchData(matches: List<MatchData>, onComplete: (List<MatchData>) -> Unit) {
        // Use a persistent CoroutineScope
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val processedMatches = matches.toMutableList()

        // Enhance match data with cached information first
        for (i in matches.indices) {
            val match = matches[i]

            // Apply cached team data if available
            match.localteam?.let { team ->
                teamCache[team.id]?.let { cachedTeam ->
                    processedMatches[i] = processedMatches[i].copy(localteam = cachedTeam)
                }
            }

            match.visitorteam?.let { team ->
                teamCache[team.id]?.let { cachedTeam ->
                    processedMatches[i] = processedMatches[i].copy(visitorteam = cachedTeam)
                }
            }

            // Apply cached league data if available
            match.league?.let { league ->
                leagueCache[league.id]?.let { cachedLeague ->
                    processedMatches[i] = processedMatches[i].copy(league = cachedLeague)
                }
            }
        }

        // If we're currently at the rate limit, just return what we have from cache
        if (apiSemaphore.availablePermits() == 0) {
            Log.w("API_RATE_LIMIT", "API rate limit hit, using cached data only")
            onComplete(processedMatches)
            return
        }

        // For data not in cache, schedule background fetches but don't block the UI
        scope.launch {
            val deferredFetches = mutableListOf<Deferred<Unit>>()

            // Schedule fetches for missing team data
            for (i in matches.indices) {
                val match = matches[i]

                match.localteam?.let { team ->
                    if (team.name.isNullOrEmpty() || team.image_path.isNullOrEmpty()) {
                        if (teamCache[team.id] == null) {
                            async {
                                val teamDetails = fetchTeamDetailsSync(team.id)
                                teamDetails?.let {
                                    teamCache[it.id] = it
                                    val idx = processedMatches.indexOfFirst { m -> m.id == match.id }
                                    if (idx >= 0) {
                                        processedMatches[idx] = processedMatches[idx].copy(localteam = it)
                                    }
                                }
                            }?.let { deferredFetches.add(it as Deferred<Unit>) }
                        }
                    }
                }

                match.visitorteam?.let { team ->
                    if (team.name.isNullOrEmpty() || team.image_path.isNullOrEmpty()) {
                        if (teamCache[team.id] == null) {
                            async {
                                val teamDetails = fetchTeamDetailsSync(team.id)
                                teamDetails?.let {
                                    teamCache[it.id] = it
                                    val idx = processedMatches.indexOfFirst { m -> m.id == match.id }
                                    if (idx >= 0) {
                                        processedMatches[idx] = processedMatches[idx].copy(visitorteam = it)
                                    }
                                }
                            }?.let { deferredFetches.add(it as Deferred<Unit>) }
                        }
                    }
                }

                match.league?.let { league ->
                    if (league.name.isNullOrEmpty() || league.image_path.isNullOrEmpty()) {
                        if (leagueCache[league.id] == null) {
                            async {
                                val leagueDetails = fetchLeagueDetailsSync(league.id)
                                leagueDetails?.let {
                                    leagueCache[it.id] = it
                                    val idx = processedMatches.indexOfFirst { m -> m.id == match.id }
                                    if (idx >= 0) {
                                        processedMatches[idx] = processedMatches[idx].copy(league = it)
                                    }
                                }
                            }?.let { deferredFetches.add(it as Deferred<Unit>) }
                        }
                    }
                }
            }
        }

        // Return what we have now, background fetches will update cache for next time
        onComplete(processedMatches)
    }

    private suspend fun fetchTeamDetailsSync(teamId: Int): Team? {
        return makeApiCall {
            try {
                val authToken = FirebaseConfig.authToken ?: return@makeApiCall null

                val response = api.getTeamDetails(teamId, authToken).execute()
                if (response.isSuccessful && response.body() != null) {
                    val team = response.body()?.data
                    team?.let { teamCache[teamId] = it }
                    return@makeApiCall team
                } else {
                    Log.e("API_ERROR", "Failed to fetch team $teamId: ${response.errorBody()?.string()}")
                    // Create placeholder if API fails
                    return@makeApiCall Team(teamId, "Team $teamId", "Team $teamId", null)
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Exception fetching team $teamId: ${e.message}")
                return@makeApiCall null
            }
        }
    }

    private suspend fun fetchLeagueDetailsSync(leagueId: Int): League? {
        return makeApiCall {
            try {
                val authToken = FirebaseConfig.authToken ?: return@makeApiCall null

                val response = api.getLeagueDetails(leagueId, authToken).execute()
                if (response.isSuccessful && response.body() != null) {
                    val league = response.body()?.data
                    league?.let { leagueCache[leagueId] = it }
                    return@makeApiCall league
                } else {
                    // Handle subscription error gracefully
                    if (response.errorBody()?.string()?.contains("not accessible from your subscription") == true) {
                        Log.w("API_ERROR", "League $leagueId is not accessible due to subscription limits")
                        // Return a placeholder league with the ID but no other data
                        return@makeApiCall League(leagueId, "League $leagueId", null)
                    } else {
                        Log.e("API_ERROR", "Failed to fetch league $leagueId: ${response.errorBody()?.string()}")
                        return@makeApiCall League(leagueId, "League $leagueId", null)
                    }
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Exception fetching league $leagueId: ${e.message}")
                return@makeApiCall null
            }
        }
    }

    // Keeping these API for backward compatibility
    fun fetchTeamDetails(teamId: Int, onResult: (Team?) -> Unit) {
        // Check cache first and return immediately if available
        teamCache[teamId]?.let {
            onResult(it)
            return
        }

        // If we're currently at the rate limit, use fallback data
        if (apiSemaphore.availablePermits() == 0) {
            Log.w("API_RATE_LIMIT", "API rate limit hit, using placeholder for team $teamId")
            onResult(Team(teamId, "Team $teamId", "Team $teamId", null))
            return
        }

        FirebaseConfig.loadConfig {
            val authToken = FirebaseConfig.authToken

            if (authToken.isNullOrEmpty()) {
                Log.e("API_ERROR", "Missing auth token")
                onResult(null)
                return@loadConfig
            }

            val call: Call<TeamResponse> = api.getTeamDetails(teamId, authToken)

            call.enqueue(object : Callback<TeamResponse> {
                override fun onResponse(call: Call<TeamResponse>, response: Response<TeamResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val team = response.body()?.data
                        team?.let {
                            teamCache[teamId] = it // Cache the result
                            saveCacheToPrefs()
                        }
                        onResult(team)
                    } else {
                        Log.e("API_ERROR", "Failed to fetch team $teamId: ${response.errorBody()?.string()}")
                        // Return a placeholder for UI to show something
                        onResult(Team(teamId, "Team $teamId", "Team $teamId", null))
                    }
                }

                override fun onFailure(call: Call<TeamResponse>, t: Throwable) {
                    Log.e("API_ERROR", "Network call failed when fetching team $teamId: ${t.message}")
                    // Return a placeholder for UI to show something
                    onResult(Team(teamId, "Team $teamId", "Team $teamId", null))
                }
            })
        }
    }

    fun fetchLeagueDetails(leagueId: Int, onResult: (League?) -> Unit) {
        // Check cache first and return immediately if available
        leagueCache[leagueId]?.let {
            onResult(it)
            return
        }

        // If we're currently at the rate limit, use fallback data
        if (apiSemaphore.availablePermits() == 0) {
            Log.w("API_RATE_LIMIT", "API rate limit hit, using placeholder for league $leagueId")
            onResult(League(leagueId, "League $leagueId", null))
            return
        }

        FirebaseConfig.loadConfig {
            val authToken = FirebaseConfig.authToken

            if (authToken.isNullOrEmpty()) {
                Log.e("API_ERROR", "Missing auth token")
                onResult(null)
                return@loadConfig
            }

            val call: Call<LeagueResponse> = api.getLeagueDetails(leagueId, authToken)

            call.enqueue(object : Callback<LeagueResponse> {
                override fun onResponse(call: Call<LeagueResponse>, response: Response<LeagueResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val league = response.body()?.data
                        league?.let {
                            leagueCache[leagueId] = it // Cache the result
                            saveCacheToPrefs()
                        }
                        onResult(league)
                    } else {
                        // Handle subscription error gracefully
                        if (response.errorBody()?.string()?.contains("not accessible from your subscription") == true) {
                            Log.w("API_ERROR", "League $leagueId is not accessible due to subscription limits")
                            // Return a placeholder league with the ID but no other data
                            val placeholder = League(leagueId, "League $leagueId", null)
                            leagueCache[leagueId] = placeholder
                            onResult(placeholder)
                        } else {
                            Log.e("API_ERROR", "Failed to fetch league $leagueId: ${response.errorBody()?.string()}")
                            onResult(League(leagueId, "League $leagueId", null))
                        }
                    }
                }

                override fun onFailure(call: Call<LeagueResponse>, t: Throwable) {
                    Log.e("API_ERROR", "Network call failed when fetching league $leagueId: ${t.message}")
                    onResult(League(leagueId, "League $leagueId", null))
                }
            })
        }
    }
}