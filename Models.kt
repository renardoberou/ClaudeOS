package com.claudeos.launcher

import org.json.JSONArray
import org.json.JSONObject

// ─── Message models ───────────────────────────────────────────────

data class ChatMessage(
    val role: String,          // "user" | "assistant"
    val content: Any,          // String or List<ContentBlock>
    val isLoading: Boolean = false
)

sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class ToolUse(val id: String, val name: String, val input: JSONObject) : ContentBlock()
    data class ToolResult(val toolUseId: String, val content: String) : ContentBlock()
}

// ─── Tool definitions sent to Claude API ─────────────────────────

object ToolDefinitions {

    val allTools: JSONArray get() = JSONArray().apply {
        put(launchApp)
        put(listApps)
        put(makeCall)
        put(sendSms)
        put(openUrl)
        put(setAlarm)
        put(openSettings)
        put(getDeviceInfo)
        put(sendNotification)
    }

    private val launchApp = JSONObject("""
    {
      "name": "launch_app",
      "description": "Launch an installed app on the device by its name or package. Use this whenever the user wants to open an application.",
      "input_schema": {
        "type": "object",
        "properties": {
          "app_name": {
            "type": "string",
            "description": "The common name of the app, e.g. 'Gmail', 'Spotify', 'Chrome', 'Camera', 'Maps'"
          }
        },
        "required": ["app_name"]
      }
    }
    """)

    private val listApps = JSONObject("""
    {
      "name": "list_apps",
      "description": "Get a list of all apps installed on the device. Use this to help the user discover what's available or find a specific app.",
      "input_schema": {
        "type": "object",
        "properties": {
          "filter": {
            "type": "string",
            "description": "Optional search filter to narrow down apps by name"
          }
        }
      }
    }
    """)

    private val makeCall = JSONObject("""
    {
      "name": "make_call",
      "description": "Initiate a phone call to a number or contact name.",
      "input_schema": {
        "type": "object",
        "properties": {
          "target": {
            "type": "string",
            "description": "Phone number or contact name to call"
          }
        },
        "required": ["target"]
      }
    }
    """)

    private val sendSms = JSONObject("""
    {
      "name": "send_sms",
      "description": "Open the SMS app pre-filled with a recipient and message body.",
      "input_schema": {
        "type": "object",
        "properties": {
          "to": { "type": "string", "description": "Recipient phone number or contact name" },
          "body": { "type": "string", "description": "Message text" }
        },
        "required": ["to"]
      }
    }
    """)

    private val openUrl = JSONObject("""
    {
      "name": "open_url",
      "description": "Open a URL in the browser, or perform a web search.",
      "input_schema": {
        "type": "object",
        "properties": {
          "url": { "type": "string", "description": "Full URL or search query" },
          "is_search": { "type": "boolean", "description": "If true, treat as a Google search query" }
        },
        "required": ["url"]
      }
    }
    """)

    private val setAlarm = JSONObject("""
    {
      "name": "set_alarm",
      "description": "Set an alarm at a specific time.",
      "input_schema": {
        "type": "object",
        "properties": {
          "hour": { "type": "integer", "description": "Hour in 24-hour format (0-23)" },
          "minute": { "type": "integer", "description": "Minute (0-59)" },
          "label": { "type": "string", "description": "Optional alarm label" }
        },
        "required": ["hour", "minute"]
      }
    }
    """)

    private val openSettings = JSONObject("""
    {
      "name": "open_settings",
      "description": "Open a specific Android settings screen.",
      "input_schema": {
        "type": "object",
        "properties": {
          "section": {
            "type": "string",
            "enum": ["wifi", "bluetooth", "display", "sound", "battery", "storage", "apps", "location", "security", "accessibility", "developer", "about", "main"],
            "description": "The settings section to open"
          }
        },
        "required": ["section"]
      }
    }
    """)

    private val getDeviceInfo = JSONObject("""
    {
      "name": "get_device_info",
      "description": "Get current device information: battery level, time, date, network status.",
      "input_schema": {
        "type": "object",
        "properties": {}
      }
    }
    """)

    private val sendNotification = JSONObject("""
    {
      "name": "send_notification",
      "description": "Show a local notification on the device.",
      "input_schema": {
        "type": "object",
        "properties": {
          "title": { "type": "string" },
          "body": { "type": "string" }
        },
        "required": ["title", "body"]
      }
    }
    """)
}
