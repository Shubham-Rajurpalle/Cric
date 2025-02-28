package com.cricketApp.cric.home.upcomingMatch

import com.cricketApp.cric.home.UpcomingMatches.FirebaseConfigUpcoming
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstanceUpcoming {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(FirebaseConfigUpcoming.baseUrl ?: "https://cricket.sportmonks.com/api/v2.0/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: SportMonkApiUpcoming by lazy {
        retrofit.create(SportMonkApiUpcoming::class.java)
    }
}
