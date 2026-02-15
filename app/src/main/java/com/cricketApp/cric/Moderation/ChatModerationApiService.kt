package com.cricketApp.cric.Moderation

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ChatModerationApiService {
    @POST("v1alpha1/comments:analyze")
    fun analyzeMessage(@Body request: JsonObject, @Query("key") apiKey: String): Call<JsonObject>
}

class ChatModerationService(private val context: Context) {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://commentanalyzer.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(ChatModerationApiService::class.java)

    // Store word lists as immutable sets to improve performance
    private val hinglishProhibitedWords = setOf(
        "chutiya", "madarchod", "behenchod", "bhosdike", "gandu", "randi", "lauda",
        "chodu", "lodu","land", "jhatu", "bhadva", "bhosadike", "chutiye", "gaandu",
        "harami", "kamina", "saala", "rand", "bkl", "mc", "bc", "mkc", "bhosdi",

        // Hinglish slang and abbreviations
        "laudu","lavdhya","lavdha","lavdhe","lawdhe","laud","lauda", "madar", "madarjaat", "bhonsdike", "lodey", "gadha", "kutte",
        "chodu bhai", "gali mt de", "gandu log", "chu", "chep", "chichora",

        // Common phrases
        "teri maa", "teri ma", "teri behn", "baap ke", "maaki",
        "gaand mara", "maa chuda", "bhains ki",

        // Transliteration variations
        "madharchod", "maderchod", "madarjaat", "banchod", "benchod", "gandoo",
        "chutiyapa", "chutiyagiri", "randaap", "randipana", "bhosadpappu",

        // Common text-speak variations
        "chwtiya", "g@ndu", "m@d@rchod", "bh3nch0d", "r@nd1", "ch00t", "l@ud@",
        "bh0$ d!ke", "l0du", "chut!y@", "r@nd", "bc", "mc", "bsdk", "mkc", "bhsdk",

        // Roman numerals and symbols as replacements
        "ch001", "g4ndu", "m4d4rch0d", "b3h3nch0d", "r4nd1", "l4ud4", "ch00t",
        "bh05d1k3", "ch*t", "g*nd", "b*c", "m*c", "b*dk", "l*de", "r*ndi"
    )

    // Custom list of Hindi prohibited words (Devanagari script)
    private val hindiProhibitedWords = setOf(
        // Common Hindi/Urdu profanity in Devanagari
        "गांडू", "लवड़्या", "भोसडी", "चुतिया", "मादरचोद",
        "बहनचोद", "भोसड़ीके", "लंड", "रंडी", "लौड़ा",
        "झाटू", "झांट", "चोदू", "चूत", "गांड",
        "टट्टी", "मुठ", "हरामी", "हरामखोर", "कुत्ता", "कुत्ती",
        "साला", "कंजर", "भोसड़ी", "भोसड़ा", "रांड",

        // Regional variations in Devanagari
        "लोडू", "लोड़ा", "चुदाई", "गधा", "सुअर", "कमीने",

        // Common phrases in Devanagari
        "तेरी माँ की", "तेरी माँ", "तेरी बहन", "गांड मारा",
        "माँ चुदा", "अपनी गांड", "माँ की चूत"
    )

    // Custom list of English prohibited words
    private val englishProhibitedWords = setOf(
        // Common English profanity
        "fuck", "fucking", "fucker", "motherfucker", "motherfucking", "mothafucka",
        "shit", "bullshit", "horseshit", "shithead", "shitty", "shitting",
        "asshole", "ass", "arse", "arsehole", "asswipe", "asshat",
        "bitch", "bitching", "bitchy", "son of a bitch", "sob",
        "cunt", "twat", "pussy", "vagina", "clitoris", "clit",
        "dick", "cock", "penis", "dong", "dickhead", "dickwad",
        "whore", "slut", "hoe", "hooker", "prostitute",
        "bastard", "douchebag", "douche", "wanker", "jackass",

        // Racial/ethnic slurs (included for comprehensive moderation)
        "nigger", "nigga", "chink", "gook", "spic", "wetback", "kike",
        "paki", "raghead", "towelhead", "beaner", "cracker", "honky",

        // Sexual terms
        "blowjob", "handjob", "rimjob", "fellatio", "cunnilingus", "anilingus",
        "anal", "cum", "cumming", "jizz", "sperm", "semen",
        "orgasm", "masturbate", "masturbation", "fap",

        // Common obfuscations
        "f*ck", "sh*t", "a**hole", "b*tch", "c*nt", "d*ck", "p*ssy",
        "fck", "fuk", "fuq", "sht", "azz", "a$$", "b!tch",

        // Abbreviated forms
        "wtf", "stfu", "gtfo", "lmfao", "omfg", "af", "bs", "mf"
    )

