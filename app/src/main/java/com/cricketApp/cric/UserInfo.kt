package com.cricketApp.cric

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
import com.cricketApp.cric.databinding.ActivitySignUpBinding
import com.cricketApp.cric.databinding.ActivityUserInfoBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class UserInfo : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityUserInfoBinding
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
        setContentView(R.layout.activity_user_info)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

            binding = ActivityUserInfoBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()

            // Setup UI Elements
            setupUI()
        }

        private fun setupUI() {
            binding.backBtn.setOnClickListener {
                startActivity(Intent(this@UserInfo, SignIn::class.java))
            }

            binding.profileImgSetup.setOnClickListener {
                pickImageLauncher.launch("image/*")
            }

            setIplTeams()
            setCountries()

            binding.addInfoUserBtn.setOnClickListener {
                if (validateInput()) {
                    updateUserDataToFirebase()
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
                    ""
                }
            }
        }

        private fun validateInput(): Boolean {
            clearErrors()
            var isValid = true

            with(binding) {
                if (usernameTxt.text.isNullOrEmpty()) {
                    usernameTxt.error = "Username cannot be empty"
                    isValid = false
                }

                if (iplTeamTxt.selectedItem == "Select your Team") {
                    errorSpinnerIpl.visibility = View.VISIBLE
                    isValid = false
                }
                if (countryTxt.selectedItem == "Select your Country") {
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
            binding.errorSpinnerIpl.visibility = View.GONE
            binding.errorSpinnerCountry.visibility = View.GONE
        }

        private fun showLoading(show: Boolean) {
            binding.apply {
                progressBar.visibility = if (show) View.VISIBLE else View.INVISIBLE
                creatingAcTxt.visibility = if (show) View.VISIBLE else View.INVISIBLE
                addInfoUserBtn.isEnabled = !show
            }
        }

        private fun updateUserDataToFirebase() {
            showLoading(true)

            coroutineScope.launch {
                try {
                    val userId = auth.currentUser?.uid.toString()
                    val downloadLink = selectedImageUri?.let { uri ->
                        uploadProfilePhoto(uri, userId)
                    } ?: ""

                    val userInfo = getUserData(downloadLink)

                    withContext(Dispatchers.IO) {
                        FirebaseDatabase.getInstance().getReference("Users").child(userId).setValue(userInfo).await()
                    }

                    withContext(Dispatchers.IO) {
                        auth.currentUser?.sendEmailVerification()?.await()
                    }

                    showToast("Info stored successfully")
                    startActivity(Intent(this@UserInfo,Home::class.java))
                    finish()
                } catch (e: Exception) {
                    showToast("Error: ${e.message}")
                    showLoading(false)
                }
            }
        }

        private fun getUserData(profilePhotoUrl: String): Map<String, Any> {
            return hashMapOf(
                "username" to binding.usernameTxt.text.toString(),
                "iplTeam" to binding.iplTeamTxt.selectedItem.toString(),
                "country" to binding.countryTxt.selectedItem.toString(),
                "createdAt" to ServerValue.TIMESTAMP,
                "profilePhoto" to profilePhotoUrl
            )
        }

        private fun setIplTeams() {
            val iplTeams = arrayOf(
                "Select your Team", "MI", "CSK", "DC", "RCB", "KKR", "RR", "PBKS", "SRH", "LSG", "GT"
            )
            val adapter = ArrayAdapter(this,R.layout.spinner_item, iplTeams)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.iplTeamTxt.adapter = adapter
        }

        private fun setCountries() {
            val countries = readCountriesFromFile()
            val adapter = ArrayAdapter(this, R.layout.spinner_item, countries)
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
            Toast.makeText(this@UserInfo, message, Toast.LENGTH_SHORT).show()
        }

        override fun onDestroy() {
            super.onDestroy()
            coroutineScope.cancel()
        }
    }
