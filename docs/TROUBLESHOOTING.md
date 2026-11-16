# Troubleshooting

Something not working? Start with the problem that matches yours. For general questions, see [FAQ.md](FAQ.md).

The **Music** tab in the settings (press **J**) is the best place to start. It shows which player the mod is following and whether lyrics were found.

## No lyrics in the sky

Go through these in order:

1. **Is the mod turned on?** At the bottom left of the settings screen, under the list of tabs, there is a **Lyrics** switch. If you set a key for "Show or hide lyrics", you may have pressed it by accident.
2. **Are you looking the right way?** On the **Lyrics** tab, **Direction** can be set to N, E, S or W (north, east, south or west). Then the lyrics stay on that side of the sky. Turn around, or set Direction to **Follow** so they stay where you look. If they seem stuck in one spot, **View lock** (under **Position**) may be on: turn it off, or press your "Lock lyrics here" key if you set one.
3. **Are they hidden on purpose?** On the **Display** tab check **Hide with F1** (pressing F1 hides them along with the rest of the screen), **Hide in menus** (hidden while chat or any menu is open) and **Hide when paused** (hidden while the music is paused).
4. **Does the mod see your music?** On the **Music** tab, **Status** should say "Following" and the name of your player. If it says "No music playing" or "Can't reach music players", see [Check that your player can be seen](#check-that-your-player-can-be-seen). With **Spotify Web**, "Can't reach the internet or Spotify" means your internet (or Spotify) isn't answering: check that a web page opens. The mod keeps asking and carries on by itself once it is back.
5. **Were lyrics found?** The **Lyrics** line on the Music tab tells you:
   - **Looking for lyrics…**: wait a moment.
   - **No lyrics found for this song. Try Retry.**: neither LRCLIB nor NetEase Cloud Music, the two lyrics sites the mod uses, has this song. The mod can't show lyrics it doesn't have. It checks again after a few days, or press **Retry**.
   - **Couldn't reach the lyrics sites. Check your internet, then press Retry.**: the mod uses two sites, LRCLIB first and NetEase Cloud Music as a backup, and it couldn't get lyrics from either. Try these in order:
     1. Check that your internet works (open a web page).
     2. Press **Retry**.
     3. If your internet is fine, one of the sites may be down or busy, and the other doesn't have this song. The mod keeps trying while the song plays, and doesn't remember the song as "no lyrics", so the lyrics usually appear by themselves once a site is back. Try again in a few minutes, or play another song.
     4. A school, work or antivirus network can block one of the sites. Try another network, or allow `lrclib.net` and `music.163.com`.
     5. On hotel or café Wi-Fi you often have to log in first, and until you do, the lyrics site gets an empty page or a redirect instead of lyrics. The mod counts that as a failed try, not as "no lyrics", so the song isn't remembered as having none. Log in to the Wi-Fi, then press **Retry** or play the song again, and the lyrics come back.
   - **Instrumental, no words**: the song has no lyrics. A song whose lyrics are only music notes shows as instrumental too.
   - **Found, without timing**: the lyrics exist but without times for each line. They only show if **Untimed lyrics** (Music tab) is on (it is at first). Then the lines are spread evenly over the song.
   - **A line shows as "line / translation"**: some lyrics files give a line and its translation (or romanization) the same time, so the mod shows both together. That is how the file is made.

## The lyrics are for the wrong song

- **More than one thing is playing.** The mod follows one player at a time. Pause the other one, or on the **Music** tab set **Player** to the app you want. The choices are different on Linux, Windows and macOS (see the [FAQ](FAQ.md#can-i-choose-which-player-it-follows)).
- **A browser tab is playing something else.** A muted video in another tab still counts as playing. Pause it or close it.
- **The song has no artist.** Browser audio often comes without an artist and without a length. Then the mod only accepts a lyrics match whose title is the same, and when different artists have a song with that title, it shows no lyrics rather than guess and show another song's words. If you play the song in a player that shows the artist (like Spotify), the lyrics are found.
- **The song name is messy.** Video titles like "Artist - Song (Official Video)" are cleaned up before looking for lyrics, but some titles are too unusual to match, or LRCLIB matched a different version. Press **Retry** on the Music tab to look again.

## Lyrics come early or late

Lyrics can be a little off when the version you're hearing is different from the one the lyrics were timed to. You can fix it for just this song, and the fix is remembered next time it plays.

**In the settings:** open the **Music** tab while the song plays and move the **This song** slider. If the words come late, move it right. If they come early, move it left. It moves in steps of 0.05 seconds, up to 5 seconds either way.

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

**A music player that stopped answering.** If one player is stuck (a frozen browser tab, say), the mod no longer waits for it: it goes on with the players that answer, and the lyrics and the other players keep working. If the song you want isn't followed, pause or close the stuck one, or set **Player** on the Music tab to the app you want.

**Playback speed.** If you play a podcast or video faster or slower (1.5x, 2x), the lyrics keep time with it on Linux players and on Windows players that report their speed.

**Minecraft from a Flatpak (Prism Launcher, for one).** If you run Minecraft through a Flatpak, such as the Flatpak version of Prism Launcher, it is kept in a sandbox that can't see your music players. Music tab Status then says "No music playing" or "Can't reach music players" even though music is playing, and the message names the command to use. Allowing it to talk to music players should fix it. For Prism Launcher, in a terminal, type:

```
flatpak override --user --talk-name=org.mpris.MediaPlayer2.* org.prismlauncher.PrismLauncher
```

For another Flatpak, put its own app id at the end instead of `org.prismlauncher.PrismLauncher` (the message in the game shows it). Then close the launcher completely and open it again. If you prefer a window to a terminal, the Flatseal app can do the same: pick Prism Launcher, and under **Session Bus → Talks** add `org.mpris.MediaPlayer2.*`.

### On macOS

On a Mac the mod can see only two apps: **Spotify** and Apple's **Music** app. macOS doesn't tell other apps what's playing, so the mod asks these two directly. YouTube and other sites in a browser, VLC and other players can't be seen on a Mac.

**The app has to be open.** The mod only asks Spotify or Music while they are already running, so it never opens them by itself. If both play at once, set **Player** on the Music tab to the one you want.

**Allow your launcher to control Spotify and Music.** The first time, macOS shows a message like *"Minecraft" wants access to control "Spotify"*. Click **OK**. The name in the message is the app that started the game: Minecraft for the official launcher, but Prism Launcher, MultiMC, Modrinth App, CurseForge, Terminal or "java" for others. Until you answer, the Music tab says it is waiting for Spotify or Music.

If you clicked **Don't Allow**, the Music tab says "Allow your launcher (Minecraft, Prism Launcher…) to control Spotify in System Settings → Privacy & Security → Automation". To fix it:

1. Open **System Settings**, then **Privacy & Security**, then **Automation**.
2. Find your launcher (Minecraft, Prism Launcher…) or "java" in the list, and turn on **Spotify** and **Music** under it.
3. Go back to the game. The mod notices within a second or two.

If nothing like that is in the list, you can make macOS ask again. Quit Minecraft, open the Terminal app and type:

```
tccutil reset AppleEvents
```

Then start Minecraft and play a song. Note that this makes macOS ask again for every app that controls other apps, not only your launcher.

**No album cover for Music songs.** Spotify gives the mod a link to the album cover, but the Music app doesn't, so the top bar shows no album cover for songs played in Music.

**Music tab Status mentions osascript.** The mod talks to Spotify and Music with `osascript`, a tool that comes with every Mac. If it fails a few times in a row, the mod waits 15 seconds and then tries again on its own.

## Lyrics vanish in caves

With the Arc and Lines layouts, the lyrics stay visible underground as long as **Show underground too** (Display tab) is on, **Draw on top** (Look tab) is on **Auto**, and **Hide in caves** (Display tab) is off. **Hide in caves** hides them on purpose when there is no open sky above you. See [the FAQ](FAQ.md#why-dont-i-see-lyrics-in-caves) for more.

## Shaders make the lyrics dim

Shader packs darken the sky and draw clouds and fog over it. Open the settings, go to **Look** and set **Draw on top** to **Always**. Then the lyrics are drawn on top of the finished picture, so shaders, clouds, rain and fog can't dim them.

On **Auto** (the starting setting), the mod already draws on top when a shader pack is on, when it rains, when **See through blocks** is on, or when you are in a cave or under a roof (with **Show underground too** on). **Never** keeps the lyrics inside the world, so hills and buildings can hide them.

In bright daytime without shaders, keep **Shadow** on (Look tab, on at first). It gives the words a dark edge that makes them easier to read against a pale sky.

## Lyrics trail behind when I turn (Exordium)

Exordium redraws the HUD less often than the game draws frames, and the lyrics are drawn as part of the HUD, so they can lag behind your camera. Turn off Exordium's HUD buffering (in Exordium's own settings) and they follow smoothly again. The game log says the same when Exordium is installed.

## The mod says a new version is out, but won't install it

**It needs something newer.** If the pop-up says "SkyLyrics 0.9.0 needs Fabric API 0.137.0 or newer" (or a newer Minecraft), the new version wouldn't start on what you have, so the mod doesn't install it and your working version stays. Update what it names (Fabric API, say) and the next start installs the new SkyLyrics. A newer Minecraft needs the SkyLyrics jar for that version from the [Releases page](https://github.com/arbelonson-source/belis-skylyrics/releases).

**Windows.** The update is swapped in when you close the game, with a small PowerShell script. Any user name or folder name works: accents, Hebrew, Cyrillic, spaces, & and ’ included. The old jar is only deleted once the new one is in place, and it is put back if that fails, so you are never left without the mod. If the new version isn't there after you close and restart the game, download the new jar for your Minecraft version from the Releases page, put it in your `mods` folder and delete the old SkyLyrics jar. You can also set **About → Updates** to **Tell me** if you only want the notice.

Other reasons for the notice: the download failed (a slow connection, say; one that stops halfway is given up after 5 minutes and the unfinished file is removed from your `mods` folder), or it wasn't accepted. The mod only uses a download from its own GitHub releases, only if it is Beli's SkyLyrics in the version it expected, and refuses one over 20 MB. If you launch with a launcher that links the `mods` or instance folder to somewhere else, updates still work.

## Settings or Spotify sign-in not saved on Windows

Antivirus, OneDrive or the Windows search indexer sometimes hold a file open for a moment. The mod tries again for up to about a second before giving up, for the settings, the Spotify sign-in, saved album covers and the Windows music reader's helper file. If a setting still won't stick, update to the newest version first.

## Two Minecraft games open at once

Two games can share one Minecraft folder (two instances, or a game and a test copy). When one saves its settings or song timings it keeps what the other changed, so they don't undo each other. They share one Spotify sign-in: when both renew it at the same moment, the second waits briefly for the first one's new sign-in.

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
| Recently heard list | `.cache/skylyrics/heard.json` |
| Spotify Web sign-in | `.cache/skylyrics/spotify-login.json` |

Your Minecraft folder is:

- **Official launcher on Linux:** `~/.minecraft` (`~` is your home folder; folders starting with a dot are hidden, press Ctrl+H in the file manager to see them).
- **Official launcher on Windows:** `%appdata%\.minecraft`. Paste that into the File Explorer address bar and press Enter.
- **Official launcher on macOS:** `~/Library/Application Support/minecraft`. In Finder, choose **Go → Go to Folder…**, paste that and press Return.
- **Prism Launcher:** right-click the instance and choose **Folder**. The `config` folder and the hidden `.cache` folder are inside.

To start over with all settings, use **Reset all** at the bottom of the settings screen (click it twice). It keeps your finished intro tour, your update choice, your Spotify app id (Client ID), your per-song timings and your recent searches. Or close Minecraft and delete `skylyrics.json`.

Deleting the `skylyrics` cache folder is safe, but it holds more than lyrics. The mod looks up the lyrics and covers again next time, the **Recently heard** list starts empty, and you are signed out of Spotify Web. Your per-song timing fixes are kept in the settings file, not in the cache.

## My settings went back to default, or the Recently heard list is empty

If the settings file has a value the mod can't read (a typo, or a value from another version), only that setting goes back to its default and the rest are kept. The mod keeps a copy of the file as `skylyrics.json.bad` next to it in the `config` folder, so nothing is lost. If the whole file is unreadable, the mod starts with the defaults and keeps the copy the same way.

The **Recently heard** list does the same: a damaged `heard.json` is kept as `heard.json.bad` in `.cache/skylyrics`, and one bad song in it no longer drops the others.

If Windows, an antivirus or OneDrive holds the settings file when the game starts, the mod tries again. If it still can't read it, it leaves the file alone and doesn't save defaults over your settings, so closing and reopening the game once the lock is gone gets your settings back. A half-written `.tmp` file next to the settings or the Spotify sign-in, left when the game was killed, is cleaned up on a later start, once it is a day old.

To get your old values back, close Minecraft, fix the `.bad` file (or copy the values you need from it), and put it back under the original name. A damaged file is copied to `.bad`; if a `.bad` already exists it is kept and the new copy goes to `.bad.1` to `.bad.9`, and only when all ten are taken is the oldest one replaced. The same damaged file is not copied twice. A file the mod can't open at all (for example because of its permissions) is moved aside instead, as `.bad`, or `.bad.1` to `.bad.9` when that name is taken, so no earlier copy is lost.

## Still stuck?

Open an issue at [github.com/arbelonson-source/belis-skylyrics/issues](https://github.com/arbelonson-source/belis-skylyrics/issues). Say whether you're on Windows, macOS or Linux, which music player you use, what the **Music** tab shows for Status and Lyrics, and whether you use shaders.
