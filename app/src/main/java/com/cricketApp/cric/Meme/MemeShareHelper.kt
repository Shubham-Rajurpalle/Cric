package com.cricketApp.cric.Meme

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object MemeShareHelper {

    // ── Your Firebase Storage APK path ──────────────────────────────────────
    private const val APK_STORAGE_PATH = "apk/Cric_web.apk"

    // ── Where the short link is cached in Realtime Database ─────────────────
    // Structure: AppConfig/apkShortLink  (String value)
    private const val DB_SHORT_LINK_PATH = "AppConfig/apkShortLink"

    // ── TinyURL API — free, no key needed ───────────────────────────────────
    private const val TINYURL_API = "https://tinyurl.com/api-create.php?url="

    private val executor = Executors.newSingleThreadExecutor()

    // ─────────────────────────────────────────────────────────────────────────
    // Main entry point
    // ─────────────────────────────────────────────────────────────────────────
    fun shareMeme(context: Context, meme: MemeMessage) {
        Toast.makeText(context, "Preparing share…", Toast.LENGTH_SHORT).show()

        // Step 1: Try to get cached short link from Firebase Database first
        val dbRef = FirebaseDatabase.getInstance().getReference(DB_SHORT_LINK_PATH)
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cachedLink = snapshot.getValue(String::class.java)
                if (!cachedLink.isNullOrEmpty()) {
                    // ✅ Short link already exists in DB — use it directly
                    downloadImageAndShare(context, meme, cachedLink)
                } else {
                    // ❌ No cached link — fetch raw URL from Storage, then shorten it
                    fetchStorageUrlThenShorten(context, meme, dbRef)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // DB read failed — try fetching from Storage directly
                fetchStorageUrlThenShorten(context, meme, dbRef)
            }
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get Firebase Storage URL → shorten via TinyURL → cache in DB
    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchStorageUrlThenShorten(
        context: Context,
        meme: MemeMessage,
        dbRef: com.google.firebase.database.DatabaseReference
    ) {
        FirebaseStorage.getInstance()
            .getReference(APK_STORAGE_PATH)
            .downloadUrl
            .addOnSuccessListener { storageUri ->
                val rawUrl = storageUri.toString()
                // Shorten on background thread (network call)
                executor.execute {
                    val shortLink = shortenUrl(rawUrl) ?: rawUrl
                    // Cache in Firebase DB so next share is instant
                    dbRef.setValue(shortLink)
                    // Now share with the short/original link
                    downloadImageAndShare(context, meme, shortLink)
                }
            }
            .addOnFailureListener {
                // Storage URL fetch failed entirely — share without link
                downloadImageAndShare(context, meme, null)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TinyURL shortener — runs on background thread
    // Returns null if shortening fails (caller uses raw URL as fallback)
    // ─────────────────────────────────────────────────────────────────────────
    private fun shortenUrl(longUrl: String): String? {
        return try {
            val encodedUrl = java.net.URLEncoder.encode(longUrl, "UTF-8")
            val connection = URL("$TINYURL_API$encodedUrl").openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val response = connection.inputStream.bufferedReader().readText().trim()
            connection.disconnect()
            // TinyURL returns the short URL directly, validate it looks right
            if (response.startsWith("https://tinyurl.com/")) response else null
        } catch (e: Exception) {
            null // Network error — caller will use raw URL
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Download meme bitmap via Glide → cache → fire share intent
    // ─────────────────────────────────────────────────────────────────────────
    private fun downloadImageAndShare(context: Context, meme: MemeMessage, apkLink: String?) {
        Glide.with(context)
            .asBitmap()
            .load(meme.memeUrl)
            .into(object : CustomTarget<Bitmap>() {

                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    try {
                        val cacheDir = File(context.cacheDir, "memes").also { it.mkdirs() }
                        val file = File(cacheDir, "shared_meme_${meme.id}.jpg")
                        FileOutputStream(file).use { out ->
                            resource.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        val imageUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        launchShareIntent(context, meme, apkLink, imageUri)
                    } catch (e: Exception) {
                        launchShareIntent(context, meme, apkLink, null)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    launchShareIntent(context, meme, apkLink, null)
                }
            })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build and fire the share Intent
    // ─────────────────────────────────────────────────────────────────────────
    private fun launchShareIntent(
        context: Context,
        meme: MemeMessage,
        apkLink: String?,
        imageUri: Uri?
    ) {
        val shareText = buildShareText(meme, apkLink)

        val intent = if (imageUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
        }

        context.startActivity(Intent.createChooser(intent, "Share Meme via"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compose the share message
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildShareText(meme: MemeMessage, apkLink: String?): String {
        val teamSuffix = if (meme.team.isNotEmpty() && meme.team != "No Team") " • ${meme.team}" else ""
        val senderLine = if (meme.senderName.isNotEmpty()) "Posted by ${meme.senderName}$teamSuffix" else ""

        return buildString {
            appendLine("😂 Check out this cricket meme on CricketApp!")
            appendLine()
            if (senderLine.isNotEmpty()) appendLine(senderLine)
            appendLine("🔥 ${meme.hit} Hits   ❌ ${meme.miss} Misses")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("📲 Download CricketApp — The #1 app for cricket fans!")
            appendLine("Memes, live chat, leaderboards & more 🏏")
            if (!apkLink.isNullOrEmpty()) {
                appendLine()
                append("⬇️ $apkLink")
            }
        }.trimEnd()
    }
}