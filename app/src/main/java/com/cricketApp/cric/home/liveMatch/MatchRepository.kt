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

            val call: Call<SportMonksResponse> = api.getLiveMatches(
                endpoint,
                authToken,
                "localteam,visitorteam,league,runs,stage" // Include additional data
            )

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

    // Optional: Add method to fetch additional team details if needed
    fun fetchTeamDetails(teamId: Int, onResult: (Team?) -> Unit) {
        FirebaseConfig.loadConfig {
            val authToken = FirebaseConfig.authToken

            if (authToken.isNullOrEmpty()) {
                Log.e("API_ERROR", "Missing auth token")
                onResult(null)
                return@loadConfig
            }

            val call: Call<TeamResponse> = api.getTeamDetails(teamId, authToken)

            call.enqueue(object : Callback<TeamResponse> {
                override fun onResponse(call: Call<TeamResponse>, response: Response<TeamResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        onResult(response.body()?.data)
                    } else {
                        Log.e("API_ERROR", "Response unsuccessful: ${response.errorBody()?.string()}")
                        onResult(null)
                    }
                }

                override fun onFailure(call: Call<TeamResponse>, t: Throwable) {
                    Log.e("API_ERROR", "Network call failed: ${t.message}")
                    onResult(null)
                }
            })
        }
    }
}