package com.cricketApp.cric.home.liveMatch

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    private var retrofit: Retrofit? = null
    private const val DEFAULT_BASE_URL = "https://cricket.sportmonks.com/api/v2.0/"

    fun getApi(): SportMonksApi {
        if (retrofit == null) {
            val baseUrl = FirebaseConfig.baseUrl ?: DEFAULT_BASE_URL

            // Create a custom Gson instance that handles null values properly
            val gson = GsonBuilder()
                .serializeNulls()
                .create()

            // Add logging for debug builds
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Create OkHttpClient with longer timeouts
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }
        return retrofit!!.create(SportMonksApi::class.java)
    }
}