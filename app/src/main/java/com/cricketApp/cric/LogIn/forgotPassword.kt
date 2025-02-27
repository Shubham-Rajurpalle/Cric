package com.cricketApp.cric.LogIn

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.ActivityForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth

class forgotPassword : AppCompatActivity() {

    private lateinit var binding:ActivityForgotPasswordBinding
    private lateinit var auth:FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding=ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth=FirebaseAuth.getInstance()

        //Back Btn
        binding.backBtn.setOnClickListener {
            startActivity(Intent(this@forgotPassword, SignIn::class.java))
        }

        binding.resetPassword.setOnClickListener{
            val email=binding.emailTxt.text.toString()
            if(email.isEmpty()){
                binding.emailTxt.error="Email is required"
                return@setOnClickListener
            }

            resetPassword(email)
        }
    }

    private fun resetPassword(email:String) {
        binding.progressBar.visibility= View.VISIBLE
        binding.resetPassword.isEnabled=false

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener{task->

                binding.progressBar.visibility= View.INVISIBLE
                binding.resetPassword.isEnabled=true

                if(task.isSuccessful){
                    Toast.makeText(this, "Reset link sent to your email!", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@forgotPassword, SignIn::class.java))
                    finish()
                }else{
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}