# GlassBeat for macOS (Kotlin Multiplatform / Compose for Desktop)

Official macOS port of **GlassBeat** ([https://github.com/abzgif/mac-glassbeat](https://github.com/abzgif/mac-glassbeat)), built with **Kotlin Multiplatform (Compose for Desktop)**, **SQLite**, **JAudioTagger**, and a native macOS **CoreAudio (`AVAudioEngine` + `AVAudioUnitEQ` + `AVAudioUnitTimePitch`)** playback and 5-band equalizer pipeline.

## Ported Features

- **Glassmorphic UI & Theme**:
  - Supports both GlassBeat's signature **Dark Mode (`#050505`)** and **Light Mode (`#F5F6F8`)** palettes with translucent glass cards, specular borders, dynamic blurred album artwork backdrops on the Now Playing & Mini Player screens, and `22.dp` Gaussian blur backdrops under all modal sheets.
  - Native macOS full-content transparent titlebar and rounded macOS-style squircle app icon.
- **Songs Tab**:
  - Instant search across Title, Artist, and Album (`Cmd+F` shortcut).
  - All 6 sort modes from Android: `Title (A–Z)`, `Title (Z–A)`, `Date Added (Newest)`, `Date Added (Oldest)`, `Duration (Longest)`, and `Duration (Shortest)`.
  - Multi-selection bar (`Select All`, `Play Next`, `Add to Playlist`, `Delete`).
  - Song context menu (`Play`, `Play Next`, `Add/Remove Favorite`, `Add to Playlist`, `Edit Metadata Tags`, `Song Info`, `Reveal in Finder`, `Delete`).
- **Library Tab**:
  - **Favorites**: Liked tracks synced with SQLite.
  - **Playlists**: Create, rename, delete playlists, and add/remove tracks.
  - **Podcasts**: Long-form audio tracks.
  - **Albums**: Responsive album art grid with drill-down tracklists.
  - **Artists**: Artist list with album and song counts.
  - **Folders**: Browse local filesystem directories containing audio files.
- **5-Band Parametric Equalizer**:
  - Center frequencies: `60 Hz`, `230 Hz`, `910 Hz`, `4 kHz`, `14 kHz` (`-15 dB` to `+15 dB`).
  - All 10 presets (`Bass Boost`, `Classical`, `Dance`, `Folk`, `Heavy Metal`, `Hip Hop`, `Jazz`, `Pop`, `Rock`, `Custom`) + real-time frequency response curve visualization.
  - Dual-engine support: Native macOS `AVAudioUnitEQ` + pure-Kotlin Direct-Form II Transposed (`DF2T`) biquad cascade (`EqualizerAudioProcessor`).
- **Mini Player & Expanded Now Playing**:
  - Interactive seekable progress bar directly on the Mini Player (click or drag to seek).
  - Dynamic blurred cover art background, crisp album art, and audio technical specs pill (`Format • Bitrate kb/s • Sample Rate kHz`).
  - Transport controls: Shuffle, `-10s` Replay, Previous, Play/Pause, Next, `+10s` Forward, Repeat (`Off` / `All` / `One`), and Volume slider.
  - **Playback Speed (`0.5x` – `2.0x`)**: Pitch-preserved time stretching with preset chips (`0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`).
  - **Sleep Timer**: `15`, `30`, `45`, `60`, `90` minutes, or `End of current song` with a centered, non-jittering tabular countdown badge.
  - **Interactive Queue Drawer**: Jump to any queued track, reorder tracks up/down, or remove tracks.
- **Metadata Tag Editor**:
  - Edit `Title`, `Artist`, `Album`, `Album Artist`, `Composer`, `Genre`, `Year`, `Comment`, `Lyrics`, and embedded **Cover Art** (choose image or remove cover).
  - Supports MP3 (ID3v1/ID3v2.3/ID3v2.4), FLAC, M4A/MP4, OGG Vorbis, WAV, and Ogg **Opus** (via the ported pure-Kotlin `OpusTagHandler` RFC 7845 / RFC 3533 implementation).
- **OTA Self-Updater & GitHub Actions CI/CD**:
  - **Settings → Check for Updates** queries GitHub Releases (`https://github.com/abzgif/mac-glassbeat/releases`), downloads the new `.dmg`/`.zip` installer with live progress, installs the new `.app` bundle, and automatically reopens GlassBeat.
  - `.github/workflows/build-macos.yml` automatically builds the macOS `.dmg` and `.app.zip` on GitHub's `macos-latest` runners and publishes GitHub Releases on tagged pushes (`v*`).

## Running & Packaging on macOS

### Option 1: Using the Launcher Script
```bash
cd /Users/abuzar/Music/GlassBeat_android/GlassBeat_macOS
chmod +x run_macos.sh
./run_macos.sh
```

### Option 2: Building a `.dmg` Installer Locally
```bash
./run_macos.sh :composeApp:packageDmg
```
The compiled `.dmg` installer is generated under `composeApp/build/compose/binaries/main/dmg/`.
