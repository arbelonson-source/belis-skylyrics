# CurseForge project page

Ready-to-paste text for the CurseForge project. Nothing here is published automatically.

## Project settings

| Field | Value |
| --- | --- |
| Project name | Beli's SkyLyrics |
| Summary | Synced lyrics for the song you're playing, glowing in the Minecraft sky. |
| Class | Mods |
| Main category | Cosmetic |
| Other categories | Utility & QoL |
| License | GNU Affero General Public License version 3 (AGPLv3) |
| Source | https://github.com/arbelonson-source/belis-skylyrics |
| Issues | https://github.com/arbelonson-source/belis-skylyrics/issues |
| Logo | `src/main/resources/assets/skylyrics/icon.png` |

When uploading each file:

| Field | Value |
| --- | --- |
| Game version | 1.21.11 |
| Mod loader | Fabric |
| Environment | Client |
| Java | 21 |

Relations to add:

- Fabric API: Required Dependency
- Mod Menu: Optional Dependency
- Sodium: Optional Dependency
- Iris Shaders: Optional Dependency

Images tab (upload these, in this order): `docs/sunset-shaders.jpg`, `docs/aurora-shaders.jpg`, `docs/nebula.jpg`, `docs/floating.jpg`, `docs/daytime.jpg`, `docs/settings.jpg`.

The description below links pictures straight from GitHub. If CurseForge doesn't show them, upload them in the Images tab and use those links instead.

## Description

Switch the description editor to Markdown and paste everything below the line.

---

![Lyrics in the sky at sunset](https://raw.githubusercontent.com/arbelonson-source/belis-skylyrics/main/docs/sunset-shaders.jpg)

# Beli's SkyLyrics

See the words of the song you're listening to, right in the Minecraft sky, in time with the music.

The mod finds the song your music player is playing, looks up synced lyrics, and draws them as glowing text far away in the sky or floating close to you. It's free and open source, and it always will be.

**Works on Linux, on Windows 10 and 11, and on macOS with Spotify or Apple Music.** On Windows the mod reads the media controls that pop up when you press a media key. On Linux it uses MPRIS, the feature behind your desktop's media keys. On macOS it asks the Spotify and Music apps directly, so other players and browsers can't be seen there.

## Features

- **Lyrics in the sky**, line by line, in time with the song, with the previous and next lines if you want them
- **Word-by-word animation**, or Fade, Rise, Pop and Type, plus a karaoke fill and a small pulse on each new line
- **Top bar** with the album cover, the song name and the line being sung
- **Three letter styles**: Smooth, Handwritten and the Minecraft font
- **Colours** from the album cover, a colour you pick, or rainbow, with adjustable glow, shadow and opacity
- **Stays bright with shaders**: lyrics can be drawn on top of the finished picture, so shader packs, clouds, rain and fog don't dim them
- **Night skies**: an aurora or a nebula behind the lyrics at night
- **Per-song timing**: nudge a song's lyrics earlier or later, and the fix is remembered for that song
- **Music controls**: play, pause and skip from the settings screen or with keys you choose
- **English and Hebrew** settings screen, following your Minecraft language
- **Demo song** with made-up lyrics, for trying the settings without music
- Works with Sodium, Iris and shader packs
- Client side only: works in single player and on any server

![Aurora over the lyrics](https://raw.githubusercontent.com/arbelonson-source/belis-skylyrics/main/docs/aurora-shaders.jpg)

## Requirements

- Minecraft 1.21.11 with Fabric Loader 0.17.0 or newer
- Fabric API
- Java 21 or newer (the Minecraft launcher already includes it)
- Linux, or Windows 10 or 11, with a music player that shows up in your media controls; or macOS with the Spotify or Music app
- Optional: Mod Menu, to open the settings from the mod list

## How to install

1. Install Fabric for Minecraft 1.21.11. The CurseForge app or Prism Launcher can set this up when you make a new profile.
2. Add **Fabric API** and **Beli's SkyLyrics** to the profile.
3. Start the game and play a song in Spotify, a browser tab or any other music app.
4. Press **J** in game to open the settings.

![Floating lyrics close to the player](https://raw.githubusercontent.com/arbelonson-source/belis-skylyrics/main/docs/floating.jpg)

## FAQ

**Does it work on Windows or macOS?**
Windows 10 and 11: yes, it reads the media controls that pop up when you press a media key. macOS: yes, with the Spotify and Apple Music apps only. The first time, macOS asks whether Minecraft may control them: click OK.

**Which music players work?**
On Windows: anything that shows up in the media panel when you press a media key, such as Spotify, Chrome, Edge, Firefox and other browsers, Media Player and Apple Music (VLC and mpv don't show up there). On Linux: anything that shows up in your desktop's media controls: Spotify (including the Flatpak version), Firefox, Chrome and other browsers (so YouTube, YouTube Music and SoundCloud in a tab), VLC, Rhythmbox, Elisa and most other music apps. mpv works with the mpv-mpris plugin. On macOS: the Spotify and Music apps, while they are open. You can choose which player to follow in the Music tab.

**Where do the lyrics come from?**
From LRCLIB (lrclib.net), a free, volunteer-run lyrics database. No account needed. If LRCLIB doesn't have a song, the mod can't show lyrics for it. Lyrics it finds are saved on your computer, so songs it has seen before work offline.

**Does it work with shaders?**
Yes. Shader packs can darken the sky, so the mod can draw the lyrics on top of everything to keep them bright. On **Auto** it does this whenever a shader pack is on. If they still look dim, set **Look → Draw on top** to **Always**.

**The lyrics are early or late.**
Open the **Music** tab and move the **This song** slider, or give keys to "Lyrics are late (this song)" and "Lyrics are early (this song)" in Minecraft's key binds. The fix is remembered for that song.

**Does the server need it?**
No. It's client side only.

**Is this the Neon Lyrics mod?**
No. It's a free mod with a similar idea, written from scratch, and not connected to Neon Lyrics or its maker.

More help: [FAQ](https://github.com/arbelonson-source/belis-skylyrics/blob/main/docs/FAQ.md) and [Troubleshooting](https://github.com/arbelonson-source/belis-skylyrics/blob/main/docs/TROUBLESHOOTING.md) on GitHub.

![The settings screen](https://raw.githubusercontent.com/arbelonson-source/belis-skylyrics/main/docs/settings.jpg)

## Credits

- Made by Beli.
- Lyrics from LRCLIB. The lyrics belong to their writers and are not part of this mod.
- Fonts: Poppins, Kaushan Script and Noto Sans Hebrew, all under the SIL Open Font License.

## License

AGPL-3.0. You can use, change and share the mod freely. If you share a changed version, share its source too. Source code: [github.com/arbelonson-source/belis-skylyrics](https://github.com/arbelonson-source/belis-skylyrics)
