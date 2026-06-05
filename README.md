<a name="readme-top"></a>

<br />
<div align="center">
  <h1>セルフホスト (Self Hosted) Music Bot 🎵</h1>
  <p align="center">
    A minimal, high-quality Discord music streaming bot built for real-time, self-hosted control with near zero bloat.
    <br />
    <a href="https://github.com/JakeTurner616/Serufuhosuto-music-bot"><strong>View the Source »</strong></a>
    <br /><br />
  </p>

  <p align="center">

  [![Build](https://img.shields.io/github/actions/workflow/status/JakeTurner616/Serufuhosuto-music-bot/manual-release.yml?label=Build&style=for-the-badge)](https://github.com/JakeTurner616/Serufuhosuto-music-bot/actions/workflows/manual-release.yml "Build Status")
  [![Release](https://img.shields.io/github/v/release/JakeTurner616/Serufuhosuto-music-bot?label=Release&style=for-the-badge)](https://github.com/JakeTurner616/Serufuhosuto-music-bot/releases "Latest Release")
  [![yt-dlp CI](https://img.shields.io/github/actions/workflow/status/yt-dlp/yt-dlp/core.yml?branch=master&label=yt-dlp%20Build&style=for-the-badge)](https://github.com/yt-dlp/yt-dlp/actions "yt-dlp CI")
  [![yt-dlp Release](https://img.shields.io/github/v/release/yt-dlp/yt-dlp?color=brightgreen&label=yt-dlp%20latest&style=for-the-badge)](https://github.com/yt-dlp/yt-dlp/releases "yt-dlp Latest")
  </p>
</div>

---

## About The Project

セルフホスト (Self Hosted) Music Bot is a modern Java-based Discord bot built for high-quality music streaming using `yt-dlp`, `ffmpeg`, JDA 6, and JDAVE for Discord voice channels using E2EE - mandatory since March 1, 2026.

✅ No tracking
✅ No third-party music APIs
✅ Self-hosted and easy to repair when YouTube inevitably changes something
✅ DAVE-capable Discord voice support

---

## ⚙️ Built With

- ☕ Java 17+ for compilation, Java 21+ recommended for runtime
- 🎧 [JDA 6.4.1](https://github.com/discord-jda/JDA)
- 🔐 JDAVE 0.1.8 with native artifacts for Windows x64, Linux x64, Linux ARM64, and macOS
- 🧪 [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- 🛠 FFmpeg
- 📦 Maven + Shade Plugin

---

## Prerequisites

- Java installed and available as `java`
- FFmpeg installed and available as `ffmpeg`
- A Discord bot token
- Message Content Intent enabled for the bot in the Discord Developer Portal

The bot can use a system `yt-dlp`, but recent YouTube extractor changes matter a lot. For best results, keep a project-local yt-dlp binary.

Windows:

```powershell
New-Item -ItemType Directory -Force tools
curl.exe -L -o tools\yt-dlp.exe https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe
.\tools\yt-dlp.exe --version
```

Linux:

```bash
mkdir -p tools
curl -L -o tools/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
chmod +x tools/yt-dlp
./tools/yt-dlp --version
```

`tools/yt-dlp` and `tools\yt-dlp.exe` are intentionally ignored by git.

---

## 🤖 Create The Discord Bot

1. Open the Discord Developer Portal: https://discord.com/developers/applications
2. Click **New Application**.
3. Give it a name, then open the application.
4. Go to **Bot**.
5. Click **Add Bot** if the app does not already have one.
6. Under **Privileged Gateway Intents**, enable **Message Content Intent**.
7. Under **Token**, click **Reset Token** or **Copy Token**.
8. Put that token in `config.json` as `"token": "YOUR_DISCORD_BOT_TOKEN"`.

Invite the bot to your server:

1. Go to **OAuth2** -> **URL Generator**.
2. Under **Scopes**, select `bot`.
3. Under **Bot Permissions**, select:
   - View Channels
   - Send Messages
   - Read Message History
   - Connect
   - Speak
   - Use Voice Activity
4. Copy the generated URL, open it in your browser, and choose the server to invite the bot to.

You must have permission to manage or invite bots in the target server!

---

## 📁 Configuration

Create a `config.json` in the folder where you run the jar.

Windows:

```json
{
  "token": "YOUR_DISCORD_BOT_TOKEN",
  "prefix": ".",
  "ffmpegPath": "ffmpeg",
  "ytDlpPath": "tools\\yt-dlp.exe",
  "ytQuality": "bestaudio[ext=webm]/bestaudio/bestaudio[ext=m4a]"
}
```

Linux:

```json
{
  "token": "YOUR_DISCORD_BOT_TOKEN",
  "prefix": ".",
  "ffmpegPath": "ffmpeg",
  "ytDlpPath": "tools/yt-dlp",
  "ytQuality": "bestaudio[ext=webm]/bestaudio/bestaudio[ext=m4a]"
}
```

If you prefer a system install, set `ytDlpPath` to `yt-dlp`.

---

## ⬇️ Installation

Download the latest `.jar` file from the releases page:

👉 [Latest Release](https://github.com/JakeTurner616/Serufuhosuto-music-bot/releases/latest)

Put `config.json` next to the jar, then run:

```bash
java -jar Serufuhosuto-music-bot-1.6.jar
```

---

## 🐧 Linux Quick Setup

For Debian or Ubuntu:

```bash
sudo apt update
sudo apt install -y git maven openjdk-21-jre-headless ffmpeg curl
```

Clone and enter the repo:

```bash
cd "$HOME"
git clone https://github.com/JakeTurner616/Serufuhosuto-music-bot.git serufuhosuto-music-bot
cd "$HOME/serufuhosuto-music-bot"
```

Download local yt-dlp:

```bash
mkdir -p tools
curl -L -o tools/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
chmod +x tools/yt-dlp
```

Create `config.json` using the Linux example above, then build and run:

```bash
mvn clean package
java -jar target/Serufuhosuto-music-bot-1.6.jar
```

Optional systemd service:

Replace `YOUR_USER` with your Linux username, adjust working directory if using a custom install location.

```ini
[Unit]
Description=Serufuhosuto Music Bot
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/home/YOUR_USER/serufuhosuto-music-bot
ExecStart=/usr/bin/java -jar target/Serufuhosuto-music-bot-1.6.jar
Restart=always
RestartSec=10
User=YOUR_USER

[Install]
WantedBy=multi-user.target
```

Save it as `/etc/systemd/system/serufuhosuto.service`, then run:

```bash
sudo systemctl daemon-reload
sudo systemctl enable serufuhosuto
sudo systemctl start serufuhosuto
journalctl -u serufuhosuto -f
```

---

## 🧱 Building From Source

```bash
git clone https://github.com/JakeTurner616/Serufuhosuto-music-bot.git
cd Serufuhosuto-music-bot
mvn clean package
```

The shaded jar is written to:

```text
target/Serufuhosuto-music-bot-1.6.jar
```

The shaded jar includes common JDAVE native artifacts for Windows x64, Linux x64, Linux ARM64, and macOS.

---

## 🎮 Usage

```text
🎧 .play <url or search>  stream or queue audio
🎧 .p <url or search>     alias for play
⏭ .skip                  skip the current track
⏩ .seek <time>           seek to seconds, MM:SS, or HH:MM:SS
🧹 .clear                 clear the queue
🛑 .stop                  stop playback
👋 .leave                 disconnect the bot
```

---

## 📜 License

Distributed under the GNU GPL v3.0 License. See the [LICENSE](LICENSE) file for more information.

<p align="right">(<a href="#readme-top">back to top</a>)</p>
