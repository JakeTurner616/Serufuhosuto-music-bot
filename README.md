<a name="readme-top"></a>

[![Contributors][contributors-shield]][contributors-url]
[![Stars][stars-shield]][stars-url]
[![License][license-shield]][license-url]


<br />
<div align="center">
  <h1>セルフホスト (Self Hosted) Music Bot 🎵</h1>
  <p align="center">
    A minimal, high-quality Discord music streaming bot — built for real-time, self-hosted control with near zero bloat.
    <br />
    <a href="https://github.com/JakeTurner616/Serufuhosuto-music-bot"><strong>View the Source »</strong></a>
    <br />
    <br />
    <a href="#usage">Try It</a>
    ·
    <a href="https://github.com/JakeTurner616/Serufuhosuto-music-bot/issues">Report Bug</a>
    ·
    <a href="https://github.com/JakeTurner616/Serufuhosuto-music-bot/issues">Request Feature</a>
  </p>
</div>

---

## About The Project

セルフホスト (Self Hosted) Music Bot is a modern, sleek Java-based Discord bot built for high-quality music streaming using `yt-dlp` and `ffmpeg`. Built to solve the problem of overcomplicated and unreliable self-hosted discord msuic bots.

✅ No tracking  
✅ No third-party APIs  
✅ Just a fast, queue-friendly music bot that respects your setup.
✅ Easy to fix when it inevitably breaks

---

## ⚙️ Built With

- ☕ Java 21  
- 🎧 [JDA 5](https://github.com/discord-jda/JDA)  
- 🧪 [yt-dlp](https://github.com/yt-dlp/yt-dlp)  
- 🛠 FFmpeg  
- 📦 Maven + Shade Plugin 

---

### Prerequisites

- Java 17+
- `ffmpeg` installed (in your system path)
- `yt-dlp` installed and accessible

```bash
# Example setup (Windows / Linux)
choco install ffmpeg -y     # or brew install ffmpeg
pip install yt-dlp
````

---

### Installation

**⬇️ Download the latest `.jar` file:**

👉 [Serufuhosuto-music-bot.jar](https://github.com/JakeTurner616/Serufuhosuto-music-bot/releases/latest)
**📁 In the same folder where the Java executable is run, create a `config.json`:**

```json
{
    "token": "YOUR_DISCORD_BOT_TOKEN",
    "prefix": ".",
    "ffmpegPath": "ffmpeg",
    "ytQuality": "bestaudio[ext=m4a]"
}
```

**Run the bot:**

```bash
java -jar Serufuhosuto-music-bot-1.0.jar
```

---

### Building from Source

If you want to modify:

```bash
git clone https://github.com/JakeTurner616/Serufuhosuto-music-bot.git
cd Serufuhosuto-music-bot
mvn clean package
```

The final JAR will be in the `target/` folder.

---

## 🎮 Usage

```text
🎧 .play <url>    – stream or queue YouTube audio
⏭ .skip          – skip the current track
⏩ .seek <sec>    – jump to a time in the current track
🧹 .clear         – clear the queue
🛑 .stop          – stop all playback
👋 .leave         – disconnect the bot
```




---

## 📜 License

Distributed under the GNU GPL v3.0 License. See the [LICENSE](LICENSE) file for more information.

---

<!-- SHIELDS -->

[contributors-shield]: https://img.shields.io/github/contributors/JakeTurner616/Serufuhosuto-music-bot.svg?style=for-the-badge
[contributors-url]: https://github.com/JakeTurner616/Serufuhosuto-music-bot/graphs/contributors
[stars-shield]: https://img.shields.io/github/stars/JakeTurner616/Serufuhosuto-music-bot.svg?style=for-the-badge
[stars-url]: https://github.com/JakeTurner616/Serufuhosuto-music-bot/stargazers
[license-shield]: https://img.shields.io/github/license/JakeTurner616/Serufuhosuto-music-bot.svg?style=for-the-badge
[license-url]: https://github.com/JakeTurner616/Serufuhosuto-music-bot/blob/main/LICENSE

