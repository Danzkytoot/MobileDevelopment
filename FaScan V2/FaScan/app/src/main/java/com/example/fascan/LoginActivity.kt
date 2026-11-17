package com.example.fascan

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in? Skip the login screen.
        if (Store.currentUser(this) != null) {
            openMain(); return
        }
        setContentView(R.layout.activity_login)

        val etId = findViewById<EditText>(R.id.etId)
        val etPw = findViewById<EditText>(R.id.etPassword)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val id = etId.text.toString().trim()
            val pw = etPw.text.toString()
            when {
                id.isEmpty() || pw.isEmpty() ->
                    Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show()
                id == "admin" && pw == "admin123" -> {   // demo credentials only
                    Store.login(this, id)
                    Store.addHistory(this, "Logged in as $id")
                    openMain()
                }
                else -> etPw.error = "Invalid ID number or password"
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
