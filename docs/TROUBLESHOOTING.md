# Troubleshooting

Something not working? Start with the problem that matches yours. For general questions, see [FAQ.md](FAQ.md).

The **Music** tab in the settings (press **J**) is the best place to start. It shows which player the mod is following and whether lyrics were found.

## No lyrics in the sky

Go through these in order:

1. **Is the mod turned on?** At the top of the settings screen there is a **Lyrics** switch. If you set a key for "Show or hide lyrics", you may have pressed it by accident.
2. **Are you looking the right way?** On the **Lyrics** tab, **Direction** can be set to North, East, South or West. Then the lyrics stay on that side of the sky. Turn around, or set Direction to **Follow** so they stay where you look.
3. **Are they hidden on purpose?** On the **Display** tab check **Hide with F1** (pressing F1 hides them along with the rest of the screen), **Hide in menus** (hidden while chat or any menu is open) and **Hide when paused** (hidden while the music is paused).
4. **Does the mod see your music?** On the **Music** tab, **Status** should say "Following" and the name of your player. If it says "No music playing" or "Can't reach music players", see [Check that your player can be seen](#check-that-your-player-can-be-seen).
5. **Were lyrics found?** The **Lyrics** line on the Music tab tells you:
   - **Looking for lyrics…**: wait a moment.
   - **No lyrics found for this song**: LRCLIB, the free lyrics database the mod uses, doesn't have this song. The mod can't show lyrics it doesn't have. It checks again after a few days, or press **Retry**.
   - **Couldn't reach LRCLIB. Offline?**: check your internet, then press **Retry**.
   - **Instrumental, no words**: the song has no lyrics.
   - **Found, without timing**: the lyrics exist but without times for each line. They only show if **Untimed lyrics** is on (it is at first). Then the lines are spread evenly over the song.

## The lyrics are for the wrong song

- **More than one thing is playing.** The mod follows one player at a time. Pause the other one, or on the **Music** tab set **Player** to the app you want. The choices are different on Linux, Windows and macOS (see the [FAQ](FAQ.md#can-i-choose-which-player-it-follows)).
- **A browser tab is playing something else.** A muted video in another tab still counts as playing. Pause it or close it.
- **The song name is messy.** Video titles like "Artist - Song (Official Video)" are cleaned up before looking for lyrics, but some titles are too unusual to match, or LRCLIB matched a different version. Press **Retry** on the Music tab to look again.

## Lyrics come early or late

Lyrics can be a little off when the version you're hearing is different from the one the lyrics were timed to. You can fix it for just this song, and the fix is remembered next time it plays.

**In the settings:** open the **Music** tab while the song plays and move the **This song** slider. If the words come late, move it right. If they come early, move it left. It moves in steps of 0.05 seconds, up to 3 seconds either way.

**With keys, without leaving the game:** in **Options → Controls → Key Binds → Beli's SkyLyrics**, give keys to **Lyrics are late (this song)** and **Lyrics are early (this song)**. Each press moves the lyrics by 0.1 seconds, and a message above your hotbar shows the new timing.

A few more things to know:

- The fix is saved under the song's artist and title, so it also works when the same song plays in a different app.
- The mod remembers fixes for the last 500 songs.
- If *every* song is off by the same amount, use the **Timing** slider above **This song** instead. It moves all songs at once.

## Check that your player can be seen

### On Windows

The mod reads the media controls that Windows shows when you press a media key or change the volume. If your song shows up there, the mod can see it too.

**Quick check.** While the music plays, press a volume key on your keyboard. On Windows 11 you can also click the speaker icon in the taskbar. A small panel should show the song's name and the player. If it isn't there, the mod can't see it either.

**Spotify** works from spotify.com and from the Microsoft Store. If it doesn't show up in the panel, open Spotify's **Settings**, find **Display**, and turn on **Show desktop overlay when using media keys**.

**Browsers** (Chrome, Edge, Firefox and others) show any tab that plays sound, such as YouTube, YouTube Music or SoundCloud. If several tabs play, the mod follows the one that is playing. Pause the others if it picks the wrong one.

**VLC and mpv** don't show up in that panel on their own, so the mod can't see them.

**Music tab Status mentions PowerShell or "media controls".** To read the media controls the mod starts a small hidden PowerShell helper. PowerShell comes with Windows 10 and 11. The helper can't run if:

- your computer is on Windows 8 or older,
- a school or work computer blocks PowerShell scripts, or
- an antivirus program blocks it. Allowing Minecraft (Java) to run PowerShell fixes this.

The helper writes its own error messages to `.cache\skylyrics\windows\windows-media.log` in your Minecraft folder. That file is useful to include if you [ask for help](#still-stuck). If the helper fails, the mod tries again 15 seconds later on its own.

### On Linux

The mod finds your song through MPRIS, the Linux feature behind your desktop's media keys and music controls. If your desktop's music controls show the song, the mod can usually see it too.

**Quick check with playerctl.** Open a terminal and type:

```
playerctl -l
```

This lists the players that are visible. Then type:

```
playerctl metadata
```

If this shows the title and artist of your song, the mod can see it too. If `playerctl` isn't installed, install it from your system's package manager (it's usually called `playerctl`).

**Spotify from Flatpak** works normally. It shows itself to the system the same way the regular Spotify app does.

**Browsers** (Firefox, Chrome, Chromium, Brave and others) show any tab that plays sound, such as YouTube, YouTube Music or SoundCloud. If several tabs play, the mod follows the one that is playing. Pause the others if it picks the wrong one.

**mpv** needs the mpv-mpris plugin before it shows up.

**Prism Launcher from Flatpak.** If you run Minecraft through the Flatpak version of Prism Launcher, the launcher is kept in a sandbox that can't see your music players. Music tab Status then says "No music playing" or "Can't reach music players" even though music is playing. Allowing it to talk to music players should fix it. In a terminal, type:

```
flatpak override --user --talk-name=org.mpris.MediaPlayer2.* org.prismlauncher.PrismLauncher
```

Then close Prism Launcher completely and open it again. If you prefer a window to a terminal, the Flatseal app can do the same: pick Prism Launcher, and under **Session Bus → Talks** add `org.mpris.MediaPlayer2.*`.

### On macOS

On a Mac the mod can see only two apps: **Spotify** and Apple's **Music** app. macOS doesn't tell other apps what's playing, so the mod asks these two directly. YouTube and other sites in a browser, VLC and other players can't be seen on a Mac.

**The app has to be open.** The mod only asks Spotify or Music while they are already running, so it never opens them by itself. If both play at once, set **Player** on the Music tab to the one you want.

**Allow Minecraft to control Spotify and Music.** The first time, macOS shows a message like *"Minecraft" wants access to control "Spotify"*. Click **OK**. Depending on how you start the game, the message may name your launcher (for example Prism Launcher) or "java" instead of Minecraft. Until you answer, the Music tab says it is waiting for Spotify or Music.

If you clicked **Don't Allow**, the Music tab says "Allow Minecraft to control Spotify in System Settings → Privacy & Security → Automation". To fix it:

1. Open **System Settings**, then **Privacy & Security**, then **Automation**.
2. Find Minecraft, your launcher or "java" in the list, and turn on **Spotify** and **Music** under it.
3. Go back to the game. The mod notices within a second or two.

If nothing like that is in the list, you can make macOS ask again. Quit Minecraft, open the Terminal app and type:

```
tccutil reset AppleEvents
```

Then start Minecraft and play a song. Note that this makes macOS ask again for every app that controls other apps, not only Minecraft.

**No album cover for Music songs.** Spotify gives the mod a link to the cover, but the Music app doesn't, so the top bar shows no cover for songs played in Music.

**Music tab Status mentions osascript.** The mod talks to Spotify and Music with `osascript`, a tool that comes with every Mac. If it fails a few times in a row, the mod waits 15 seconds and then tries again on its own.

## Shaders make the lyrics dim

Shader packs darken the sky and draw clouds and fog over it. Open the settings, go to **Look** and set **Draw on top** to **Always**. Then the lyrics are drawn on top of the finished picture, so shaders, clouds, rain and fog can't dim them.

On **Auto** (the starting setting), the mod already draws on top when a shader pack is on, when it rains, or when **See through blocks** is on. **Never** keeps the lyrics inside the world, so hills and buildings can hide them.

In bright daytime without shaders, keep **Shadow** on (Look tab, on at first). It gives the words a dark edge that makes them easier to read against a pale sky.

## Opening the settings

- Press **J** while playing (not while another menu is open).
- If J does nothing, another mod may use the same key. Check **Options → Controls → Key Binds → Beli's SkyLyrics → Open settings** and pick a free key.
- With [Mod Menu](https://modrinth.com/mod/modmenu) installed you can also open them from the **Mods** list: select Beli's SkyLyrics and click its settings button.

## Where the files are

Everything is inside your Minecraft folder:

| What | Where |
| --- | --- |
| Settings | `config/skylyrics.json` |
| Saved lyrics | `.cache/skylyrics/` |

Your Minecraft folder is:

- **Official launcher on Linux:** `~/.minecraft` (`~` is your home folder; folders starting with a dot are hidden, press Ctrl+H in the file manager to see them).
- **Official launcher on Windows:** `%appdata%\.minecraft`. Paste that into the File Explorer address bar and press Enter.
- **Official launcher on macOS:** `~/Library/Application Support/minecraft`. In Finder, choose **Go → Go to Folder…**, paste that and press Return.
- **Prism Launcher:** right-click the instance and choose **Folder**. The `config` folder and the hidden `.cache` folder are inside.

To start over with all settings, use **Reset all** at the bottom of the settings screen (click it twice). Or close Minecraft and delete `skylyrics.json`.

Deleting the `skylyrics` cache folder is safe. The mod just looks up the lyrics again next time. Your per-song timing fixes are kept in the settings file, not in the cache.

## Still stuck?

Open an issue at [github.com/arbelonson-source/belis-skylyrics/issues](https://github.com/arbelonson-source/belis-skylyrics/issues). Say whether you're on Windows, macOS or Linux, which music player you use, what the **Music** tab shows for Status and Lyrics, and whether you use shaders.
