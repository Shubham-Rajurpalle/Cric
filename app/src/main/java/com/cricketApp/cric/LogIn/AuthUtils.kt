package com.cricketApp.cric.Utils

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.cricketApp.cric.LogIn.SignIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

object AuthUtils {

    /**
     * Checks if user is authenticated and proceeds with the action if logged in,
     * otherwise shows a login dialog
     */
    fun checkAuthAndProceed(fragment: Fragment, action: () -> Unit) {
        if (FirebaseAuth.getInstance().currentUser != null) {
            // User is logged in, proceed with action
            action.invoke()
        } else {
            // User is not logged in, show login dialog
            showLoginRequiredDialog(fragment)
        }
    }

    /**
     * Shows a dialog prompting the user to log in or cancel
     */
    private fun showLoginRequiredDialog(fragment: Fragment) {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle("Login Required")
            .setMessage("You need to log in to use this feature.")
            .setPositiveButton("Log In") { _, _ ->
                // Navigate to login screen with current class name as return destination
                val intent = Intent(context, SignIn::class.java)
                intent.putExtra("returnTo", fragment.requireActivity().javaClass.name)
                fragment.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Saves the login state to shared preferences
     */
    fun saveLoginState(context: Context, isLoggedIn: Boolean) {
        val sharedRef = context.getSharedPreferences("user_login_info", Context.MODE_PRIVATE)
        val editor = sharedRef.edit()
        editor.putBoolean("isLoggedIn", isLoggedIn)
        editor.apply()
    }
}