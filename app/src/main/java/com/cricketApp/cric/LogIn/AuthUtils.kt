package com.cricketApp.cric.Utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.cricketApp.cric.LogIn.SignIn
import com.google.firebase.auth.FirebaseAuth

object AuthUtils {

    /**
     * Check if the user is logged in
     */
    fun isUserLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    /**
     * Get the current user ID, returns null if not logged in
     */
    fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    /**
     * Show login dialog and handle redirection to login screen
     * Returns true if user is already logged in, false otherwise
     */
    fun checkLoginAndPrompt(context: Context, message: String = "You need to login to access this feature"): Boolean {
        if (isUserLoggedIn()) {
            return true
        }

        AlertDialog.Builder(context)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(context, SignIn::class.java)
                context.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()

        return false
    }

    /**
     * Check login with callback for result
     */
    fun checkLoginWithCallback(context: Context, onLoginSuccess: () -> Unit,
                               message: String = "You need to login to access this feature") {
        if (isUserLoggedIn()) {
            onLoginSuccess()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(context, SignIn::class.java)
                if (context is Activity) {
                    context.startActivityForResult(intent, LOGIN_REQUEST_CODE)
                } else {
                    context.startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * For use in fragment onClick events
     */
    fun checkLoginFromFragment(fragment: Fragment, onLoginSuccess: () -> Unit,
                               message: String = "You need to login to access this feature") {
        val context = fragment.context ?: return

        if (isUserLoggedIn()) {
            onLoginSuccess()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Login Required")
            .setMessage(message)
            .setPositiveButton("Login") { _, _ ->
                val intent = Intent(context, SignIn::class.java)
                fragment.startActivityForResult(intent, LOGIN_REQUEST_CODE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Request code for login activity
    const val LOGIN_REQUEST_CODE = 1001
}