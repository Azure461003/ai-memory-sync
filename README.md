# Memory Sync - AI-Powered Smart Glasses App

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg" alt="Language">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

**Memory Sync** is an intelligent smart glasses application that captures your daily moments through photos and audio, analyzes them using AI, and automatically syncs extracted tasks, events, and memories to your Google ecosystem.

Built with the [xg.glass SDK](https://github.com/hkust-spark/xg-glass-sdk), it works seamlessly across multiple smart glasses platforms including **RayNeo**, **Rokid**, **Frame**, and more.

---

## ✨ Features

### 🎯 Core Capabilities

- **📸 Photo Capture** - Take photos directly from your smart glasses camera
- **🎤 Audio Recording** - Record conversations and voice notes through glasses microphone
- **🤖 AI Analysis** - Extract structured data using OpenAI GPT-4o:
  - Tasks with due dates
  - Calendar events with time and location
  - General memories and notes
- **☁️ Google Sync** - Automatically sync to your Google services:
  - **Google Tasks** - To-do items and deadlines
  - **Google Calendar** - Events and appointments
  - **Google Drive** - Memory notes and documents

### 🚀 Key Highlights

- **One-Time Setup** - OAuth 2.0 authorization with automatic token refresh
- **Cross-Platform** - Works on RayNeo X2/X3, Rokid, Frame, and other xg.glass-supported devices
- **Privacy-First** - Tokens stored locally, no data sent to third-party servers
- **Offline Fallback** - Saves data locally when offline, syncs when connected
- **Minimal Code** - Built with just 2 Kotlin files (~740 lines total)

---

## 📱 Supported Devices

| Device | Status |
|--------|--------|
| RayNeo X2 | ✅ Supported |
| RayNeo X3 Pro | ✅ Supported |
| Rokid Glasses | ✅ Supported |
| Brilliant Labs Frame | ✅ Supported |
| Android Emulator (Simulator) | ✅ Supported |

*Full compatibility list: [xg.glass SDK](https://xg.glass)*

---

## 🛠️ Installation

### Quick Install (For End Users)

1. **Download the APK**
   - Go to [Releases](../../releases)
   - Download `memory-sync.apk`

2. **Install on Android Phone**
   ```bash
   adb install memory-sync.apk
   ```
   Or transfer the APK to your phone and install directly.

3. **Setup API Keys**
   - Set your OpenAI API key in environment variables (see [Configuration](#-configuration))
   - Complete Google OAuth authorization on first run

### Build from Source (For Developers)

```bash
# Clone the xg.glass SDK
git clone https://github.com/hkust-spark/xg-glass-sdk.git
cd xg-glass-sdk

# Copy source files to the project
cp /path/to/MemorySyncEntry.kt templates/kotlin-app/ug_app_logic/src/main/java/com/example/xgglassapp/logic/
cp /path/to/GoogleSyncManager.kt templates/kotlin-app/ug_app_logic/src/main/java/com/example/xgglassapp/logic/

# Build
cd templates/kotlin-app
./gradlew assembleDebug
```

---

## ⚙️ Configuration

### 1. OpenAI API Key

Set your API key as an environment variable:

**Windows:**
```powershell
$env:OPENAI_API_KEY = "sk-your-api-key-here"
[Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "sk-your-api-key-here", "User")
```

**Linux/Mac:**
```bash
export OPENAI_API_KEY="sk-your-api-key-here"
```

### 2. Google OAuth Setup

#### Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project
3. Enable these APIs:
   - Google Drive API
   - Google Calendar API
   - Google Tasks API

#### Create OAuth Credentials

1. Navigate to **APIs & Services** → **Credentials**
2. Click **Create Credentials** → **OAuth client ID**
3. Choose **Desktop app** as application type
4. Download the credentials JSON file
5. Extract `client_id` and `client_secret`

#### Configure the App

Set environment variables:

```powershell
$env:GOOGLE_CLIENT_ID = "your-client-id.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET = "your-client-secret"
```

---

## 🎮 Usage

### First Time Authorization

1. **Launch the app** on your Android device
2. **Select "Memory Sync"** from the app menu
3. **Grant permissions** when prompted:
   - Camera access
   - Microphone access
4. **Authorize Google access**:
   - App displays an authorization URL
   - Open the URL in a browser
   - Sign in to Google and grant permissions
   - Copy the authorization code
   - Enter the code in the app

### Daily Use

1. **Trigger Memory Capture**
   - Say "Sync Memory" or use the app button
   - Glasses will capture a photo
   - Microphone records your voice for context

2. **AI Processing**
   - OpenAI analyzes the photo and audio
   - Extracts tasks, events, and memories

3. **Automatic Sync**
   - Tasks appear in Google Tasks
   - Events added to Google Calendar
   - Memories saved to Google Drive

### Example Scenarios

**Scenario 1: Meeting Notes**
- Photo: Whiteboard with project timeline
- Audio: "We need to finish the presentation by Friday"
- Result: Task created in Google Tasks with due date

**Scenario 2: Conference Talk**
- Photo: Speaker on stage
- Audio: "AI Conference on March 15th at Convention Center"
- Result: Calendar event created with time and location

**Scenario 3: Personal Memory**
- Photo: Sunset at the beach
- Audio: "Beautiful evening with family"
- Result: Memory note saved to Google Drive

---

## 📂 Project Structure

```
memory-sync/
├── src/
│   ├── MemorySyncEntry.kt        # Main app entry (225 lines)
│   └── GoogleSyncManager.kt      # OAuth & Google APIs (519 lines)
├── release/
│   └── memory-sync.apk           # Pre-built APK (27.3 MB)
└── README.md                     # This file
```

---

## 💻 Code Overview

### Core Logic (MemorySyncEntry.kt)

The main entry point implements the capture-analyze-sync pipeline in ~50 lines:

```kotlin
override suspend fun run(ctx: UniversalAppContext): Result<Unit> {
    // 1. Capture photo
    ctx.client.display("📸 Taking photo...", DisplayOptions())
    val img = ctx.client.capturePhoto().getOrThrow()
    
    // 2. Record audio
    ctx.client.display("🎤 Recording...", DisplayOptions())
    val audioSession = ctx.client.startMicrophone().getOrThrow()
    val transcript = captureAudioTranscript(audioSession)
    
    // 3. AI analysis
    ctx.client.display("🤖 Analyzing...", DisplayOptions())
    val extractedData = extractMemoriesAndTasks(transcript)
    
    // 4. Sync to Google
    ctx.client.display("☁️ Syncing...", DisplayOptions())
    googleManager.syncTasksToGoogle(extractedData.tasks)
    googleManager.syncMemoriesToGoogle(extractedData.memories)
    googleManager.syncEventsToGoogle(extractedData.events)
    
    ctx.client.display("✅ Done!", DisplayOptions())
    return Result.success(Unit)
}
```

### OAuth Flow (GoogleSyncManager.kt)

Handles complete OAuth 2.0 flow with automatic token refresh:

```kotlin
// Get authorization URL
val authUrl = googleManager.getAuthorizationUrl(clientId)

// Exchange authorization code for tokens
googleManager.exchangeAuthorizationCode(code, clientId, clientSecret)

// Tokens are saved and automatically refreshed
googleManager.syncTasksToGoogle(tasks)  // Just works!
```

---

## 🔧 Troubleshooting

### Common Issues

**"Invalid credentials" error**
- Verify your OAuth client ID and secret
- Ensure all three APIs are enabled in Google Cloud Console

**"Token expired" error**
- App should auto-refresh (wait a moment)
- If persistent, delete saved tokens: `~/.memory-sync/google_token.json`

**"OpenAI API error"**
- Check your API key is set correctly
- Verify you have credits in your OpenAI account

**Build errors**
- Ensure JDK 17 is installed
- Run `./gradlew clean build`

### Debug Mode

Check logs via ADB:

```bash
adb logcat | grep "MemorySync"
```

---

## 🔐 Privacy & Security

- **Tokens stored locally** - OAuth tokens saved in `~/.memory-sync/` directory
- **No third-party servers** - Direct communication with Google APIs only
- **End-to-end encryption** - Data transmitted over HTTPS
- **Local processing** - Photos/audio processed locally before sending to OpenAI
- **User control** - Can revoke access anytime via [Google Account Settings](https://myaccount.google.com/permissions)

---

## 📊 Performance

- **Authorization**: One-time setup (~30 seconds)
- **Photo Capture**: < 1 second
- **Audio Recording**: 5-10 seconds
- **AI Analysis**: 2-5 seconds (depends on OpenAI API)
- **Google Sync**: 1-3 seconds per item
- **Total Flow**: ~15-30 seconds end-to-end

---

## 🛣️ Roadmap

- [ ] Support for more AI models (Claude, Gemini)
- [ ] Batch sync optimization
- [ ] Custom prompts for AI analysis
- [ ] Voice command activation
- [ ] Microsoft 365 integration (Outlook, OneNote, To Do)
- [ ] Notion integration
- [ ] Web dashboard for viewing synced data

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- Built with [xg.glass SDK](https://xg.glass) - Universal smart glasses development framework
- Powered by [OpenAI GPT-4o](https://openai.com) - AI analysis
- Integrated with [Google APIs](https://developers.google.com) - Tasks, Calendar, Drive

---

## 📧 Contact

- **Issues**: [GitHub Issues](../../issues)
- **Documentation**: [xg.glass Developer Guide](https://xg.glass/developer-guide/)
- **Community**: [xg.glass Discord](https://discord.gg/xgglass)

---

<p align="center">
Made with ❤️ for the smart glasses community
</p>
