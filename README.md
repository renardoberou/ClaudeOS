# ClaudeOS — AI-First Android Launcher

**An Android home screen powered by Claude as the central UI.**  
Instead of an icon grid, you get a conversational AI that controls your entire phone.

---

## What It Does

- Replaces your Android home screen
- Lets you control your phone entirely through natural language
- Claude uses **tool_use** to execute device actions autonomously
- Keeps full conversation context so Claude knows what you've been doing

### Capabilities

| Command | Example |
|---------|---------|
| **Open apps** | "Open Spotify" / "Launch Maps" |
| **Make calls** | "Call Mom" / "Call 555-1234" |
| **Send texts** | "Text Sarah that I'll be 10 min late" |
| **Set alarms** | "Wake me at 7am" |
| **Web search** | "Search for Italian restaurants near me" |
| **Open URLs** | "Go to github.com" |
| **Settings** | "Turn on WiFi" / "Open Bluetooth settings" |
| **Device info** | "What's my battery level?" |
| **Notifications** | "Remind me about the meeting" |
| **General AI** | Ask anything — Claude is fully capable |

---

## Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+ (Android 8.0)
- An [Anthropic API key](https://console.anthropic.com)

### 1. Clone / Open Project
Open the `ClaudeOS` folder in Android Studio.

### 2. Fonts (Optional but recommended)
Download [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) and place the TTF files in `app/src/main/res/font/`:
- `space_grotesk_light.ttf`
- `space_grotesk_regular.ttf`
- `space_grotesk_medium.ttf`

If you skip this, Android will fall back to the default system font — still functional.

### 3. Build & Install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
Or press **Run ▶** in Android Studio.

### 4. Set as Default Launcher
After install, press the home button → "Always" → select **ClaudeOS**.

### 5. Enter API Key
On first launch, enter your Anthropic API key (`sk-ant-...`).  
You can update it anytime by long-pressing the orb → Settings.

---

## Architecture

```
MainActivity.kt        — UI, clock, message dispatch
ClaudeApiClient.kt     — Anthropic Messages API + tool_use
DeviceToolExecutor.kt  — Executes device actions from tool calls
ChatAdapter.kt         — RecyclerView adapter for messages
Models.kt              — Data models & tool definitions
```

### Agentic Loop
```
User types message
    → Add to conversation history
    → Call Claude API with full history + tool definitions
    → If response has tool_use blocks:
        → Execute each tool on device
        → Add tool_results to history
        → Call Claude API again (loop)
    → If response is text only: display to user
```

---

## Permissions Required

| Permission | Used For |
|-----------|---------|
| INTERNET | Claude API calls |
| CALL_PHONE | Making phone calls |
| READ_CONTACTS | Contact lookup for calls/SMS |
| SEND_SMS | Pre-filling SMS app |
| SET_ALARM | Setting alarms via AlarmClock intent |
| QUERY_ALL_PACKAGES | Listing/launching installed apps |

---

## Customization

### System Prompt
Edit `ClaudeApiClient.kt → SYSTEM_PROMPT` to customize Claude's personality and behavior.

### Adding New Tools
1. Add tool definition in `Models.kt → ToolDefinitions`
2. Add execution handler in `DeviceToolExecutor.kt → execute()`
3. Add any required permissions in `AndroidManifest.xml`

### UI Colors
All colors in `res/values/colors.xml`. Currently uses a deep navy/black with cyan accent.

---

## Notes

- **API costs**: Every message makes an API call. Use Claude Haiku for cheaper, faster responses by changing `MODEL` in `ClaudeApiClient.kt`.
- **No offline mode**: Requires internet for AI responses. Device actions (once initiated) work offline.
- **Privacy**: Conversation history stays in memory only and is not persisted to disk.
- **Rate limits**: Heavy use may hit Anthropic rate limits.

---

## Roadmap Ideas

- [ ] Voice input (SpeechRecognizer API)  
- [ ] Widget-style quick actions bar  
- [ ] Conversation history persistence  
- [ ] Proactive suggestions based on time/context  
- [ ] Contact integration for smarter calling/messaging  
- [ ] Custom wake word  
- [ ] Wear OS companion  

---

Built with ❤️ using Anthropic's Claude API.
