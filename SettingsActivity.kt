package com.claudeos.launcher

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val apiKeyInput = findViewById<EditText>(R.id.api_key_input)
        val saveButton  = findViewById<Button>(R.id.save_button)
        val backButton  = findViewById<Button>(R.id.back_button)

        // Load current key
        val prefs = getSharedPreferences("claudeos_prefs", MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("api_key", ""))

        saveButton.setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            prefs.edit().putString("api_key", key).apply()
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        backButton.setOnClickListener { finish() }
    }
}
