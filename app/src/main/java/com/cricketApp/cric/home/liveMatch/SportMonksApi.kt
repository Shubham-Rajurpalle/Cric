package com.cricketApp.cric.home.liveMatch

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface SportMonksApi {
    @GET
    fun getLiveMatches(
        @Url endpoint: String,
        @Query("api_token") token: String,
        @Query("include") includes: String = "localteam,visitorteam,league,runs,stage"
    ): Call<SportMonksResponse>

    // Optional: Add methods to fetch individual team details if needed
    @GET("teams/{id}")
    fun getTeamDetails(
        @Path("id") teamId: Int,
        @Query("api_token") token: String
    ): Call<TeamResponse>

    // Optional: Add method to fetch league details if needed
    @GET("leagues/{id}")
    fun getLeagueDetails(
        @Path("id") leagueId: Int,
        @Query("api_token") token: String
    ): Call<League>
}