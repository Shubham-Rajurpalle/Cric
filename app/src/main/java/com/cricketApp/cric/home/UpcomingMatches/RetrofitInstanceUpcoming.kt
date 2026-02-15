package com.cricketApp.cric.home.upcomingMatch

import com.cricketApp.cric.home.UpcomingMatches.FirebaseConfigUpcoming
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstanceUpcoming {
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit by lazy {
        val baseUrl = FirebaseConfigUpcoming.baseUrl ?: "https://cricket.sportmonks.com/api/v2.0/"
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: SportMonkApiUpcoming by lazy {
        retrofit.create(SportMonkApiUpcoming::class.java)
    }
}