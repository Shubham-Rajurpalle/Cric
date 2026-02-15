package com.cricketApp.cric.home.liveMatch

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
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
    private val gson = GsonBuilder().serializeNulls().create()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("cricket_app_cache", Context.MODE_PRIVATE)

    // Rate limiting - allow only 1 API call at a time with a 500ms delay between calls
    private val apiSemaphore = Semaphore(1)
    private val apiCallDelay = 500L // 500ms delay between API calls

    init {
        // Load cached data on initialization
        loadCachedData()
    }

    private fun loadCachedData() {
        try {
            // Load team cache
            val cachedTeams = sharedPrefs.getString("teams_cache", null)
            if (!cachedTeams.isNullOrEmpty()) {
                val teamMapType = object : TypeToken<Map<Int, Team>>() {}.type
                val teamsMap: Map<Int, Team> = gson.fromJson(cachedTeams, teamMapType)
                teamCache.putAll(teamsMap)
            }

            // Load league cache
            val cachedLeagues = sharedPrefs.getString("leagues_cache", null)
            if (!cachedLeagues.isNullOrEmpty()) {
                val leagueMapType = object : TypeToken<Map<Int, League>>() {}.type
                val leaguesMap: Map<Int, League> = gson.fromJson(cachedLeagues, leagueMapType)
                leagueCache.putAll(leaguesMap)
            }
        } catch (e: Exception) {
            // Clear corrupted cache and continue
            sharedPrefs.edit().clear().apply()
        }
    }

    private fun saveCacheToPrefs() {
        try {
            // Save team cache
            val teamsJson = gson.toJson(teamCache)
            sharedPrefs.edit().putString("teams_cache", teamsJson).apply()

            // Save league cache
            val leaguesJson = gson.toJson(leagueCache)
            sharedPrefs.edit().putString("leagues_cache", leaguesJson).apply()
        } catch (e: Exception) {
            // Just log error and continue - caching is non-critical
        }
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
            return@withContext null
        }
    }

    fun fetchLiveMatches(onResult: (List<MatchData>?) -> Unit) {
        FirebaseConfig.loadConfig {
            val endpoint = FirebaseConfig.liveMatchesEndpoint
            val authToken = FirebaseConfig.authToken

            if (endpoint.isNullOrEmpty() || authToken.isNullOrEmpty()) {
                onResult(null)
                return@loadConfig
            }

            // Make sure we're using the correct generic type for Call<SportMonksResponse>
            val call: Call<SportMonksResponse> = api.getLiveMatches(
                endpoint,
                authToken,
                "localteam,visitorteam,league,runs,stage"
            )

            call.enqueue(object : Callback<SportMonksResponse> {
                override fun onResponse(
                    call: Call<SportMonksResponse>,
                    response: Response<SportMonksResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val matches = response.body()?.data ?: emptyList()

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
                        onResult(null)
                    }
                }

                override fun onFailure(call: Call<SportMonksResponse>, t: Throwable) {
                    onResult(null)
                }
            })
        }
    }

    private fun completeMatchData(matches: List<MatchData>, onComplete: (List<MatchData>) -> Unit) {
        // Create a defensive copy of matches list to avoid modification issues
        val processedMatches = matches.map { it.copy() }.toMutableList()

        // Enhance match data with cached information first
        for (i in processedMatches.indices) {
            val match = processedMatches[i]

            // Apply cached team data if available
            match.localteam?.id?.let { teamId ->
                teamCache[teamId]?.let { cachedTeam ->
                    processedMatches[i] = processedMatches[i].copy(localteam = cachedTeam)
                }
            }

            match.visitorteam?.id?.let { teamId ->
                teamCache[teamId]?.let { cachedTeam ->
                    processedMatches[i] = processedMatches[i].copy(visitorteam = cachedTeam)
                }
            }

            // Apply cached league data if available
            match.league?.id?.let { leagueId ->
                leagueCache[leagueId]?.let { cachedLeague ->
                    processedMatches[i] = processedMatches[i].copy(league = cachedLeague)
                }
            }
        }

        // Return what we have from cache immediately
        onComplete(processedMatches)

        // Schedule background fetch for missing data
        CoroutineScope(Dispatchers.IO).launch {
            val missingTeamIds = mutableSetOf<Int>()
            val missingLeagueIds = mutableSetOf<Int>()

            // Collect all missing IDs
            for (match in matches) {
                match.localteam?.id?.let { teamId ->
                    if (teamCache[teamId]?.name.isNullOrEmpty() || teamCache[teamId]?.image_path.isNullOrEmpty()) {
                        missingTeamIds.add(teamId)
                    }
                }

                match.visitorteam?.id?.let { teamId ->
                    if (teamCache[teamId]?.name.isNullOrEmpty() || teamCache[teamId]?.image_path.isNullOrEmpty()) {
                        missingTeamIds.add(teamId)
                    }
                }

                match.league?.id?.let { leagueId ->
                    if (leagueCache[leagueId]?.name.isNullOrEmpty() || leagueCache[leagueId]?.image_path.isNullOrEmpty()) {
                        missingLeagueIds.add(leagueId)
                    }
                }
            }

            // Fetch missing teams
            for (teamId in missingTeamIds) {
                fetchTeamDetailsSync(teamId)
            }

            // Fetch missing leagues
            for (leagueId in missingLeagueIds) {
                fetchLeagueDetailsSync(leagueId)
            }

            // Save the updated cache
            saveCacheToPrefs()
        }
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
                    // Create placeholder if API fails
                    return@makeApiCall Team(teamId, "Team $teamId", "Team $teamId", null)
                }
            } catch (e: Exception) {
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
                        // Return a placeholder league with the ID but no other data
                        return@makeApiCall League(leagueId, "League $leagueId", null)
                    } else {
                        return@makeApiCall League(leagueId, "League $leagueId", null)
                    }
                }
            } catch (e: Exception) {
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
            onResult(Team(teamId, "Team $teamId", "Team $teamId", null))
            return
        }

        FirebaseConfig.loadConfig {
            val authToken = FirebaseConfig.authToken

            if (authToken.isNullOrEmpty()) {
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
                        // Return a placeholder for UI to show something
                        onResult(Team(teamId, "Team $teamId", "Team $teamId", null))
                    }
                }

                override fun onFailure(call: Call<TeamResponse>, t: Throwable) {
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
            onResult(League(leagueId, "League $leagueId", null))
            return
        }

        FirebaseConfig.loadConfig {
            val authToken = FirebaseConfig.authToken

            if (authToken.isNullOrEmpty()) {
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
                            // Return a placeholder league with the ID but no other data
                            val placeholder = League(leagueId, "League $leagueId", null)
                            leagueCache[leagueId] = placeholder
                            onResult(placeholder)
                        } else {
                            onResult(League(leagueId, "League $leagueId", null))
                        }
                    }
                }

                override fun onFailure(call: Call<LeagueResponse>, t: Throwable) {
                    onResult(League(leagueId, "League $leagueId", null))
                }
            })
        }
    }
}