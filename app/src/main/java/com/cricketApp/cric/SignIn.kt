package com.cricketApp.cric

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cricketApp.cric.databinding.ActivitySignInBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SignIn : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySignInBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_in)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //Initialization
        auth=FirebaseAuth.getInstance()
        binding= ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Create Account
        binding.createAccountBtn.setOnClickListener {
            startActivity(Intent(this@SignIn,SignUp::class.java))
        }

        //Forgot Password
        binding.forgotPasswordBtn.setOnClickListener {
            startActivity(Intent(this@SignIn,forgotPassword::class.java))
        }

        binding.logInBtn.setOnClickListener{
            binding.progressBar.visibility= View.VISIBLE
            binding.creatingAcTxt.visibility=View.VISIBLE

            val email=binding.emailTxtLogIn.text.toString()
            val password=binding.passwordTxtLogIn.text.toString()

            if(email.isEmpty()){
                showError(binding.emailTxtLogIn,"Email cannot be empty")
                return@setOnClickListener
            }

            if(password.isEmpty()){
                showError(binding.passwordTxtLogIn,"Password cannot be empty")
                return@setOnClickListener
            }

            if(email.isNotEmpty()&&password.isNotEmpty()){
                userLogin(email,password)
            }

        }
    }

    private fun showError(itemView: EditText,errorMessage: String){
        itemView.error=errorMessage
    }

    private fun userLogin(email:String,password:String){
        auth.signInWithEmailAndPassword(email,password).
                addOnCompleteListener{task->
                    binding.progressBar.visibility=View.INVISIBLE
                    binding.creatingAcTxt.visibility=View.INVISIBLE
                    if (task.isSuccessful){
                        if (auth.currentUser?.isEmailVerified == true) {
                            checkUserInfo()
                        } else {
                            Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show()
                        }
                    }else{
                        Toast.makeText(
                            baseContext,
                            "Authentication failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
    }

    private fun checkUserInfo(){
        val userId=auth.currentUser?.uid?:return
        val userRef=FirebaseDatabase.getInstance().getReference("Users").child(userId)

        userRef.get().addOnSuccessListener { snapshot->
            if(snapshot.exists()){
                val username=snapshot.child("username").getValue(String::class.java)
                val country=snapshot.child("country").getValue(String::class.java)
                val iplTeam=snapshot.child("iplTeam").getValue(String::class.java)
                val profilePhoto=snapshot.child("profilePhoto").getValue(String::class.java)

                if(username.isNullOrEmpty()||country.isNullOrEmpty()||iplTeam.isNullOrEmpty()||profilePhoto.isNullOrEmpty()){
                    startActivity(Intent(this@SignIn,UserInfo::class.java))
                }else{
                    saveLoginState(true)
                    startActivity(Intent(this@SignIn,Home::class.java))
                }
            }else{
                startActivity(Intent(this@SignIn,UserInfo::class.java))
            }
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to retrieve user data.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLoginState(isLoggedIn:Boolean){
        val sharedRef=getSharedPreferences("user_login_info", MODE_PRIVATE)
        val editor=sharedRef.edit()
        editor.putBoolean("isLoggedIn",isLoggedIn)
        editor.apply()
    }

}