package com.claudeos.launcher

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DeviceToolExecutor(private val context: Context) {

    /**
     * Execute a tool called by Claude and return a result string.
     */
    fun execute(toolName: String, input: JSONObject): String {
        return try {
            when (toolName) {
                "launch_app"       -> launchApp(input.getString("app_name"))
                "list_apps"        -> listApps(input.optString("filter", ""))
                "make_call"        -> makeCall(input.getString("target"))
                "send_sms"         -> sendSms(input.getString("to"), input.optString("body", ""))
                "open_url"         -> openUrl(input.getString("url"), input.optBoolean("is_search", false))
                "set_alarm"        -> setAlarm(input.getInt("hour"), input.getInt("minute"), input.optString("label", ""))
                "open_settings"    -> openSettings(input.getString("section"))
                "get_device_info"  -> getDeviceInfo()
                "send_notification"-> sendNotification(input.getString("title"), input.getString("body"))
                else               -> "Unknown tool: $toolName"
            }
        } catch (e: Exception) {
            "Error executing $toolName: ${e.message}"
        }
    }

    // ─── Tool implementations ──────────────────────────────────────

    private fun launchApp(appName: String): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        // Try exact match first, then fuzzy
        val target = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().equals(appName, ignoreCase = true)
        } ?: apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }

        return if (target != null) {
            val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "Launched ${pm.getApplicationLabel(target)}"
            } else {
                "Found ${pm.getApplicationLabel(target)} but it can't be launched directly"
            }
        } else {
            "App '$appName' not found. Try list_apps to see what's installed."
        }
    }

    private fun listApps(filter: String): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // user apps only
            .map { pm.getApplicationLabel(it).toString() }
            .filter { filter.isEmpty() || it.contains(filter, ignoreCase = true) }
            .sorted()
        
        return if (apps.isEmpty()) {
            "No apps found matching '$filter'"
        } else {
            "Installed apps (${apps.size}): ${apps.joinToString(", ")}"
        }
    }

    private fun makeCall(target: String): String {
        val uri = if (target.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
            Uri.parse("tel:${target.replace(" ", "")}")
        } else {
            Uri.parse("tel:$target")
        }
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Initiating call to $target"
    }

    private fun sendSms(to: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$to")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opened SMS to $to${if (body.isNotEmpty()) " with message pre-filled" else ""}"
    }

    private fun openUrl(url: String, isSearch: Boolean): String {
        val uri = if (isSearch) {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(url)}")
        } else {
            Uri.parse(if (url.startsWith("http")) url else "https://$url")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return if (isSearch) "Searching for: $url" else "Opening $url"
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        val timeStr = String.format("%02d:%02d", hour, minute)
        return "Alarm set for $timeStr${if (label.isNotEmpty()) " — $label" else ""}"
    }

    private fun openSettings(section: String): String {
        val action = when (section) {
            "wifi"          -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth"     -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display"       -> Settings.ACTION_DISPLAY_SETTINGS
            "sound"         -> Settings.ACTION_SOUND_SETTINGS
            "battery"       -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "storage"       -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "apps"          -> Settings.ACTION_APPLICATION_SETTINGS
            "location"      -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "security"      -> Settings.ACTION_SECURITY_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "developer"     -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "about"         -> Settings.ACTION_DEVICE_INFO_SETTINGS
            else            -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return "Opened $section settings"
    }

    private fun getDeviceInfo(): String {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = battery.isCharging

        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        return buildString {
            appendLine("Time: ${timeFormat.format(now.time)}")
            appendLine("Date: ${dateFormat.format(now.time)}")
            appendLine("Battery: $batteryLevel%${if (isCharging) " (charging)" else ""}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE}")
        }.trim()
    }

    private fun sendNotification(title: String, body: String): String {
        val channelId = "claudeos_notifications"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ClaudeOS", NotificationManager.IMPORTANCE_DEFAULT)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            return "Notification permission not granted"
        }
        return "Notification sent: $title"
    }
}
