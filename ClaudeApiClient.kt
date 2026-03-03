package com.claudeos.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ClaudeApiClient(private val apiKey: String) {

    companion object {
        const val MODEL = "claude-sonnet-4-20250514"
        const val API_URL = "https://api.anthropic.com/v1/messages"
        const val MAX_TOKENS = 1024

        val SYSTEM_PROMPT = """
You are ClaudeOS — the intelligent core of an AI-first Android launcher. You replace the traditional home screen and serve as the user's primary interface with their device.

Your capabilities (via tools):
- Launch any installed app by name
- List installed apps  
- Make phone calls
- Send SMS messages
- Open URLs or perform web searches
- Set alarms
- Open settings screens
- Get device info (battery, time, network)
- Show notifications

Behavior guidelines:
- Be concise and action-oriented. Users want to get things done fast.
- When the user's intent is clear, immediately call the appropriate tool — don't ask for confirmation unless the action is irreversible (like sending a message or making a call).
- After executing an action, briefly confirm what you did in 1-2 sentences.
- If unsure which app to launch, call list_apps first to see what's installed.
- Use natural, conversational language. You're a helpful AI companion on their phone.
- You can also answer questions, help think through problems, write things, etc. — you're a full AI assistant, not just a device controller.
- When the user seems to be making small talk or needs a moment, be warm and engaging.

The current date and device context will be provided when relevant.
        """.trimIndent()
    }

    /**
     * Send a conversation to Claude and get a response.
     * Returns a list of ContentBlock (text and/or tool_use).
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        onStream: ((String) -> Unit)? = null
    ): Result<List<ContentBlock>> = withContext(Dispatchers.IO) {
        try {
            val messagesJson = buildMessagesJson(messages)
            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", MAX_TOKENS)
                put("system", SYSTEM_PROMPT)
                put("tools", ToolDefinitions.allTools)
                put("messages", messagesJson)
            }

            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode == 200) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).readText()
            conn.disconnect()

            if (responseCode != 200) {
                return@withContext Result.failure(
                    Exception("API error $responseCode: $response")
                )
            }

            val json = JSONObject(response)
            val contentBlocks = parseContentBlocks(json.getJSONArray("content"))
            Result.success(contentBlocks)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildMessagesJson(messages: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        for (msg in messages) {
            if (msg.isLoading) continue
            val obj = JSONObject()
            obj.put("role", msg.role)

            when (val content = msg.content) {
                is String -> obj.put("content", content)
                is List<*> -> {
                    val contentArr = JSONArray()
                    for (block in content) {
                        when (block) {
                            is ContentBlock.Text -> {
                                contentArr.put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", block.text)
                                })
                            }
                            is ContentBlock.ToolUse -> {
                                contentArr.put(JSONObject().apply {
                                    put("type", "tool_use")
                                    put("id", block.id)
                                    put("name", block.name)
                                    put("input", block.input)
                                })
                            }
                            is ContentBlock.ToolResult -> {
                                contentArr.put(JSONObject().apply {
                                    put("type", "tool_result")
                                    put("tool_use_id", block.toolUseId)
                                    put("content", block.content)
                                })
                            }
                        }
                    }
                    obj.put("content", contentArr)
                }
                else -> obj.put("content", content.toString())
            }
            arr.put(obj)
        }
        return arr
    }

    private fun parseContentBlocks(arr: JSONArray): List<ContentBlock> {
        val result = mutableListOf<ContentBlock>()
        for (i in 0 until arr.length()) {
            val block = arr.getJSONObject(i)
            when (block.getString("type")) {
                "text" -> result.add(ContentBlock.Text(block.getString("text")))
                "tool_use" -> result.add(
                    ContentBlock.ToolUse(
                        id = block.getString("id"),
                        name = block.getString("name"),
                        input = block.getJSONObject("input")
                    )
                )
            }
        }
        return result
    }
}
