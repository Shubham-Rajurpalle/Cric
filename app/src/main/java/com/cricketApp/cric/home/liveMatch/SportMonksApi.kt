package com.cricketApp.cric.home.liveMatch

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface SportMonksApi {
    @GET
    fun getLiveMatches(
        @Url endpoint: String,
        @Query("api_token") token: String
    ): Call<SportMonksResponse> // Using correct response model
}
