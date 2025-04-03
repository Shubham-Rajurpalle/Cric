package com.cricketApp.cric.home.upcomingMatch

import android.util.Log
import com.cricketApp.cric.home.UpcomingMatches.FirebaseConfigUpcoming
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UpcomingMatchRepository {
    private val api = RetrofitInstanceUpcoming.api
    private val teamCache = mutableMapOf<Int, TeamDetails>()
    private val seriesCache = mutableMapOf<Int, SeriesDetails>()
    private val leagueCache = mutableMapOf<Int, LeagueDetails>()


    fun fetchUpcomingMatches(onResult: (List<MatchData>?) -> Unit) {
        FirebaseConfigUpcoming.loadConfig {
            val authToken = FirebaseConfigUpcoming.authToken

            if (authToken.isNullOrEmpty()) {
            //    Log.e("API_ERROR", "Missing API auth token")
                onResult(null)
                return@loadConfig
            }

            // First attempt - try with include=localteam,visitorteam to get team data in one call
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
                    //    Log.e(
                    //        "API_ERROR",
                    //        "Response unsuccessful: ${response.errorBody()?.string()}"
                    //    )
                        onResult(null)
                    }
                }

                override fun onFailure(call: Call<SportMonksResponseUpcoming>, t: Throwable) {
               //     Log.e("API_ERROR", "Network call failed: ${t.message}")
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

        val processedMatches = matches.toMutableList()
        val pendingRequests = matches.size * 3 + matches.size // 2 teams + 1 series + 1 league per match
        var completedRequests = 0

        // Check if we already have team objects, series logo, and league logo in the response
        val needTeamFetch = matches.any { it.localteam == null || it.visitorteam == null }
        val needSeriesFetch = matches.any { it.seriesLogo == null }
        val needLeagueFetch = matches.any { it.leagueLogo == null }

        if (!needTeamFetch && !needSeriesFetch && !needLeagueFetch) {
            // If we already have team objects, series logos, and league logos, just extract them
            matches.forEach { match ->
                match.localteamLogo = match.localteam?.image_path
                match.visitorteamLogo = match.visitorteam?.image_path
                match.seriesLogo = match.seriesLogo
                match.leagueLogo = match.leagueLogo
            }
            onResult(matches)
            return
        }

        // If we need to fetch teams, series, or league logo, do it for each match
        matches.forEach { match ->
            // Fetch local team if not in cache
            fetchTeamDetails(match.localteam_id, authToken) { teamDetails ->
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

                    completedRequests++
                    if (completedRequests >= pendingRequests) {
                        onResult(processedMatches)
                    }
                }
            }

            // Fetch visitor team if not in cache
            fetchTeamDetails(match.visitorteam_id, authToken) { teamDetails ->
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

                    completedRequests++
                    if (completedRequests >= pendingRequests) {
                        onResult(processedMatches)
                    }
                }
            }

            // Fetch series logo if not in cache
            fetchSeriesLogo(match.series_id, authToken) { seriesDetails ->
                synchronized(processedMatches) {
                    val index = processedMatches.indexOfFirst { it.id == match.id }
                    if (index != -1) {
                        val updatedMatch = processedMatches[index].copy()
                        updatedMatch.seriesLogo = seriesDetails.logo_path
                        processedMatches[index] = updatedMatch
                    }

                    completedRequests++
                    if (completedRequests >= pendingRequests) {
                        onResult(processedMatches)
                    }
                }
            }

            // Fetch league logo if not in cache
            fetchLeagueLogo(match.series_id, authToken) { leagueDetails ->
                synchronized(processedMatches) {
                    val index = processedMatches.indexOfFirst { it.id == match.id }
                    if (index != -1) {
                        val updatedMatch = processedMatches[index].copy()
                        updatedMatch.leagueLogo = leagueDetails.logo_path
                        processedMatches[index] = updatedMatch
                    }

                    completedRequests++
                    if (completedRequests >= pendingRequests) {
                        onResult(processedMatches)
                    }
                }
            }
        }
    }

    private fun fetchLeagueLogo(leagueId: Int, authToken: String, onComplete: (LeagueDetails) -> Unit) {
        // Check cache first
        if (leagueCache.containsKey(leagueId)) {
            onComplete(leagueCache[leagueId]!!)
            return
        }

        // Otherwise make API call
        api.getLeagueById(leagueId, authToken).enqueue(object : Callback<LeagueResponse> {
            override fun onResponse(call: Call<LeagueResponse>, response: Response<LeagueResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val leagueDetails = response.body()!!.data
                    // Cache the result
                    leagueCache[leagueId] = leagueDetails
                    onComplete(leagueDetails)
                } else {
                //    Log.e("API_ERROR", "Failed to fetch league $leagueId: ${response.errorBody()?.string()}")
                    // Return a placeholder (empty or default logo)
                    onComplete(LeagueDetails(leagueId, "Unknown League", null))
                }
            }

            override fun onFailure(call: Call<LeagueResponse>, t: Throwable) {
            //    Log.e("API_ERROR", "Network error fetching league $leagueId: ${t.message}")
                // Return a placeholder (empty or default logo)
                onComplete(LeagueDetails(leagueId, "Unknown League", null))
            }
        })
    }


    private fun fetchTeamDetails(teamId: Int, authToken: String, onComplete: (TeamDetails) -> Unit) {
        // Check cache first
        if (teamCache.containsKey(teamId)) {
            onComplete(teamCache[teamId]!!)
            return
        }

        // Otherwise make API call
        api.getTeamById(teamId, authToken).enqueue(object : Callback<TeamResponse> {
            override fun onResponse(call: Call<TeamResponse>, response: Response<TeamResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val teamDetails = response.body()!!.data
                    // Cache the result
                    teamCache[teamId] = teamDetails
                    onComplete(teamDetails)
                } else {
                //    Log.e("API_ERROR", "Failed to fetch team $teamId: ${response.errorBody()?.string()}")
                    // Return a placeholder
                    onComplete(TeamDetails(teamId, "Unknown Team", "UNK", null))
                }
            }

            override fun onFailure(call: Call<TeamResponse>, t: Throwable) {
            //    Log.e("API_ERROR", "Network error fetching team $teamId: ${t.message}")
                // Return a placeholder
                onComplete(TeamDetails(teamId, "Unknown Team", "UNK", null))
            }
        })
    }

    private fun fetchSeriesLogo(seriesId: Int, authToken: String, onComplete: (SeriesDetails) -> Unit) {
        // Check cache first
        if (seriesCache.containsKey(seriesId)) {
            onComplete(seriesCache[seriesId]!!)
            return
        }

        // Otherwise make API call
        api.getSeriesById(seriesId, authToken).enqueue(object : Callback<SeriesResponse> {
            override fun onResponse(call: Call<SeriesResponse>, response: Response<SeriesResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val seriesDetails = response.body()!!.data
                    // Cache the result
                    seriesCache[seriesId] = seriesDetails
                    onComplete(seriesDetails)
                } else {
                //    Log.e("API_ERROR", "Failed to fetch series $seriesId: ${response.errorBody()?.string()}")
                    // Return a placeholder (empty or default logo)
                    onComplete(SeriesDetails(seriesId, "Unknown Series", null))
                }
            }

            override fun onFailure(call: Call<SeriesResponse>, t: Throwable) {
            //    Log.e("API_ERROR", "Network error fetching series $seriesId: ${t.message}")
                // Return a placeholder (empty or default logo)
                onComplete(SeriesDetails(seriesId, "Unknown Series", null))
            }
        })
    }
}
