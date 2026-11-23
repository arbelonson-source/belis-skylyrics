# Troubleshooting

Something not working? Start with the problem that matches yours. For general questions, see [FAQ.md](FAQ.md).

The **Music** tab in the settings (press **J**) is the best place to start. It shows which player the mod is following and whether lyrics were found.

## No lyrics in the sky

Go through these in order:

1. **Is the mod turned on?** At the bottom left of the settings screen, under the list of tabs, there is a **Lyrics** switch. If you set a key for "Show or hide lyrics", you may have pressed it by accident.
2. **Are you looking the right way?** On the **Lyrics** tab, **Direction** can be set to N, E, S or W (north, east, south or west). Then the lyrics stay on that side of the sky. (**Direction** only shows in the World layout, or with the Arc and Lines layouts when **Placement** is **Sky**; with **Floating** it is hidden.) Turn around, or set Direction to **Follow** so they stay where you look. If they seem stuck in one spot, **View lock** (under **Position**) may be on: turn it off, or press your "Lock lyrics here" key if you set one.
3. **Are they hidden on purpose?** On the **Display** tab check **Hide with F1** (pressing F1 hides them along with the rest of the screen), **Hide in menus** (hidden while chat or any menu is open) and **Hide when paused** (hidden while the music is paused). In the Nether or the End, underwater, in a cave or on a server, also check **Hide in the Nether**, **Hide in the End**, **Hide underwater**, **Hide in caves** and **Hide in multiplayer**. The Nether and End switches also count a server's extra Nether and End worlds, such as a resource Nether.
4. **Does the mod see your music?** On the **Music** tab, **Status** should say "Following" and the name of your player. If it says "No music playing" (on a Mac: "No Spotify or Music playing (browsers can't be read on a Mac)") or "Can't reach music players", see [Check that your player can be seen](#check-that-your-player-can-be-seen). With **Spotify Web**, "Can't reach the internet or Spotify" means your internet (or Spotify) isn't answering: check that a web page opens. The mod keeps asking and carries on by itself once it is back. If the mod can't read your music player (or reach the internet, with **Spotify Web**) for 10 seconds, a pop-up says "SkyLyrics can't read your music player" (or "SkyLyrics can't reach the internet or Spotify"), once per game start; **Status** on the **Music** tab shows why, and the sections below say what to do.
5. **Were lyrics found?** The **Lyrics** line on the Music tab tells you:
   - **Looking for lyrics…**: wait a moment.
   - **Lyrics off**: the **Lyrics** switch is off (see step 1), so the song isn't looked up. Turning it on looks it up.
   - **No lyrics found for this song. Try Retry.**: neither LRCLIB nor NetEase Cloud Music, the two lyrics sites the mod uses, has this song. The mod can't show lyrics it doesn't have. It checks again after a few days, or press **Retry**. If a lyrics site answers with something odd, this is what you see too (not a message about your internet), and **Retry** asks again.
   - **Couldn't reach the lyrics sites. Check your internet, then press Retry.**: the mod uses two sites, LRCLIB first and NetEase Cloud Music as a backup, and it couldn't get lyrics from either. Try these in order:
     1. Check that your internet works (open a web page).
     2. Press **Retry**.
     3. If your internet is fine, one of the sites may be down or busy, and the other doesn't have this song. The mod keeps trying while the song plays, and doesn't remember the song as "no lyrics", so the lyrics usually appear by themselves once a site is back. Try again in a few minutes, or play another song.
     4. A school, work or antivirus network can block one of the sites. Try another network, or allow `lrclib.net` and `music.163.com`. A proxy that asks for a password shows this too: the mod keeps trying, and the lyrics come once the proxy lets it through.
     5. On hotel or café Wi-Fi you often have to log in first, and until you do, the lyrics site gets an empty page or a redirect instead of lyrics. The mod counts that as a failed try, not as "no lyrics", so the song isn't remembered as having none. Log in to the Wi-Fi, then press **Retry** or play the song again, and the lyrics come back.
   - **Instrumental, no words**: the song has no lyrics. A song whose lyrics are only music notes shows as instrumental too. So does a song where NetEase Cloud Music only has its "pure music, please enjoy" note.
   - **Found, without timing**: the lyrics exist but without times for each line. When LRCLIB only has untimed lyrics, the mod also asks NetEase Cloud Music for timed ones, and it asks again after a week. They only show if **Untimed lyrics** (Music tab) is on (it is at first). Then the lines are spread evenly over the song.
   - **A line shows as "line / translation"**: some lyrics files give a line and its translation (or romanization) the same time, so the mod shows both together. That is how the file is made.

## The lyrics are for the wrong song

- **More than one thing is playing.** The mod follows one player at a time. Pause the other one, or on the **Music** tab set **Player** to the app you want. The choices are different on Linux, Windows and macOS (see the [FAQ](FAQ.md#can-i-choose-which-player-it-follows)).
- **A browser tab is playing something else.** A muted video in another tab still counts as playing. Pause it or close it.
- **The song has no artist.** Browser audio often comes without an artist and without a length. Then the mod only accepts a lyrics match whose title is the same and, when the song's length is known, whose length is within about 3 seconds. When different artists have a song with that title, it shows no lyrics rather than guess and show another song's words, so a common title like "Intro" may show none. If you play the song in a player that shows the artist (like Spotify), the lyrics are found.
- **The song name is messy.** Video titles like "Artist - Song (Official Video)" or "Artist - Song | Official Video" are cleaned up before looking for lyrics, and a title like "Song - Skrillex Remix" is no longer taken for "Artist - Song". Neither is "Song - From "Film"", "Song - Recorded at …" or "Song - Bonus Track". A title with a version tag in brackets, like "Song (2011 Remaster)", is looked up again without the tag when nothing is found. Some titles are still too unusual to match, or LRCLIB matched a different version. If the Music tab says no lyrics were found, press **Retry** to look again. If lyrics were found but are the wrong ones, the mod keeps them for that song, and Retry can't look again. Play the song from a player that shows the artist (like Spotify) if you can. Lyrics that are only unsynced, or that came from the backup site, are looked up again after a week, and replaced if something better exists. If the words are only early or late, use the **This song** slider on the Music tab.

## Lyrics come early or late

Lyrics can be a little off when the version you're hearing is different from the one the lyrics were timed to. You can fix it for just this song, and the fix is remembered next time it plays.

**In the settings:** open the **Music** tab while the song plays and move the **This song** slider. If the words come late, move it right. If they come early, move it left. It moves in steps of 0.05 seconds, up to 5 seconds either way.

**With keys, without leaving the game:** in **Options → Controls → Key Binds → Beli's SkyLyrics**, give keys to **Lyrics are late (this song)** and **Lyrics are early (this song)**. Each press moves the lyrics by 0.1 seconds (hold the key to keep stepping), and a message above your hotbar shows the new timing.

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

The helper writes its own error messages to `.cache\skylyrics\windows\windows-media.log` in your Minecraft folder. That file is useful to include if you [ask for help](#still-stuck). If the helper fails, or starts but never answers, the mod tries again on its own, first after 15 seconds and then less and less often, up to every 5 minutes.

**"The Windows music reader is missing from the mod. Download SkyLyrics again"** means the SkyLyrics file in your `mods` folder is incomplete or damaged (a download cut short, or a copy someone repacked). Download the jar for your Minecraft version again from the [Releases page](https://github.com/arbelonson-source/belis-skylyrics/releases) and put it in place of the old one.

**"The Windows music reader stopped right away…"** The Music tab shows this when PowerShell started and quit at once. PowerShell's own message follows in brackets, for example one about Constrained Language Mode or AppLocker. That means the computer is set up to block PowerShell, which is common on school and work PCs. The mod can't change that, and only whoever looks after the PC (your school or IT) can allow it. You can use the mod anyway: on the **Music** tab set **Player** to **Spotify Web** and sign in to Spotify there. Spotify Web asks Spotify over the internet, so it doesn't need PowerShell, and while it is picked the mod stops the PowerShell helper (see "Can it follow Spotify on my phone or another computer?" in the [FAQ](FAQ.md)).

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

**No session bus.** Some containers and launchers have no desktop message bus, so there is nothing for the mod to ask. It then waits longer between tries (up to 30 seconds) and picks the music up when a bus shows up later. If you never see lyrics there, run Minecraft from your normal desktop session.

### On macOS

On a Mac the mod can see only two apps: **Spotify** and Apple's **Music** app. macOS doesn't tell other apps what's playing, so the mod asks these two directly. YouTube and other sites in a browser, VLC and other players can't be seen on a Mac.

**The app has to be open.** The mod only asks Spotify or Music while they are already running, so it never opens them by itself. If both play at once, set **Player** on the Music tab to the one you want.

**Allow your launcher to control Spotify and Music.** The first time, macOS shows a message like *"Minecraft" wants access to control "Spotify"*. Click **OK**. The name in the message is the app that started the game: Minecraft for the official launcher, but Prism Launcher, MultiMC, Modrinth App, CurseForge, Terminal or "java" for others. Until you answer, the Music tab says it is waiting for Spotify or Music.

If you clicked **Don't Allow**, the Music tab says "Allow your launcher (Minecraft, Prism Launcher…) to control Spotify in System Settings → Privacy & Security → Automation". To fix it:

1. Open **System Settings**, then **Privacy & Security**, then **Automation**.
2. Find your launcher (Minecraft, Prism Launcher…) or "java" in the list, and turn on **Spotify** and **Music** under it.
3. Go back to the game. The mod notices within about 15 seconds.

If nothing like that is in the list, you can make macOS ask again. Quit Minecraft, open the Terminal app and type:

```
tccutil reset AppleEvents
```

Then start Minecraft and play a song. Note that this makes macOS ask again for every app that controls other apps, not only your launcher.

**No album cover for Music songs.** Spotify gives the mod a link to the album cover, but the Music app doesn't, so the top bar shows no album cover for songs played in Music.

**Internet radio and ads.** When Music plays an internet radio station, the mod shows the song the station is playing now, not just the station's name. Spotify's ads are skipped: the mod doesn't look up lyrics for them. While an ad plays, the mod stays with Spotify, so it doesn't switch to a song paused in Music, and the play/pause key still reaches Spotify.

**One app frozen.** If Spotify or Music freezes (the spinning beach ball) while both are open, the mod stops waiting for the frozen one after 2 seconds, follows the other, and asks the frozen one again every few seconds.

**Music tab Status mentions osascript.** The mod talks to Spotify and Music with `osascript`, a tool that comes with every Mac. If it fails a few times in a row, the mod waits 15 seconds and then tries again on its own.

## The album cover is missing

The top bar and **Record card** show the cover your player gives. If it is a web link that can't be downloaded (no internet, a busy server, a hotel Wi-Fi sign-in page, or a proxy that asks for a password), the mod tries again every so often for about half an hour, then stops for that song. A cover the game can't read (an odd colour format or a cut-off file) is given up on straight away. Play the song again to try once more.

## The flashes or motion stopped, or seem gone

Check Minecraft's **Accessibility** settings. **Hide Sky Flashes** (called **Hide Lightning Flashes** on 1.21.8 and older) on turns off the lyrics' flashes (glow pulse, crosshair flash, hat flare, sky pulse and the World words' white flash when they land or you hit them), and **Distortion Effects** at 0 keeps the line pop, the World words' sway, knock and arrival bounce, the hat's wobble and spin and the spinning record still. The World words then only fade when they leave, fall, slide in or make room for each other. Turn the flashes back on, or raise **Distortion Effects**, to get them back.

With the flashes on, the World words' white flash still starts at most three times a second: in a fast line, the words that come quicker than that land in their own colour, and a word you click at quickly flashes only on some clicks. The flashes at a new line are held to three a second the same way. This is on purpose, for players sensitive to flashing light.

## Lyrics vanish in caves

With the Arc and Lines layouts, and **Hide in caves** (Display tab) off, the lyrics stay visible underground when **See through blocks** (Look tab) is on, when **Draw on top** (Look tab) is on **Always**, or, on **Auto**, when **Show underground too** (Display tab) is on. **Hide in caves** hides them on purpose when there is no open sky above you. See [the FAQ](FAQ.md#why-dont-i-see-lyrics-in-caves) for more.

## Shaders make the lyrics dim

Shader packs darken the sky and draw clouds and fog over it. Open the settings, go to **Look** and set **Draw on top** to **Always**. Then the lyrics are drawn on top of the finished picture, so shaders, clouds, rain and fog can't dim them.

**Draw on top** only shows with the Arc and Lines layouts. **Shadow** shows with Lines, and with Arc when **Depth** is off. The World layout always draws its words in the world itself (it has **Soft edges** instead of Shadow), so for shaders it helps to try Arc or Lines.

On **Auto** (the starting setting), the mod already draws on top when a shader pack is on, when it rains, when **See through blocks** is on, or when you are in a cave or under a roof (with **Show underground too** on). **Never** keeps the lyrics inside the world, so hills and buildings can hide them.

In bright daytime without shaders, keep **Shadow** on (Look tab, on at first). It gives the words a dark edge that makes them easier to read against a pale sky. In the Arc layout, **Shadow** only shows when **Depth** is off, because Depth already gives the letters a solid edge.

## Replay Mod and Flashback recordings

The mod draws the lyrics and the night sky live, from whatever is playing on your PC at the moment you watch or render the replay. It does not remember the song that played while you recorded, and it doesn't know a replay is running. The sky animation runs on real time, so a slowed down or sped up replay doesn't slow the lyrics or the sky.

If the video shows the wrong lyrics, or lyrics you didn't want:

- Turn the lyrics off before you render: press your "Show or hide lyrics" key (it has no key at first; set one under **Options → Controls → Key Binds → Beli's SkyLyrics**), or use the **Lyrics** switch in the settings.
- Or pause your music before you render.
- To have lyrics in the video, start the same song from the beginning right when the render starts.

## Distant Horizons

If you use Distant Horizons and see a painted night sky shape in front of far-away mountains or other distant terrain, that may be this mod. The painted sky is drawn at one set distance from you (at most 110 blocks, or just short of the fog for the sky-wide shapes), so terrain farther away than that can end up behind it. It could happen with **Synthwave** (its sun and sky reach the horizon), **Moonlight** (its low clouds drift round the horizon), **Eclipse** (the sun sits low) and **Comet** (the head sits low). You can try:

- On the **Look** tab, set **Night sky** to **Aurora**, **Nebula**, **Aurora + nebula** or **Galaxy**. Their shapes sit higher up in the sky.
- Or set **Night sky** to **Off**.

## Lyrics trail behind when I turn (Exordium)

Exordium redraws the HUD less often than the game draws frames, and the lyrics are drawn as part of the HUD, so they can lag behind your camera. Turn off Exordium's HUD buffering (in Exordium's own settings) and they follow smoothly again. The game log says the same when Exordium is installed.

## The top bar covers another mod's info (Jade, WTHIT, a minimap)

The song bar sits at the top centre of the screen, where Jade and WTHIT show what you're looking at, and a wide bar can reach a minimap in the top right corner. On the **Display** tab, set **Bar position** to **Bottom** (it sits just above the hotbar), or make it smaller with **Bar size**, or turn off **Clock**, **Ping** and **FPS** to make it narrower.

## The Record card moves or fades

The **Record card** steps out of the way of other things on screen. On the right it moves above or below the scoreboard sidebar, and fades if there is no room. In the bottom right it moves up above the subtitles while some show. While the chat is open, both bottom cards sit above its text box, and a card fades while the chat's messages cover it: in the bottom left most often, and in the other corners too in a small window or at a big GUI scale (not when chat is set to Hidden). A card in a top corner moves down below your potion effect icons when they reach it. A card in a top corner hides while the debug screen (F3) is open. A card in a bottom corner hides only while F3's text or one of F3's charts along the bottom of the screen reaches it (on a small window or at a big GUI scale, say). On Minecraft 1.21.9 and later, F3 lines you set to show always (in the debug options, F3 + F6) push a card in a top corner down below them, and it hides when they reach too far down. This is on purpose; it comes back when the other thing goes away.

## The top bar and the player list (Tab)

While you hold **Tab** on a server, the top bar and a **Record card** in a top corner fade out so the player list shows clearly, and they come back when you let go. A bar at the bottom stays. The same happens while the debug screen (F3) is open, except that a bar at the bottom hides too while F3's text or one of its charts reaches it. On Minecraft 1.21.9 and later, F3 lines you set to show always move the top bar down below them instead (it hides when they reach too far down).

## Spotify sign-in doesn't start

- **"Paste your Client ID first"** (under **Account** in the **Spotify account** card on the **Music** tab): **Sign in** needs your Spotify app's Client ID. Press **Paste** next to **Client ID**. Steps are in the [FAQ](FAQ.md#can-it-follow-spotify-on-my-phone-or-another-computer).
- **"Spotify sign-in needs port ... but another program is using it"**: close the other program or Minecraft game that holds that port (often a second game with SkyLyrics), then press **Sign in** again. The red line goes away when you press **Sign in** or **Sign out**.
- **"Can't reach the internet or Spotify"**: your internet (or Spotify) isn't answering. Check that a web page opens, then try again.
- **"Spotify no longer accepts your Client ID, check it and sign in again"**: your Spotify app was deleted or reset, so you are signed out. Check the Client ID in your app on the Spotify dashboard (or make a new app), paste it, and press **Sign in**.
- **"Sign-in timed out"**: the Spotify page in your browser wasn't answered within 5 minutes. Press **Sign in** again.
- **"If Spotify shows INVALID_CLIENT, check the Client ID and that your app has the Redirect URI above, exactly as shown"**: the Music tab says this while the Spotify page is open in your browser. If that page says INVALID_CLIENT ("Invalid redirect URI", say), Spotify never sends you back to the game. On the Spotify dashboard, check that your app's Client ID is the one you pasted and that the app has the Redirect URI exactly as the settings show it (press **Copy** next to **Redirect URI** and paste it again). Then press **Cancel** and **Sign in** again.
- **Spotify is busy**: when Spotify says there are too many requests, the mod waits as long as Spotify asks before trying again. Usually no message shows; if it happens while you sign in or the sign-in is renewed, it says "Spotify is getting too many requests, wait a moment". There is nothing for you to do but wait.
- **"Spotify is having problems right now"**: Spotify's own servers are failing. The mod keeps trying by itself; to sign in, press **Sign in** again a little later.
- **"Sign-in failed: Something went wrong, try again"**: Spotify didn't take this sign-in, or sent an answer the mod couldn't read. Press **Sign in** again. What Spotify said is in the game log, which helps if you [ask for help](#still-stuck).

## The mod says a new version is out, but won't install it

**It needs something newer.** If the pop-up says "SkyLyrics 0.9.0 needs Fabric API 0.137.0 or newer" (or a newer Minecraft), the new version wouldn't start on what you have, so the mod doesn't install it and your working version stays. Update what it names (Fabric API, say) and the next start installs the new SkyLyrics. Until then the mod remembers the refusal, so it doesn't download the same update again on every start; a newer release, or updating Fabric API, Fabric Loader, Minecraft or Java, makes it check again. A newer Minecraft needs the SkyLyrics jar for that version from the [Releases page](https://github.com/arbelonson-source/belis-skylyrics/releases).

**Windows.** The update is swapped in when you close the game, with a small PowerShell script. Any user name or folder name works: accents, Hebrew, Cyrillic, spaces, & and ’ included. The old jar is only deleted once the new one is in place, and it is put back if that fails, so you are never left without the mod. If the new version isn't there after you close and restart the game, download the new jar for your Minecraft version from the Releases page, put it in your `mods` folder and delete the old SkyLyrics jar. You can also set **About → Updates** to **Tell me** if you only want the notice.

Other reasons for the notice: the download failed (a slow connection, say; one that stops halfway is given up after 5 minutes and the unfinished file is removed from your `mods` folder), or it wasn't accepted. The mod only uses a download from its own GitHub releases, only if it is Beli's SkyLyrics in the version it expected, and refuses one over 20 MB.

**The mod file is a link.** If the SkyLyrics file in your `mods` folder is a link to a file somewhere else (Nix and GNU Stow do this), the mod still tells you about the new version, but it can't replace the file. Download the new jar from the Releases page and swap it in yourself.

If you launch with a launcher that links the `mods` or instance folder to somewhere else, updates still work.

**A packwiz modpack.** If SkyLyrics came with a modpack that packwiz keeps up to date each time you press Play, the mod only tells you about a new version: the pack's installer would put the old jar back next to the new one. The pack's maker updates SkyLyrics in the pack. If you added SkyLyrics to the pack yourself, it still updates itself.

## Settings or Spotify sign-in not saved on Windows

Antivirus, OneDrive or the Windows search indexer sometimes hold a file open for a moment. The mod tries again for up to about a second before giving up, for the settings, the Spotify sign-in, saved album covers and the Windows music reader's helper file. When the game starts, it also tries reading the settings and the Spotify sign-in again, so you don't start signed out of Spotify because the file was held. If a setting still won't stick, update to the newest version first.

## Two Minecraft games open at once

Two games can share one Minecraft folder (two instances, or a game and a test copy). When one saves its settings or song timings it keeps what the other changed, so they don't undo each other. This also works across Minecraft versions: going back to an older SkyLyrics, or running two Minecraft versions of the mod on the same folder, no longer wipes the settings the other one saved, and your per-song timings are kept. They share one Spotify sign-in: when both renew it at the same moment, the second waits briefly for the first one's new sign-in.

## Hebrew or Arabic looks wrong

- **Boxes in the lyrics.** Invisible left-to-right or right-to-left marks in copied lyrics no longer show as small boxes in the sky, the top bar or the **Record card**. They still set the word order. Hidden codes that force a direction on a whole line (copied from some websites) are removed, so the line no longer shows backwards.
- **Numbers with the game in Hebrew or Arabic.** The **Record card**'s time left reads "-2:35", and the **Height**, **Distance** and **Size** sliders read the right way round ("-10°", "0.50×"). If you still see one the other way round, update to the newest version.

## Very long words and fast lyrics

- **A very long word or web address in the top bar** shows its start while it is sung, not its end.
- **Fast lyrics (rap).** In **World** and **Arc**, all the words of a short, quick line now show: World lets the last words out before the next line takes over, and Arc fades in the words not yet out as the line leaves.

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

To start over with all settings, use **Reset all** at the bottom of the settings screen: click it, then click again within 3 seconds while it says "Click again to reset" (a double-click counts as one click). It keeps your finished intro tour, your update choice, your Spotify app id (Client ID), your per-song timings and your recent searches. Or close Minecraft and delete `skylyrics.json`.

Deleting the `skylyrics` cache folder is safe, but it holds more than lyrics. The mod looks up the lyrics and covers again next time, the **Recently heard** list starts empty, and you are signed out of Spotify Web. Your per-song timing fixes are kept in the settings file, not in the cache.

## My settings went back to default, or the Recently heard list is empty

If the settings file has a value the mod can't read (a typo, or a value from another version), only that setting goes back to its default and the rest are kept. The mod keeps a copy of the file as `skylyrics.json.bad` next to it in the `config` folder, so nothing is lost. If the whole file is unreadable, the mod starts with the defaults and keeps the copy the same way.

The **Recently heard** list does the same: a damaged `heard.json` is kept as `heard.json.bad` in `.cache/skylyrics`, and one bad song in it no longer drops the others. A `heard.json` or `spotify-login.json` over 1 MB is too big to be ours: it is set aside the same way (as `.bad`), the game log says so, and the list starts empty or you are signed out of Spotify Web.

A wrongly typed value is fixed the next time the mod saves, so it doesn't make a new `.bad` copy every time you start the game.

If Windows, an antivirus or OneDrive holds the settings file when the game starts, the mod tries again. If it still can't read it, it leaves the file alone and doesn't save defaults over your settings, so closing and reopening the game once the lock is gone gets your settings back. A half-written `.tmp` file next to the settings, the Spotify sign-in, the Recently heard list or the update memory, or one left by the Windows music reader's helper, when the game was killed, is cleaned up on a later start, once it is a day old.

To get your old values back, close Minecraft, fix the `.bad` file (or copy the values you need from it), and put it back under the original name. A damaged file is copied to `.bad`; if a `.bad` already exists it is kept and the new copy goes to `.bad.1` to `.bad.9`, and only when all ten are taken is the oldest one replaced. The same damaged file is not copied twice. A file the mod can't open at all (for example because of its permissions) is moved aside instead, as `.bad`, or `.bad.1` to `.bad.9` when that name is taken, so no earlier copy is lost.

## Still stuck?

Open an issue at [github.com/arbelonson-source/belis-skylyrics/issues](https://github.com/arbelonson-source/belis-skylyrics/issues). The game log (`logs/latest.log` in your Minecraft folder) is fine to attach: it doesn't show song names or your home folder. Say whether you're on Windows, macOS or Linux, which music player you use, what the **Music** tab shows for Status and Lyrics, and whether you use shaders.
