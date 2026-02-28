package com.cricketApp.cric.LogIn

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivitySignInBinding
import com.cricketApp.cric.home.Home
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class SignIn : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignInBinding
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        binding.googleLoginBtn.setOnClickListener { signInWithGoogle() }

        binding.logInBtn.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            binding.creatingAcTxt.visibility = View.VISIBLE
            val email = binding.emailTxtLogIn.text.toString()
            val password = binding.passwordTxtLogIn.text.toString()
            if (email.isEmpty()) { showError(binding.emailTxtLogIn, "Email cannot be empty"); return@setOnClickListener }
            if (password.isEmpty()) { showError(binding.passwordTxtLogIn, "Password cannot be empty"); return@setOnClickListener }
            userLogin(email, password)
        }

        binding.createAccountBtn.setOnClickListener { startActivity(Intent(this, SignUp::class.java)) }
        binding.forgotPasswordBtn.setOnClickListener { startActivity(Intent(this, forgotPassword::class.java)) }

        binding.progressBar.visibility = View.INVISIBLE
        binding.creatingAcTxt.visibility = View.INVISIBLE
    }

    // ── Google Sign-In: fast path first, fallback to full picker ─────────────

    private fun signInWithGoogle() {
        binding.progressBar.visibility = View.VISIBLE
        binding.creatingAcTxt.visibility = View.VISIBLE
        binding.creatingAcTxt.text = "Signing in with Google..."

        lifecycleScope.launch {
            val usedFastPath = tryGoogleIdOption()
            if (!usedFastPath) trySignInWithGoogleOption()
        }
    }

    /** Fast path — for accounts that previously signed in */
    private suspend fun tryGoogleIdOption(): Boolean {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

            val result = credentialManager.getCredential(
                request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build(),
                context = this@SignIn
            )
            handleGoogleCredential(result.credential)
            true
        } catch (e: GetCredentialException) {
            false // fall through to full picker
        }
    }

    /** Fallback — always shows full Google account picker */
    private suspend fun trySignInWithGoogleOption() {
        try {
            val signInWithGoogleOption = GetSignInWithGoogleOption
                .Builder(getString(R.string.default_web_client_id))
                .build()

            val result = credentialManager.getCredential(
                request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build(),
                context = this@SignIn
            )
            handleGoogleCredential(result.credential)
        } catch (e: GetCredentialException) {
            binding.progressBar.visibility = View.INVISIBLE
            binding.creatingAcTxt.visibility = View.INVISIBLE
            Toast.makeText(this, "Google sign-in failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGoogleCredential(credential: androidx.credentials.Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                binding.progressBar.visibility = View.INVISIBLE
                binding.creatingAcTxt.visibility = View.INVISIBLE
                Toast.makeText(this, "Invalid Google ID token.", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.progressBar.visibility = View.INVISIBLE
            binding.creatingAcTxt.visibility = View.INVISIBLE
            Toast.makeText(this, "Unexpected credential type.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    binding.creatingAcTxt.text = "Checking account information..."
                    checkUserInfo()
                } else {
                    binding.progressBar.visibility = View.INVISIBLE
                    binding.creatingAcTxt.visibility = View.INVISIBLE
                    Toast.makeText(this, "Authentication Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // ── Email/Password ────────────────────────────────────────────────────────

    private fun showError(itemView: EditText, errorMessage: String) {
        itemView.error = errorMessage
        binding.progressBar.visibility = View.INVISIBLE
        binding.creatingAcTxt.visibility = View.INVISIBLE
    }

    private fun userLogin(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.progressBar.visibility = View.INVISIBLE
                binding.creatingAcTxt.visibility = View.INVISIBLE
                if (task.isSuccessful) {
                    if (auth.currentUser?.isEmailVerified == true) {
                        checkUserInfo()
                    } else {
                        Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // ── User info check ───────────────────────────────────────────────────────

    private fun checkUserInfo() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            binding.progressBar.visibility = View.VISIBLE
            binding.creatingAcTxt.visibility = View.VISIBLE
            binding.creatingAcTxt.text = "Checking account information..."

            if (snapshot.exists()) {
                val username = snapshot.child("username").getValue(String::class.java)
                val country = snapshot.child("country").getValue(String::class.java)
                val iplTeam = snapshot.child("iplTeam").getValue(String::class.java)
                val profilePhoto = snapshot.child("profilePhoto").getValue(String::class.java)

                if (username.isNullOrEmpty() || country.isNullOrEmpty() ||
                    iplTeam.isNullOrEmpty() || profilePhoto.isNullOrEmpty()
                ) {
                    startActivity(Intent(this@SignIn, UserInfo::class.java))
                } else {
                    saveLoginState(true)
                    startActivity(Intent(this@SignIn, Home::class.java))
                }
            } else {
                startActivity(Intent(this@SignIn, UserInfo::class.java))
            }
            finish()
        }.addOnFailureListener {
            binding.progressBar.visibility = View.INVISIBLE
            binding.creatingAcTxt.visibility = View.INVISIBLE
            Toast.makeText(this, "Failed to retrieve user data.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLoginState(isLoggedIn: Boolean) {
        getSharedPreferences("user_login_info", MODE_PRIVATE)
            .edit().putBoolean("isLoggedIn", true).apply()
    }
}