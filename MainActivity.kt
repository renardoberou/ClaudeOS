package com.claudeos.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var orbView: View
    private lateinit var statusText: TextView

    // Core
    private lateinit var adapter: ChatAdapter
    private lateinit var toolExecutor: DeviceToolExecutor
    private var claudeClient: ClaudeApiClient? = null
    private val messages = mutableListOf<ChatMessage>()
    private val clockHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val PREFS = "claudeos_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val PERMS_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContentView(R.layout.activity_main)

        // Init components
        toolExecutor = DeviceToolExecutor(this)
        initViews()
        startClock()
        loadApiKey()
        requestPermissions()
    }

    private fun initViews() {
        clockText    = findViewById(R.id.clock_text)
        dateText     = findViewById(R.id.date_text)
        orbView      = findViewById(R.id.ai_orb)
        statusText   = findViewById(R.id.status_text)
        recyclerView = findViewById(R.id.chat_recycler)
        inputField   = findViewById(R.id.input_field)
        sendButton   = findViewById(R.id.send_button)

        adapter = ChatAdapter(messages)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        sendButton.setOnClickListener { sendMessage() }
        inputField.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                sendMessage(); true
            } else false
        }
    }

    private fun sendMessage() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        inputField.setText("")

        addUserMessage(text)
        runAgentLoop()
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(role = "user", content = text))
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
    }

    private fun runAgentLoop() {
        val client = claudeClient
        if (client == null) {
            promptForApiKey()
            return
        }

        // Show loading
        val loadingMsg = ChatMessage(role = "assistant", content = "…", isLoading = true)
        messages.add(loadingMsg)
        val loadingIndex = messages.size - 1
        adapter.notifyItemInserted(loadingIndex)
        scrollToBottom()
        setOrbActive(true)

        lifecycleScope.launch {
            // Agentic loop: keep calling until no more tool_use
            var continueLoop = true
            while (continueLoop) {
                val result = client.chat(messages.filter { !it.isLoading })

                // Remove loading indicator
                val li = messages.indexOfFirst { it.isLoading }
                if (li >= 0) {
                    messages.removeAt(li)
                    adapter.notifyItemRemoved(li)
                }

                result.fold(
                    onSuccess = { blocks ->
                        val toolUseBlocks = blocks.filterIsInstance<ContentBlock.ToolUse>()
                        val textBlocks    = blocks.filterIsInstance<ContentBlock.Text>()

                        if (toolUseBlocks.isEmpty()) {
                            // No more tools — show final response
                            continueLoop = false
                            val text = textBlocks.joinToString("\n") { it.text }
                            if (text.isNotEmpty()) {
                                messages.add(ChatMessage(role = "assistant", content = text))
                                adapter.notifyItemInserted(messages.size - 1)
                                scrollToBottom()
                            }
                        } else {
                            // Store assistant message (with tool_use blocks)
                            messages.add(ChatMessage(role = "assistant", content = blocks))

                            // Execute each tool and collect results
                            val toolResultBlocks = mutableListOf<ContentBlock>()
                            for (toolUse in toolUseBlocks) {
                                showStatus("Running: ${toolUse.name.replace("_", " ")}…")
                                val resultStr = toolExecutor.execute(toolUse.name, toolUse.input)
                                toolResultBlocks.add(
                                    ContentBlock.ToolResult(
                                        toolUseId = toolUse.id,
                                        content = resultStr
                                    )
                                )
                            }

                            // Add tool results as user message (as per Anthropic API spec)
                            messages.add(ChatMessage(role = "user", content = toolResultBlocks))

                            // Show loading again for next iteration
                            val lm = ChatMessage(role = "assistant", content = "…", isLoading = true)
                            messages.add(lm)
                            adapter.notifyItemInserted(messages.size - 1)
                            scrollToBottom()
                        }
                    },
                    onFailure = { error ->
                        continueLoop = false
                        val errText = when {
                            error.message?.contains("401") == true ->
                                "Invalid API key. Tap here to update it."
                            error.message?.contains("429") == true ->
                                "Rate limit reached. Please wait a moment."
                            error.message?.contains("network") == true ||
                            error.message?.contains("connect") == true ->
                                "Can't connect. Check your internet connection."
                            else -> "Error: ${error.message}"
                        }
                        messages.add(ChatMessage(role = "assistant", content = errText))
                        adapter.notifyItemInserted(messages.size - 1)
                        scrollToBottom()
                    }
                )
            }

            showStatus("")
            setOrbActive(false)
        }
    }

    // ─── Clock ──────────────────────────────────────────────────

    private val clockRunnable = object : Runnable {
        override fun run() {
            val cal = Calendar.getInstance()
            val timeFormat = if (DateFormat.is24HourFormat(this@MainActivity))
                "%02d:%02d" else "%d:%02d"
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min  = cal.get(Calendar.MINUTE)
            clockText.text = if (DateFormat.is24HourFormat(this@MainActivity))
                String.format("%02d:%02d", hour, min)
            else {
                val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                "$h:${String.format("%02d", min)} $ampm"
            }
            val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)
            dateText.text = "${dayNames[dow]}, ${monthNames[month]} $day"
            clockHandler.postDelayed(this, 1000)
        }
    }

    private fun startClock() = clockHandler.post(clockRunnable)

    // ─── API Key ─────────────────────────────────────────────────

    private fun loadApiKey() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val key = prefs.getString(KEY_API_KEY, "") ?: ""
        if (key.isNotEmpty()) {
            claudeClient = ClaudeApiClient(key)
            showWelcome()
        } else {
            promptForApiKey()
        }
    }

    private fun promptForApiKey() {
        val dialog = android.app.AlertDialog.Builder(this, R.style.ClaudeOSDialog)
            .setTitle("Enter Anthropic API Key")
            .setMessage("Get your key at console.anthropic.com")
            .apply {
                val input = EditText(this@MainActivity).apply {
                    hint = "sk-ant-..."
                    inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or
                                android.text.InputType.TYPE_CLASS_TEXT
                    setPadding(48, 32, 48, 32)
                }
                setView(input)
                setPositiveButton("Save") { _, _ ->
                    val key = input.text.toString().trim()
                    if (key.isNotEmpty()) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_API_KEY, key).apply()
                        claudeClient = ClaudeApiClient(key)
                        showWelcome()
                    }
                }
                setNegativeButton("Cancel", null)
            }.create()
        dialog.show()
    }

    private fun showWelcome() {
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    role = "assistant",
                    content = "Hi! I'm ClaudeOS — your AI-powered launcher. Tell me what you want to do, open, or find. Try: "Open Spotify", "Set an alarm for 7am", or just ask me anything."
                )
            )
            adapter.notifyItemInserted(0)
        }
    }

    // ─── UI helpers ──────────────────────────────────────────────

    private fun scrollToBottom() {
        recyclerView.post {
            adapter.itemCount.let { count ->
                if (count > 0) recyclerView.scrollToPosition(count - 1)
            }
        }
    }

    private fun setOrbActive(active: Boolean) {
        orbView.animate()
            .alpha(if (active) 1f else 0.6f)
            .scaleX(if (active) 1.15f else 1f)
            .scaleY(if (active) 1.15f else 1f)
            .setDuration(300)
            .start()
    }

    private fun showStatus(text: String) {
        runOnUiThread {
            statusText.text = text
            statusText.visibility = if (text.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
    }

    // ─── Permissions ─────────────────────────────────────────────

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        val perms = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS
        )
        perms.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needed.add(it)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMS_REQUEST)
        }
    }

    override fun onBackPressed() {
        // Swallow back press — we're the launcher
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    // ─── Settings shortcut (long-press orb) ──────────────────────
    fun onOrbLongClick(view: View) {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
}
