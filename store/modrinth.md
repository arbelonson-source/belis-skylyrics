# Modrinth project page

Ready-to-paste text for the Modrinth project. Nothing here is published automatically.

## Project settings

| Field | Value |
| --- | --- |
| Name | Beli's SkyLyrics |
| Slug | `belis-skylyrics` |
| Summary (80 characters max) | Synced lyrics for the song you're playing, glowing in the Minecraft sky. |
| Project type | Mod |
| Loader | Fabric |
| Game versions | 1.21.11 |
| Client side | Required |
| Server side | Unsupported |
| Categories | Utility (main), Decoration |
| Search words | Modrinth has no free-form tags. The summary and description already contain lyrics, music, Spotify, karaoke, sky, Linux, Windows and MPRIS for search. |
| License | AGPL-3.0-only |
| Source code | https://github.com/arbelonson-source/belis-skylyrics |
| Issue tracker | https://github.com/arbelonson-source/belis-skylyrics/issues |
| Icon | `src/main/resources/assets/skylyrics/icon.png` |

Dependencies to add on each version:

- Fabric API: required
- Mod Menu: optional
- Sodium: optional
- Iris Shaders: optional

Gallery pictures (upload these, in this order): `docs/sunset-shaders.jpg`, `docs/aurora-shaders.jpg`, `docs/nebula.jpg`, `docs/floating.jpg`, `docs/daytime.jpg`, `docs/settings.jpg`.

## Description

Paste everything below the line into the description box.

---

![Lyrics in the sky at sunset](https://raw.githubusercontent.com/arbelonson-source/belis-skylyrics/main/docs/demo.webp)

**Beli's SkyLyrics** shows the words of the song you're listening to in the Minecraft sky, in time with the music. It finds the song your music player is playing, looks up synced lyrics, and draws them as glowing text far away in the sky or floating close to you.

It's free and open source, and it always will be.

> **Works on Linux and on Windows 10 and 11.** On Windows the mod reads the media controls that pop up when you press a media key. On Linux it uses MPRIS, the feature behind your desktop's media keys. On macOS it loads, but only the built-in demo song works for now.

### Features

- **Lyrics in the sky**, line by line, in time with the song. Show the previous and next lines too if you like.
- **Word-by-word animation**: words appear as they are sung. Or pick Fade, Rise, Pop or Type, plus a karaoke fill and a small pulse on each new line.
- **A top bar** with the album cover, the song name and the line being sung.
- **Three letter styles**: Smooth, Handwritten or the Minecraft font.
- **Colours** taken from the album cover, a colour you pick, or rainbow. Glow, shadow and opacity are all adjustable.
- **Stays bright with shaders**: the lyrics can be drawn on top of the finished picture, so shader packs, clouds, rain and fog don't dim them.
- **Night skies**: an aurora or a nebula behind the lyrics at night.
- **Per-song timing**: if a song's lyrics run early or late, nudge them and the fix is remembered for that song.
- **Music controls**: play, pause, skip and see the song's progress from the settings screen or with keys you choose.
- **English and Hebrew** settings screen, following your Minecraft language.
- **Demo song** with made-up lyrics, for trying the settings without music.
- Works with Sodium, Iris and shader packs. Client side only, so it works in single player and on any server.

![Aurora over the lyrics with shaders](https://raw.githubusercontent.com/arbelonson-source/belis-skylyrics/main/docs/aurora-shaders.jpg)

### Requirements

- Minecraft 1.21.11
- Fabric Loader 0.17.0 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21 or newer (the Minecraft launcher already includes it)
- Linux, or Windows 10 or 11, with a music player that shows up in your media controls
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) to open the settings from the mod list

### Install

1. Install Fabric for Minecraft 1.21.11 (Prism Launcher can do this for you when you make a new instance).
2. Put **Fabric API** and **Beli's SkyLyrics** in your `mods` folder, or add them from Prism Launcher's mod browser.
3. Start the game and play a song in Spotify, a browser tab or any other music app.
4. Press **J** in game to open the settings.

### FAQ

**Does it work on Windows or macOS?**
Windows 10 and 11: yes, it reads the media controls that pop up when you press a media key. macOS: not yet, only the demo song works there.

**Which music players work?**
On Windows: anything that shows up in the media panel when you press a media key, such as Spotify, Chrome, Edge, Firefox and other browsers, Media Player and Apple Music (VLC and mpv don't show up there). On Linux: anything that shows up in your desktop's media controls: Spotify (including the Flatpak version), Firefox, Chrome and other browsers (so YouTube, YouTube Music and SoundCloud in a tab), VLC, Rhythmbox, Elisa and most other music apps. mpv works with the mpv-mpris plugin. You can tell the mod which player to follow in the Music tab.

**Where do the lyrics come from?**
From [LRCLIB](https://lrclib.net), a free, volunteer-run lyrics database. No account needed. If LRCLIB doesn't have a song, the mod can't show lyrics for it. Lyrics it finds are saved on your computer, so songs it has seen before work offline.

**Does it work with shaders?**
Yes. Shader packs can darken the sky, so the mod can draw the lyrics on top of everything to keep them bright. On **Auto** it does this whenever a shader pack is on. If they still look dim, set **Look → Draw on top** to **Always**.

**The lyrics are early or late.**
Open the **Music** tab and move the **This song** slider, or give keys to "Lyrics are late (this song)" and "Lyrics are early (this song)" in Minecraft's key binds. The fix is remembered for that song.

**Does the server need it?**
No. It's client side only.

**Is this the Neon Lyrics mod?**
No. It's a free mod with a similar idea, written from scratch, and not connected to Neon Lyrics or its maker.

More answers: [FAQ](https://github.com/arbelonson-source/belis-skylyrics/blob/main/docs/FAQ.md) and [Troubleshooting](https://github.com/arbelonson-source/belis-skylyrics/blob/main/docs/TROUBLESHOOTING.md).

![The settings screen](https://raw.githubusercontent.com/arbelonson-source/belis-skylyrics/main/docs/settings.jpg)

### Credits

- Made by Beli.
- Lyrics from [LRCLIB](https://lrclib.net). The lyrics belong to their writers and are not part of this mod.
- Fonts: Poppins, Kaushan Script and Noto Sans Hebrew, all under the SIL Open Font License.

### License

[AGPL-3.0](https://github.com/arbelonson-source/belis-skylyrics/blob/main/LICENSE). You can use, change and share the mod freely. If you share a changed version, share its source too.
