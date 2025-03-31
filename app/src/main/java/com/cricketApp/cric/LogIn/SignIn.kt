package com.cricketApp.cric.LogIn

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cricketApp.cric.home.Home
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivitySignInBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase


class SignIn : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignInBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Use ViewBinding properly
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Ensure this is correct
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Register Google Sign-In launcher
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Google Sign-In Button Click
        binding.googleLoginBtn.setOnClickListener {
            signInWithGoogle()
        }

        // Email/Password Sign-In
        binding.logInBtn.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            binding.creatingAcTxt.visibility = View.VISIBLE

            val email = binding.emailTxtLogIn.text.toString()
            val password = binding.passwordTxtLogIn.text.toString()

            if (email.isEmpty()) {
                showError(binding.emailTxtLogIn, "Email cannot be empty")
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                showError(binding.passwordTxtLogIn, "Password cannot be empty")
                return@setOnClickListener
            }

            userLogin(email, password)
        }

        // Navigation Buttons
        binding.createAccountBtn.setOnClickListener {
            startActivity(Intent(this@SignIn, SignUp::class.java))
        }

        binding.forgotPasswordBtn.setOnClickListener {
            startActivity(Intent(this@SignIn, forgotPassword::class.java))
        }

        // Check if user is already signed in
        checkExistingGoogleUser()
    }

    private fun checkExistingGoogleUser() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            binding.progressBar.visibility = View.INVISIBLE
            binding.creatingAcTxt.visibility = View.INVISIBLE
            checkUserInfo()
        } else {
            binding.progressBar.visibility = View.INVISIBLE
            binding.creatingAcTxt.visibility = View.INVISIBLE
        }
    }

    private fun showError(itemView: EditText, errorMessage: String) {
        itemView.error = errorMessage
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
                    Toast.makeText(
                        baseContext,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
    }

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

                if (username.isNullOrEmpty() || country.isNullOrEmpty() || iplTeam.isNullOrEmpty() || profilePhoto.isNullOrEmpty()) {
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
            // Also hide progress bar on failure
            binding.progressBar.visibility = View.INVISIBLE
            binding.creatingAcTxt.visibility = View.INVISIBLE

            Toast.makeText(this, "Failed to retrieve user data.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLoginState(isLoggedIn: Boolean) {
        val sharedRef = getSharedPreferences("user_login_info", MODE_PRIVATE)
        val editor = sharedRef.edit()
        editor.putBoolean("isLoggedIn", isLoggedIn)
        editor.apply()
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.creatingAcTxt.visibility = View.VISIBLE
                    binding.creatingAcTxt.text = "Signing in with Google..."
                    checkUserInfo()
                } else {
                    Toast.makeText(this, "Authentication Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}