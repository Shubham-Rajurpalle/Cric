package com.cricketApp.cric.home.liveMatch

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private var retrofit: Retrofit? = null

    fun getApi(): SportMonksApi {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(FirebaseConfig.baseUrl ?: "https://cricket.sportmonks.com/api/v2.0/") // Fallback URL
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(SportMonksApi::class.java)
    }
}