    // Get API key from Firebase
    private suspend fun getGoogleApiKey(): String = suspendCancellableCoroutine { continuation ->
        val configRef = FirebaseDatabase.getInstance().getReference("apiConfig/perspectiveApiKey")

        val listener = configRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val apiKey = snapshot.getValue(String::class.java)
                if (!apiKey.isNullOrEmpty()) {
                    continuation.resume(apiKey)
                } else {
                    continuation.resumeWithException(Exception("Failed to retrieve Perspective API key"))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                continuation.resumeWithException(Exception("Failed to retrieve Perspective API key: ${error.message}"))
            }
        })

        continuation.invokeOnCancellation {
            configRef.removeEventListener(listener)
        }
    }

    // Pre-compile regex patterns to improve performance
    private val compiledLeetSpeakPatterns = mutableMapOf<String, Pattern>()
    private val compiledSplitWordPatterns = mutableListOf<Pattern>()
    private val compiledHinglishPatterns = mutableListOf<Pattern>()
    private val compiledHintechPatterns = mutableListOf<Pattern>()

    // Hinglish indicator words to detect Hinglish content
    private val hinglishIndicatorWords = arrayOf(
        "kya", "hai", "nahi", "karo", "mat", "aap", "tum", "hum", "mein", "ko", "se"
    )

    init {
        // Pre-compile regex patterns at initialization to avoid doing this for every message
        precompilePatterns()
    }

    private fun precompilePatterns() {
        // Leetspeak patterns
        val leetSpeakPatternStrings = mapOf(
            "fuck" to "[f]+[\\W_]*[u4]+[\\W_]*[c]+[\\W_]*[k]+",
            "shit" to "[s5]+[\\W_]*[h]+[\\W_]*[i1!]+[\\W_]*[t7]+",
            "bitch" to "[b8]+[\\W_]*[i1!]+[\\W_]*[t7]+[\\W_]*[c]+[\\W_]*[h]+",
            "ass" to "[a@4]+[\\W_]*[s5$]+[\\W_]*[s5$]+"
        )

        leetSpeakPatternStrings.forEach { (word, patternStr) ->
            try {
                compiledLeetSpeakPatterns[word] = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                // Log error handling removed for production
            }
        }

        // Split word patterns
        val splitWordPatternStrings = arrayOf(
            "f[\\W_]*u[\\W_]*c[\\W_]*k",
            "s[\\W_]*h[\\W_]*i[\\W_]*t",
            "b[\\W_]*i[\\W_]*t[\\W_]*c[\\W_]*h",
            "c[\\W_]*u[\\W_]*n[\\W_]*t"
        )

        splitWordPatternStrings.forEach { patternStr ->
            try {
                compiledSplitWordPatterns.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE))
            } catch (e: Exception) {
                // Log error handling removed for production
            }
        }

        // Hinglish patterns - properly escaped
        val hinglishPatternStrings = arrayOf(
            "ch[u\\*o0@][t\\*][i\\*1!][y\\*][a\\*@4]",
            "m[a\\*@4][d\\*][a\\*@4][r\\*][c\\*][h\\*][o\\*0][d\\*]",
            "b[h\\*][o\\*0][s\\*\\$][d\\*][i\\*1!][k\\*][e\\*3]",
            "\\bb[\\s\\*\\-_]*[c\\*]\\b",
            "\\bm[\\s\\*\\-_]*[c\\*]\\b",
            "\\bb[\\s\\*\\-_]*[s\\*\\$][\\s\\*\\-_]*[d\\*][\\s\\*\\-_]*[k\\*]\\b",
            "t[e\\*3]ri[\\s\\*\\-_]*m[a\\*@4][a\\*@4]",
            "m[a\\*@4][a\\*@4][\\s\\*\\-_]*k[i\\*1!]",
            "b[e\\*3]h[e\\*3]n[\\s\\*\\-_]*k[e\\*3]"
        )

        hinglishPatternStrings.forEach { patternStr ->
            try {
                compiledHinglishPatterns.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE))
            } catch (e: Exception) {
                // Log error handling removed for production
            }
        }

        // Hintech patterns for detecting obfuscated content
        val hintechPatternStrings = arrayOf(
            "[g][4a@][n][d][u0o]",
            "[l][a@4][u][d][a@4]",
            "[r][a@4][n][d][i1!]"
        )

        hintechPatternStrings.forEach { patternStr ->
            try {
                compiledHintechPatterns.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE))
            } catch (e: Exception) {
                // Log error handling removed for production
            }
        }
    }

    interface ModerationCallback {
        fun onMessageApproved(message: String)
        fun onMessageRejected(message: String, reason: String)
        fun onError(errorMessage: String)
    }

    // Data class for moderation results
    private data class ModerationResult(
        val isApproved: Boolean,
        val reason: String = ""
    )

    fun checkMessageContent(message: String, callback: ModerationCallback) {
        // Don't waste API calls on empty messages
        if (message.isBlank()) {
            callback.onError("Message cannot be empty")
            return
        }

        // Move all the heavy processing to a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Check against our word lists (fast local check)
                if (containsProhibitedWords(message)) {
                    withContext(Dispatchers.Main) {
                        callback.onMessageRejected(message, "Message contains inappropriate content")
                    }
                    return@launch
                }

                // Step 2: Perform Hinglish-specific check
                if (containsHinglishProfanity(message)) {
                    withContext(Dispatchers.Main) {
                        callback.onMessageRejected(message, "Message contains inappropriate content")
                    }
                    return@launch
                }

                // Step 3: If local checks pass, use the Perspective API
                val jsonRequest = createModerationRequest(message)

                try {
                    // Get API key from Firebase
                    val apiKey = try {
                        getGoogleApiKey()
                    } catch (e: Exception) {
                        // In production, fallback to local check result if API key isn't available
                        withContext(Dispatchers.Main) {
                            callback.onMessageApproved(message)
                        }
                        return@launch
                    }

                    // Make the API call on the background thread with the retrieved API key
                    val response = service.analyzeMessage(jsonRequest, apiKey).execute()

                    if (response.isSuccessful) {
                        val result = processModerationResponse(response, message)
                        withContext(Dispatchers.Main) {
                            if (result.isApproved) {
                                callback.onMessageApproved(message)
                            } else {
                                callback.onMessageRejected(message, result.reason)
                            }
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        // Log error handling removed for production

                        // Fall back to local check result
                        withContext(Dispatchers.Main) {
                            callback.onMessageApproved(message)
                        }
                    }
                } catch (e: Exception) {
                    // Log error handling removed for production

                    // Fall back to local check result
                    withContext(Dispatchers.Main) {
                        callback.onMessageApproved(message)
                    }
                }
            } catch (e: Exception) {
                // Log error handling removed for production
                withContext(Dispatchers.Main) {
                    // In case of unexpected error, approve the message
                    callback.onMessageApproved(message)
                }
            }
        }
    }

    /**
     * Optimized method to check if the message contains any prohibited words
     */
    private fun containsProhibitedWords(message: String): Boolean {
        val lowercaseMsg = message.lowercase()

        // Check for exact matches first (fastest check)
        val allProhibitedWords = hindiProhibitedWords.union(englishProhibitedWords)
        for (word in allProhibitedWords) {
            if (lowercaseMsg.contains(" $word ") ||
                lowercaseMsg.startsWith("$word ") ||
                lowercaseMsg.endsWith(" $word") ||
                lowercaseMsg == word) {
                return true
            }
        }

        // Use pre-compiled leetspeak patterns
        for ((word, pattern) in compiledLeetSpeakPatterns) {
            if (pattern.matcher(lowercaseMsg).find()) {
                return true
            }
        }

        // Use pre-compiled split word patterns
        for (pattern in compiledSplitWordPatterns) {
            if (pattern.matcher(lowercaseMsg).find()) {
                return true
            }
        }

        // Only if all quick checks pass, do the more expensive regex checks
        // for word boundaries (only for words longer than 3 characters)
        for (word in allProhibitedWords) {
            // Skip very short words to avoid false positives
            if (word.length < 3) continue

            try {
                // Only check for word boundaries and suffixes for longer words
                val escapedWord = Pattern.quote(word)
                val patternString = "\\b$escapedWord\\b|\\b$escapedWord(?:[aeiou]s?)?\\b"
                val pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE)

                if (pattern.matcher(lowercaseMsg).find()) {
                    return true
                }
            } catch (e: Exception) {
                // Error handling removed for production
            }
        }

        return false
    }

    /**
     * Optimized method to check for Hinglish profanity
     */
    private fun containsHinglishProfanity(message: String): Boolean {
        val lowercaseMsg = message.lowercase()

        // Check against our Hinglish prohibited words list using exact matching first
        for (word in hinglishProhibitedWords) {
            if (lowercaseMsg.contains(" $word ") ||
                lowercaseMsg.startsWith("$word ") ||
                lowercaseMsg.endsWith(" $word") ||
                lowercaseMsg == word) {
                return true
            }
        }

        // Use pre-compiled hinglish patterns
        for (pattern in compiledHinglishPatterns) {
            if (pattern.matcher(lowercaseMsg).find()) {
                return true
            }
        }

        // Use pre-compiled hintech patterns
        for (pattern in compiledHintechPatterns) {
            if (pattern.matcher(lowercaseMsg).find()) {
                return true
            }
        }

        // Check for word boundaries only on longer words
        for (word in hinglishProhibitedWords) {
            if (word.length < 3) continue

            try {
                val escapedWord = Pattern.quote(word)
                val patternString = "\\b$escapedWord\\b|\\b$escapedWord[aeiou]?\\b"
                val pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE)

                if (pattern.matcher(lowercaseMsg).find()) {
                    return true
                }
            } catch (e: Exception) {
                // Error handling removed for production
            }
        }

        return false
    }

    /**
     * Detects if the message likely contains Hinglish patterns
     */
    private fun containsHinglishPatterns(message: String): Boolean {
        val lowercaseMsg = message.lowercase()

        // Check for common Hinglish words
        for (word in hinglishIndicatorWords) {
            if (Pattern.compile("\\b$word\\b").matcher(lowercaseMsg).find()) {
                return true
            }
        }

        // Check if message has English characters plus Hindi patterns
        val hasEnglish = message.matches(".*[a-zA-Z].*".toRegex())
        val hasHindiPatterns = message.contains("ki") || message.contains("ka") ||
                message.contains("ko") || message.contains("hai")

        return hasEnglish && hasHindiPatterns
    }

    /**
     * Creates the request to send to the Perspective API with language-specific configurations
     */
    private fun createModerationRequest(message: String): JsonObject {
        // Detect if the message contains Hindi characters
        val containsHindi = Pattern.compile("[\\u0900-\\u097F]").matcher(message).find()
        // Detect if the message contains Hinglish patterns
        val containsHinglish = containsHinglishPatterns(message)

        val jsonRequest = JsonObject().apply {
            add("comment", JsonObject().apply {
                addProperty("text", message)
            })

            // Add all relevant languages for analysis
            add("languages", JsonArray().apply {
                add("en") // Always include English

                // Add Hindi if Devanagari script is present
                if (containsHindi) {
                    add("hi")
                }

                // Add Hinglish (Hindi-English code-mixed)
                if (containsHinglish) {
                    // Note: The exact tag depends on the API's documentation
                    add("hi-en")
                }
            })

            // Create request with attributes supported for the detected languages
            add("requestedAttributes", JsonObject().apply {
                // These attributes work for both English and Hindi
                add("TOXICITY", JsonObject())
                add("SEVERE_TOXICITY", JsonObject())
                add("IDENTITY_ATTACK", JsonObject())
                add("INSULT", JsonObject())
                add("PROFANITY", JsonObject())
                add("THREAT", JsonObject())

                // SEXUALLY_EXPLICIT is only added for English-only content
                // since it doesn't support Hindi according to the error message
                if (!containsHindi && !containsHinglish) {
                    add("SEXUALLY_EXPLICIT", JsonObject())
                }
            })
        }

        return jsonRequest
    }

    /**
     * Processes the response from the Perspective API
     */
    private fun processModerationResponse(
        response: Response<JsonObject>,
        message: String
    ): ModerationResult {
        val responseBody = response.body()
        val attributeScores = responseBody?.getAsJsonObject("attributeScores")

        // Use a lower threshold for non-English or mixed content
        val containsNonEnglish = containsNonEnglishCharacters(message) || containsHinglishPatterns(message)
        val rejectThreshold = if (containsNonEnglish) 0.6f else 0.7f

        val toxicityScore = attributeScores
            ?.getAsJsonObject("TOXICITY")
            ?.getAsJsonObject("summaryScore")
            ?.get("value")
            ?.asFloat ?: 0f

        val insultScore = attributeScores
            ?.getAsJsonObject("INSULT")
            ?.getAsJsonObject("summaryScore")
            ?.get("value")
            ?.asFloat ?: 0f

        val threatScore = attributeScores
            ?.getAsJsonObject("THREAT")
            ?.getAsJsonObject("summaryScore")
            ?.get("value")
            ?.asFloat ?: 0f

        val identityAttackScore = attributeScores
            ?.getAsJsonObject("IDENTITY_ATTACK")
            ?.getAsJsonObject("summaryScore")
            ?.get("value")
            ?.asFloat ?: 0f

        val profanityScore = attributeScores
            ?.getAsJsonObject("PROFANITY")
            ?.getAsJsonObject("summaryScore")
            ?.get("value")
            ?.asFloat ?: 0f

        // SEXUALLY_EXPLICIT may not be present for Hindi content
        val sexuallyExplicitScore = attributeScores
            ?.getAsJsonObject("SEXUALLY_EXPLICIT")
            ?.getAsJsonObject("summaryScore")
            ?.get("value")
            ?.asFloat ?: 0f

        return when {
            toxicityScore > rejectThreshold -> ModerationResult(false, "Message contains inappropriate content")
            insultScore > rejectThreshold -> ModerationResult(false, "Message contains insulting content")
            threatScore > rejectThreshold -> ModerationResult(false, "Message contains threatening content")
            identityAttackScore > rejectThreshold -> ModerationResult(false, "Message contains discriminatory content")
            profanityScore > rejectThreshold -> ModerationResult(false, "Message contains profanity")
            sexuallyExplicitScore > rejectThreshold -> ModerationResult(false, "Message contains sexually explicit content")
            else -> ModerationResult(true)
        }
    }

    /**
     * Helper function to detect non-English characters
     */
    private fun containsNonEnglishCharacters(text: String): Boolean {
        // This pattern matches Devanagari (Hindi) and other common non-English scripts
        val nonEnglishPattern = Pattern.compile("[^\\p{ASCII}]|[\\u0900-\\u097F]|[\\u0980-\\u09FF]|[\\u0A00-\\u0A7F]")
        return nonEnglishPattern.matcher(text).find()
    }

    /**
     * Add ability to add new words to the filter at runtime (useful for admin functions)
     */
    fun addHindiProhibitedWord(word: String) {
        (hindiProhibitedWords as MutableSet).add(word.lowercase())
    }

    fun addEnglishProhibitedWord(word: String) {
        (englishProhibitedWords as MutableSet).add(word.lowercase())
    }

    fun addHinglishProhibitedWord(word: String) {
        (hinglishProhibitedWords as MutableSet).add(word.lowercase())
    }
}