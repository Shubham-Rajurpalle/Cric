package com.cricketApp.cric.home.liveMatch

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MatchRepository {
    private val api = RetrofitInstance.getApi()

    fun fetchLiveMatches(onResult: (List<MatchData>?) -> Unit) {
        FirebaseConfig.loadConfig {
            val endpoint = FirebaseConfig.liveMatchesEndpoint
            val authToken = FirebaseConfig.authToken

            if (endpoint.isNullOrEmpty() || authToken.isNullOrEmpty()) {
                Log.e("API_ERROR", "Missing API endpoint or auth token")
                onResult(null)
                return@loadConfig
            }

            val call: Call<SportMonksResponse> = api.getLiveMatches(endpoint, authToken)

            call.enqueue(object : Callback<SportMonksResponse> {
                override fun onResponse(
                    call: Call<SportMonksResponse>,
                    response: Response<SportMonksResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        onResult(response.body()?.data ?: emptyList())
                    } else {
                        Log.e("API_ERROR", "Response unsuccessful: ${response.errorBody()?.string()}")
                        onResult(null)
                    }
                }

                override fun onFailure(call: Call<SportMonksResponse>, t: Throwable) {
                    Log.e("API_ERROR", "Network call failed: ${t.message}")
                    onResult(null)
                }
            })
        }
    }
}
