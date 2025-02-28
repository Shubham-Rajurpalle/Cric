package com.cricketApp.cric.home.upcomingMatch

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query


interface SportMonkApiUpcoming {
    @GET("fixtures")
    fun getUpcomingMatches(
        @Query("api_token") token: String,
        @Query("filter[status]") status: String = "NS",
        @Query("sort") sort: String = "starting_at",
        @Query("include") include: String = "localteam,visitorteam" // Try to include teams directly
    ): Call<SportMonksResponseUpcoming>

    @GET("teams/{team_id}")
    fun getTeamById(
        @Path("team_id") teamId: Int,
        @Query("api_token") token: String
    ): Call<TeamResponse>

    // Series API
    @GET("series/{series_id}")
    fun getSeriesById(
        @Path("series_id") seriesId: Int,
        @Query("api_token") authToken: String
    ): Call<SeriesResponse>

    // League API
    @GET("leagues/{league_id}")
    fun getLeagueById(
        @Path("league_id") leagueId: Int,
        @Query("api_token") authToken: String
    ): Call<LeagueResponse>
}
