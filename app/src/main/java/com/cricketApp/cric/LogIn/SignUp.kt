package com.cricketApp.cric.LogIn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivitySignUpBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.InputStreamReader

class SignUp : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignUpBinding
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var selectedImageUri: Uri? = null
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                binding.profileImg.setImageURI(it)
                selectedImageUri = it
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        FirebaseApp.initializeApp(this)

        // Setup UI Elements
        setupUI()
    }

    private fun setupUI() {
        binding.LogInBtn.setOnClickListener {
            startActivity(Intent(this@SignUp, SignIn::class.java))
        }

        binding.profileImgSetup.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        setIplTeams()
        setCountries()

        binding.backBtn.setOnClickListener {
            startActivity(Intent(this@SignUp, SignIn::class.java))
        }

        binding.privacyPolicyTxt.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/cricpolicies/privacy-policy")))
        }

        binding.termsConditionsTxt.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/cricpolicies/terms-and-conditions")))
        }

        binding.signUpBtn.setOnClickListener {
            val email = binding.emailTxt.text.toString()
            val password = binding.passwordTxt.text.toString()

            if (validateInput()) {
                createUserWithEmailPassword(email, password)
            }
        }
    }

    private suspend fun uploadProfilePhoto(uri: Uri, userId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val storageReference = FirebaseStorage.getInstance().reference
                val profileImageReference = storageReference.child("profile_images/$userId.jpg")

                profileImageReference.putFile(uri).await()
                val downloadUrl = profileImageReference.downloadUrl.await()
                downloadUrl.toString()
            } catch (e: Exception) {
                Log.e("SignUp", "Profile photo upload failed", e)
                ""
            }
        }
    }

    private fun validateInput(): Boolean {
        clearErrors()
        var isValid = true

        with(binding) {
            if (emailTxt.text.isNullOrEmpty()) {
                emailTxt.error = "Email cannot be empty"
                isValid = false
            }
            if (passwordTxt.text.isNullOrEmpty()) {
                passwordTxt.error = "Password cannot be empty"
                isValid = false
            }
            if (confirmPasswordTxt.text.toString() != passwordTxt.text.toString()) {
                confirmPasswordTxt.error = "Passwords do not match"
                isValid = false
            }
            if (usernameTxt.text.isNullOrEmpty()) {
                usernameTxt.error = "Username cannot be empty"
                isValid = false
            }
            if (iplTeamTxt.selectedItem == "eg.RCB") {
                errorSpinnerIpl.visibility = View.VISIBLE
                isValid = false
            }
            if (countryTxt.selectedItem == "eg.India") {
                errorSpinnerCountry.visibility = View.VISIBLE
                isValid = false
            }

            val defaultProfilePhoto = resources.getDrawable(R.drawable.profile_empty, null)
            if (profileImg.drawable.constantState == defaultProfilePhoto.constantState) {
                profileImg.setBackgroundResource(R.drawable.error_border)
                showToast("Please select a profile photo")
                isValid = false
            } else {
                profileImg.setBackgroundResource(0)
            }
        }

        return isValid
    }

    private fun clearErrors() {
        binding.usernameTxt.error = null
        binding.emailTxt.error = null
        binding.passwordTxt.error = null
        binding.confirmPasswordTxt.error = null
        binding.errorSpinnerIpl.visibility = View.GONE
        binding.errorSpinnerCountry.visibility = View.GONE
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            progressBar.visibility = if (show) View.VISIBLE else View.INVISIBLE
            creatingAcTxt.visibility = if (show) View.VISIBLE else View.INVISIBLE
            signUpBtn.isEnabled = !show
        }
    }

    private fun createUserWithEmailPassword(email: String, password: String) {
        showLoading(true)

        coroutineScope.launch {
            try {
                val authResult = withContext(Dispatchers.IO) {
                    auth.createUserWithEmailAndPassword(email, password).await()
                }

                val userId = authResult.user?.uid ?: throw Exception("User Id is null")
                val downloadLink = selectedImageUri?.let { uri ->
                    uploadProfilePhoto(uri, userId)
                } ?: ""

                val userInfo = getUserData(email, downloadLink)

                withContext(Dispatchers.IO) {
                    FirebaseDatabase.getInstance().getReference("Users").child(userId).setValue(userInfo).await()
                }

                withContext(Dispatchers.IO) {
                    auth.currentUser?.sendEmailVerification()?.await()
                }

                showToast("Account created successfully! Please verify your email.")
                navigateToSignIn()
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                showLoading(false)
            }
        }
    }

    private fun getUserData(email: String, profilePhotoUrl: String): Map<String, Any> {
        return hashMapOf(
            "email" to email,
            "username" to binding.usernameTxt.text.toString(),
            "iplTeam" to binding.iplTeamTxt.selectedItem.toString(),
            "country" to binding.countryTxt.selectedItem.toString(),
            "createdAt" to ServerValue.TIMESTAMP,
            "profilePhoto" to profilePhotoUrl
        )
    }

    private fun setIplTeams() {
        val iplTeams = mutableListOf(
            "Select your team", "None", "MI", "CSK", "DC", "RCB", "KKR", "RR", "PBKS", "SRH", "LSG", "GT"
        )
        val adapter = CustomSpinnerAdapter(this, R.layout.spinner_item, iplTeams)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.iplTeamTxt.adapter = adapter
    }

    private fun setCountries() {
        val countries = readCountriesFromFile()
        val adapter = CustomSpinnerAdapter(this, R.layout.spinner_item, countries)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.countryTxt.adapter = adapter
    }

    private fun readCountriesFromFile(): MutableList<String> {
        val countries = mutableListOf<String>()
        try {
            val inputStream = assets.open("countries.json")
            val reader = InputStreamReader(inputStream)
            val gson = Gson()
            countries.addAll(gson.fromJson(reader, Array<String>::class.java).toList())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return countries
    }

    private fun showToast(message: String) {
        Toast.makeText(this@SignUp, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToSignIn() {
        startActivity(Intent(this@SignUp, SignIn::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
