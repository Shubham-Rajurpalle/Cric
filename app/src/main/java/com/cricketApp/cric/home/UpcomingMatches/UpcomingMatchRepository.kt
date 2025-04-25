package com.cricketApp.cric.home.upcomingMatch

import android.util.Log
import com.cricketApp.cric.home.UpcomingMatches.FirebaseConfigUpcoming
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class UpcomingMatchRepository {
    private val api = RetrofitInstanceUpcoming.api
    private val teamCache = mutableMapOf<Int, TeamDetails>()
    private val seriesCache = mutableMapOf<Int, SeriesDetails>()
    private val leagueCache = mutableMapOf<Int, LeagueDetails>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun fetchUpcomingMatches(onResult: (List<MatchData>?) -> Unit) {
        FirebaseConfigUpcoming.loadConfig {
            val authToken = FirebaseConfigUpcoming.authToken

            if (authToken.isNullOrEmpty()) {
                onResult(null)
                return@loadConfig
            }

            val call = api.getUpcomingMatches(authToken)

            call.enqueue(object : Callback<SportMonksResponseUpcoming> {
                override fun onResponse(
                    call: Call<SportMonksResponseUpcoming>,
                    response: Response<SportMonksResponseUpcoming>
                ) {
                    if (response.isSuccessful) {
                        val upcomingMatches = response.body()?.data ?: emptyList()
                        processMatches(upcomingMatches, authToken, onResult)
                    } else {
                        onResult(null)
                    }
                }

                override fun onFailure(call: Call<SportMonksResponseUpcoming>, t: Throwable) {
                    onResult(null)
                }
            })
        }
    }

    private fun processMatches(
        matches: List<MatchData>,
        authToken: String,
        onResult: (List<MatchData>?) -> Unit
    ) {
        if (matches.isEmpty()) {
            onResult(emptyList())
            return
        }

        // Create a thread-safe copy of matches
        val processedMatches = matches.map { it.copy() }.toMutableList()

        // Use AtomicInteger to track completion safely
        val totalRequests = matches.size * 4 // 2 teams + series + league per match
        val completedRequests = AtomicInteger(0)

        // Create a CoroutineScope for concurrent operations
        coroutineScope.launch {
            matches.forEach { match ->
                // Local team
                launch {
                    try {
                        val teamDetails = fetchTeamDetailsAsync(match.localteam_id, authToken)
                        synchronized(processedMatches) {
                            val index = processedMatches.indexOfFirst { it.id == match.id }
                            if (index != -1) {
                                val updatedMatch = processedMatches[index].copy()
                                updatedMatch.localteam = Team(
                                    id = teamDetails.id,
                                    name = teamDetails.name,
                                    code = teamDetails.code
                                )
                                updatedMatch.localteamLogo = teamDetails.image_path
                                processedMatches[index] = updatedMatch
                            }
                        }
                    } catch (e: Exception) {
                        // Handle error silently
                    } finally {
                        if (completedRequests.incrementAndGet() >= totalRequests) {
                            withContext(Dispatchers.Main) {
                                onResult(processedMatches)
                            }
                        }
                    }
                }

                // Visitor team
                launch {
                    try {
                        val teamDetails = fetchTeamDetailsAsync(match.visitorteam_id, authToken)
                        synchronized(processedMatches) {
                            val index = processedMatches.indexOfFirst { it.id == match.id }
                            if (index != -1) {
                                val updatedMatch = processedMatches[index].copy()
                                updatedMatch.visitorteam = Team(
                                    id = teamDetails.id,
                                    name = teamDetails.name,
                                    code = teamDetails.code
                                )
                                updatedMatch.visitorteamLogo = teamDetails.image_path
                                processedMatches[index] = updatedMatch
                            }
                        }
                    } catch (e: Exception) {
                        // Handle error silently
                    } finally {
                        if (completedRequests.incrementAndGet() >= totalRequests) {
                            withContext(Dispatchers.Main) {
                                onResult(processedMatches)
                            }
                        }
                    }
                }

                // Series
                launch {
                    try {
                        val seriesDetails = fetchSeriesLogoAsync(match.series_id, authToken)
                        synchronized(processedMatches) {
                            val index = processedMatches.indexOfFirst { it.id == match.id }
                            if (index != -1) {
                                val updatedMatch = processedMatches[index].copy()
                                updatedMatch.seriesLogo = seriesDetails.logo_path
                                processedMatches[index] = updatedMatch
                            }
                        }
                    } catch (e: Exception) {
                        // Handle error silently
                    } finally {
                        if (completedRequests.incrementAndGet() >= totalRequests) {
                            withContext(Dispatchers.Main) {
                                onResult(processedMatches)
                            }
                        }
                    }
                }

                // League
                launch {
                    try {
                        val leagueDetails = fetchLeagueLogoAsync(match.league_id, authToken)
                        synchronized(processedMatches) {
                            val index = processedMatches.indexOfFirst { it.id == match.id }
                            if (index != -1) {
                                val updatedMatch = processedMatches[index].copy()
                                updatedMatch.leagueLogo = leagueDetails.logo_path
                                processedMatches[index] = updatedMatch
                            }
                        }
                    } catch (e: Exception) {
                        // Handle error silently
                    } finally {
                        if (completedRequests.incrementAndGet() >= totalRequests) {
                            withContext(Dispatchers.Main) {
                                onResult(processedMatches)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchTeamDetailsAsync(teamId: Int, authToken: String): TeamDetails {
        // Check cache first
        teamCache[teamId]?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            api.getTeamById(teamId, authToken).enqueue(object : Callback<TeamResponse> {
                override fun onResponse(call: Call<TeamResponse>, response: Response<TeamResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val teamDetails = response.body()!!.data
                        // Cache the result
                        teamCache[teamId] = teamDetails
                        continuation.resume(teamDetails, null)
                    } else {
                        // Return a placeholder
                        val placeholder = TeamDetails(teamId, "Unknown Team", "UNK", null)
                        continuation.resume(placeholder, null)
                    }
                }

                override fun onFailure(call: Call<TeamResponse>, t: Throwable) {
                    // Return a placeholder
                    val placeholder = TeamDetails(teamId, "Unknown Team", "UNK", null)
                    continuation.resume(placeholder, null)
                }
            })
        }
    }

    private suspend fun fetchSeriesLogoAsync(seriesId: Int, authToken: String): SeriesDetails {
        // Check cache first
        seriesCache[seriesId]?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            api.getSeriesById(seriesId, authToken).enqueue(object : Callback<SeriesResponse> {
                override fun onResponse(call: Call<SeriesResponse>, response: Response<SeriesResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val seriesDetails = response.body()!!.data
                        // Cache the result
                        seriesCache[seriesId] = seriesDetails
                        continuation.resume(seriesDetails, null)
                    } else {
                        // Return a placeholder
                        val placeholder = SeriesDetails(seriesId, "Unknown Series", null)
                        continuation.resume(placeholder, null)
                    }
                }

                override fun onFailure(call: Call<SeriesResponse>, t: Throwable) {
                    // Return a placeholder
                    val placeholder = SeriesDetails(seriesId, "Unknown Series", null)
                    continuation.resume(placeholder, null)
                }
            })
        }
    }

    private suspend fun fetchLeagueLogoAsync(leagueId: Int, authToken: String): LeagueDetails {
        // Check cache first
        leagueCache[leagueId]?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            api.getLeagueById(leagueId, authToken).enqueue(object : Callback<LeagueResponse> {
                override fun onResponse(call: Call<LeagueResponse>, response: Response<LeagueResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val leagueDetails = response.body()!!.data
                        // Cache the result
                        leagueCache[leagueId] = leagueDetails
                        continuation.resume(leagueDetails, null)
                    } else {
                        // Return a placeholder
                        val placeholder = LeagueDetails(leagueId, "Unknown League", null)
                        continuation.resume(placeholder, null)
                    }
                }

                override fun onFailure(call: Call<LeagueResponse>, t: Throwable) {
                    // Return a placeholder
                    val placeholder = LeagueDetails(leagueId, "Unknown League", null)
                    continuation.resume(placeholder, null)
                }
            })
        }
    }
}