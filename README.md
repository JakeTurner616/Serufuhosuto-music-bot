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
  [![yt-dlp Release](https://img.shields.io/pypi/v/yt-dlp?color=brightgreen&label=yt-dlp%20latest&style=for-the-badge)](https://pypi.org/project/yt-dlp/ "yt-dlp Latest")
  </p>
</div>

---

## About The Project

セルフホスト (Self Hosted) Music Bot is a modern Java-based Discord bot built for high-quality music streaming using `yt-dlp`, `ffmpeg`, JDA 6, and DAVE for Discord voice channels using E2EE (mandatory since March 1, 2026)

- No tracking
- No third-party music APIs
- Self-hosted and easy to repair when YouTube inevitably changes something
- DAVE-capable E2EE Discord voice support 

## Discord Bot Setup

1. Open the Discord Developer Portal: https://discord.com/developers/applications
2. Create or select an application.
3. Go to **Bot**.
4. Add a bot if one does not already exist.
5. Enable **Message Content Intent** under **Privileged Gateway Intents**.
6. Copy or reset the bot token.
7. Put the token in `config.json`.

Invite the bot:

1. Go to **OAuth2** -> **URL Generator**.
2. Select the `bot` scope.
3. Select these bot permissions:
   - View Channels
   - Send Messages
   - Read Message History
   - Connect
   - Speak
   - Use Voice Activity
4. Open the generated URL and invite the bot to your server.

## Configuration

Create `config.json` in the directory where the bot runs.

Linux and Docker:

```json
{
  "token": "YOUR_DISCORD_BOT_TOKEN",
  "prefix": ".",
  "ffmpegPath": "ffmpeg",
  "ytDlpPath": "tools/yt-dlp",
  "ytQuality": "bestaudio[ext=webm]/bestaudio/bestaudio[ext=m4a]"
}
```

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

Keep `config.json` private. A leaked Discord token should be rotated immediately in the Discord Developer Portal.

## Usage

```text
.play <url or search>  stream or queue audio
.p <url or search>     alias for play
.skip                  skip the current track
.seek <time>           seek to seconds, MM:SS, or HH:MM:SS
.clear                 clear the queue
.stop                  stop playback
.leave                 disconnect the bot
```

## Recommended Linux Deployment: Docker

Docker is the recommended Linux deployment, especially for Linux Mint 21 and Ubuntu 22.04 hosts. Those systems have older host libraries, while the container uses Ubuntu Noble userspace with the newer glibc/libstdc++ needed by JDAVE.

Install Docker using the official Docker instructions for your distro.

Clone the repo:

```bash
cd /opt
sudo git clone https://github.com/JakeTurner616/Serufuhosuto-music-bot.git serufuhosuto-music-bot
sudo chown -R "$USER:$USER" /opt/serufuhosuto-music-bot
cd /opt/serufuhosuto-music-bot
```

Create `config.json` using the Linux/Docker example above.

Build and run in the foreground:

```bash
docker compose build
docker compose up
```

If your system uses the older Compose command:

```bash
docker-compose build
docker-compose up -d
```

If Docker requires root on your machine, prefix those commands with `sudo`. To allow your user to run Docker without sudo:

```bash
sudo usermod -aG docker "$USER"
```

Then log out and back in.

Verify inside the container:

```bash
docker compose run --rm --entrypoint /bin/bash serufuhosuto -lc 'python3 --version && tools/yt-dlp --version && ffmpeg -version | head -n 1'
```

Run in the background:

```bash
docker compose up -d
docker compose logs -f
```

Updating the deployed container:

```bash
cd /opt/serufuhosuto-music-bot
git pull
docker compose build
docker compose up -d
docker compose logs -f
```

Stopping the container:

```bash
docker compose down
```

## Bare-Metal Linux Deployment

Bare-metal deployment is a second option for hosts with a new enough userspace, or for advanced use-cases.

Required:

- Java 25 JDK for build and runtime
- Maven
- FFmpeg
- curl
- Local or system yt-dlp
- glibc 2.38 or newer
- libstdc++ exporting `GLIBCXX_3.4.32` or newer

Ubuntu 24.04+ and Debian 13+ are good targets. Ubuntu 22.04 and Linux Mint 21 are not good bare-metal targets for JDAVE 0.1.8 because their glibc/libstdc++ versions are too old.

Check the host:

```bash
ldd --version
strings /lib/x86_64-linux-gnu/libstdc++.so.6 | grep GLIBCXX_3.4.32
uname -m
```

Install dependencies:

```bash
sudo apt update
sudo apt install -y git maven ffmpeg curl wget gpg
sudo install -d -m 0755 /etc/apt/keyrings
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor --yes -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-25-jdk
java --version
javac --version
mvn -v
```

Clone and build:

```bash
cd /opt
sudo git clone https://github.com/JakeTurner616/Serufuhosuto-music-bot.git serufuhosuto-music-bot
sudo chown -R "$USER:$USER" /opt/serufuhosuto-music-bot
cd /opt/serufuhosuto-music-bot

mkdir -p tools
curl -L -o tools/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
chmod +x tools/yt-dlp

mvn -q clean package
```

Run manually:

```bash
mkdir -p tmp
java -Djava.io.tmpdir=/opt/serufuhosuto-music-bot/tmp -jar target/Serufuhosuto-music-bot-1.6.jar
```

The `java.io.tmpdir` override keeps JDAVE's extracted native library in a project-local executable directory.

If JDAVE fails to load on Linux, inspect the extracted native library:

```bash
find tmp -name 'dave*.so' -print
file tmp/jdave*/dave*.so
ldd tmp/jdave*/dave*.so
```

If `ldd` reports missing `GLIBC_2.38` or `GLIBCXX_3.4.32`, use Docker or move to a newer OS. Do not upgrade glibc in place on an older distro.

## systemd Service

Use this only after the manual bare-metal run works.

Create `/etc/systemd/system/serufuhosuto.service`:

```ini
[Unit]
Description=Serufuhosuto Music Bot
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/serufuhosuto-music-bot
ExecStartPre=/usr/bin/mkdir -p /opt/serufuhosuto-music-bot/tmp
ExecStart=/usr/bin/java -Djava.io.tmpdir=/opt/serufuhosuto-music-bot/tmp -jar target/Serufuhosuto-music-bot-1.6.jar
Restart=always
RestartSec=10
User=YOUR_USER

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable serufuhosuto
sudo systemctl start serufuhosuto
journalctl -u serufuhosuto -f
```

## Windows Local Setup

Install Java 25, Maven, FFmpeg, and yt-dlp:

```powershell
New-Item -ItemType Directory -Force tools
curl.exe -L -o tools\yt-dlp.exe https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe
.\tools\yt-dlp.exe --version
mvn -q clean package
java -jar target\Serufuhosuto-music-bot-1.6.jar
```

## Building From Source

```bash
git clone https://github.com/JakeTurner616/Serufuhosuto-music-bot.git
cd Serufuhosuto-music-bot
mvn -q clean package
```

The runnable shaded jar is:

```text
target/Serufuhosuto-music-bot-1.6.jar
```

## License

Distributed under the GNU GPL v3.0 License. See [LICENSE](LICENSE) for details.

<p align="right">(<a href="#readme-top">back to top</a>)</p>
