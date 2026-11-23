# Questions and answers

Short answers about Beli's SkyLyrics. If something isn't working, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## What does the mod do?

While you play Minecraft and listen to music on the same computer, it shows the words of the song in the sky, in time with the music. A bar at the top of the screen shows the album cover, the song name and the line being sung.

## Which Minecraft versions does it work on?

Minecraft 1.21.1, 1.21.4, 1.21.5, 1.21.6 to 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3, with Fabric and a recent Fabric API (each jar needs a minimum one; if yours is too old, Minecraft tells you which version to install). The jar for 1.21.10 works on 1.21.10 only, not 1.21.9. Each release has one jar per version, so pick the one whose name ends in your version (see "Install" in the [README](../README.md#install)).

## Does it work on Windows or macOS?

**Windows 10 and 11: yes.** The mod reads the same media controls that Windows shows when you press a media key or change the volume. To do that it runs a small hidden PowerShell helper. PowerShell comes with Windows, so there is nothing extra to install. While **Player** is set to **Spotify Web**, the helper isn't needed, so it stops until you pick another player.

**macOS: with Spotify and Apple Music only.** A Mac doesn't tell other apps what's playing, so the mod asks the Spotify and Music apps directly. Browsers and other players can't be seen. The first time, macOS asks whether your launcher (Minecraft, Prism Launcher…) may control Spotify or Music. Click **OK**. If something goes wrong, see "On macOS" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md#on-macos).

On Linux the mod finds your song through MPRIS, the feature behind the media keys and the music controls on your desktop.

## Which music players work?

**On Windows:** anything that shows up in the little media panel when you press a media key or a volume key. For example:

- Spotify, from spotify.com or from the Microsoft Store
- Chrome, Edge, Firefox, Brave, Opera and other browsers, so YouTube, YouTube Music, SoundCloud and similar sites in a tab
- Media Player, Apple Music, TIDAL, Deezer and most other music apps

VLC and mpv don't show up in that panel on their own, so the mod can't see them.

**On Linux:** anything that shows up in your desktop's media controls. For example:

- Spotify, including the Flatpak version
- Firefox, Chrome, Chromium, Brave and other browsers, so YouTube, YouTube Music, SoundCloud and similar sites in a tab
- VLC, Rhythmbox, Elisa, Strawberry and most other music apps
- mpv, once the mpv-mpris plugin is installed

**On macOS:** only two apps:

- Spotify, the desktop app
- Music, Apple's own music app (Apple Music and your own songs)

The app has to be open; the mod never opens it by itself. YouTube and other sites in a browser, VLC and other players don't work on a Mac. Music's internet radio shows the song the station is playing now, and Spotify's ads are skipped.

If you're not sure, see "Check that your player can be seen" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Can I choose which player it follows?

Yes. Open the settings with **J**, go to the **Music** tab and change **Player**. On Linux the choices are Auto, Spotify, Firefox, Chrome, mpv, VLC and Spotify Web. On Windows they are Auto, Spotify, Chrome, Edge, Firefox, Media Player and Spotify Web. On macOS they are Auto, Spotify, Music and Spotify Web. **Spotify Web** follows your Spotify account, even when the song plays on another device (it needs the one-time Spotify set-up on the **Music** tab; see "Can it follow Spotify on my phone or another computer?"). **Auto** follows whatever is playing, and sticks with the same player while it keeps playing, so two browser tabs don't fight over it.

## Can it follow Spotify on my phone or another computer?

Yes, with **Spotify Web**. The mod then follows your Spotify account instead of a player on this computer, so it works when the song plays on your phone or another computer. Spotify makes you set up a small free app of your own once:

1. Open the settings with **J**, go to the **Music** tab and set **Player** to **Spotify Web**. A **Spotify account** section appears.
2. Next to **Spotify dashboard**, press **Open** and confirm the link. It needs a Spotify Premium account. Log in and create an app. Any name works.
3. In the app's form, paste the **Redirect URI** and add it. Press **Copy** next to **Redirect URI** in the settings to get it. Tick **Web API**, then save.
4. On the dashboard, copy only your app's Client ID (32 letters and numbers). In the settings, press **Paste** next to **Client ID**. **Sign in** stays greyed out until it looks right.
5. Press **Sign in**, confirm the link in the game, then agree on the Spotify page that opens in your browser. The settings then say "Signed in". **Sign out** is in the same place.

If Spotify says it blocked the app, open the app's **User Management** on the dashboard and add your own Spotify account. If the mod says Spotify needs Premium on the account that created your Spotify app, the account you made the app with has no Premium: log in to the dashboard with your Premium account, make the app there, and paste its Client ID. If the mod says another program is using the sign-in port, close the other game or app and try again.

## Where do the lyrics come from?

From [LRCLIB](https://lrclib.net), a free lyrics database run by volunteers. You don't need an account or any set-up. The mod sends the song's title, artist, album and length to LRCLIB to find a match. Lyrics it finds are saved on your computer, so the same song doesn't have to be looked up again.

Since version 0.7.8, when LRCLIB is down, doesn't have a song or only has untimed lyrics for it, the mod asks [NetEase Cloud Music](https://music.163.com) instead, a big music service that also has timed lyrics, including for many songs that aren't in English. It sends NetEase only the song's title and artist. No account is needed for either. To avoid showing the wrong song's words, it only uses a NetEase match when the title matches exactly and the artist or the song length matches too.

If neither has a song, the mod can't show lyrics for it.

## Do I need Spotify, or Spotify Premium?

No. To show lyrics you don't need Spotify at all, and no Premium: the mod follows whatever player you already use (see "Which music players work?" above).

Spotify only matters for the **song library** (press **N**; inside it, a plain **N** starts a search, and **Esc**, or **Ctrl** (**Cmd** on a Mac) with **N**, closes it; **Alt** or AltGr with **N** types a letter instead). If you have signed in to Spotify on the **Music** tab, it shows your liked songs and playlists. Starting a song from there needs Spotify Premium (on Linux, a single song can also open in the Spotify app without it). If you haven't signed in, the library shows a **Recently heard** list instead: the songs you have played lately. It is kept on your computer only, and you can switch it off or empty it on the **Music** tab (**Recently heard**, and the **Forget recently heard** row with its **Clear** button that empties the list). Clicking a song in that list asks the player that played it to play it again. That works on Linux, when that player is open and can do it; on Windows and macOS the list is there to look at, and the mod says so when a player can't start the song.

## Does it need internet?

Only to look up lyrics for songs it hasn't seen before, and to download album covers that your player gives as a web link. Songs whose lyrics are already saved work offline. It also checks GitHub once when the game starts to see if a new version is out, unless **About → Updates** is **Off**. While **Lyrics** is off, no song is looked up and no album cover is downloaded for the song playing (the **Music** tab's **Lyrics** row says "Lyrics off", and the **song library** still shows its covers when you open it); turning it back on looks up the song playing then. (With **Spotify Web** signed in, the mod still asks Spotify what is playing every 15 seconds.)

Without internet, a song that isn't saved shows "Couldn't reach the lyrics sites. Check your internet, then press Retry." on the **Music** tab, and the lyrics come by themselves once you are back online. An album cover that couldn't download is tried again every so often for about half an hour. With **Spotify Web**, the status says "Can't reach the internet or Spotify" and the mod keeps asking, waiting a little longer each time (30 seconds at most). LRCLIB is only set aside for a couple of minutes when it is really down or too busy, not because one song's lookup failed.

## How do I update it?

From 0.7.0 on, it updates itself. When a new version is out, it downloads in the background and a pop-up tells you to restart Minecraft; the new version is used from the next start. To choose this yourself, open the settings, go to **About → Updates** and pick **Install**, **Tell me** (just a notice) or **Off**. An update is only downloaded from the mod's own GitHub releases, and only used if it really is Beli's SkyLyrics in the expected version. If the new version needs a newer Minecraft or Fabric API than you have, it isn't installed: the pop-up says what to update (for example "Update Fabric API to get it"), and the version you have keeps working. If the download fails, you still get the "new version available" message (see [TROUBLESHOOTING.md](TROUBLESHOOTING.md#the-mod-says-a-new-version-is-out-but-wont-install-it)). In a modpack that packwiz keeps up to date each time you press Play, it only tells you about the new version, and the pack's maker updates it. On Windows it works with any user name, accents, Hebrew or spaces included. Versions before 0.7.0 can't update themselves: download the new jar from the [Releases page](https://github.com/arbelonson-source/belis-skylyrics/releases) and replace the old one in your `mods` folder.

## Does it work with shaders, Sodium and Iris?

Yes. Shader packs can make the sky darker and paint clouds over things, so the mod can draw the lyrics on top of the finished picture to keep them bright. This is the **Draw on top** setting under **Look**. On **Auto** it does this whenever a shader pack is on, when it rains, when **See through blocks** is on, or when you are in a cave or under a roof (see "Why don't I see lyrics in caves?"). If the lyrics still look dim, set it to **Always**.

## Does it work with Replay Mod or Flashback?

The mod runs, but it follows the music on your PC right now, not the music from when you recorded. See [Troubleshooting](TROUBLESHOOTING.md#replay-mod-and-flashback-recordings) for what to do before you render a video.

## Can I calm the flashes and the motion?

Yes, with Minecraft's own **Accessibility** settings. Turning on **Hide Sky Flashes** (called **Hide Lightning Flashes** on 1.21.8 and older) turns off the glow pulse, the crosshair flash, the hat flare, the sky pulse and the World words' white flash when they appear or you hit them. **Distortion Effects** calms the motion: at 0 the line pop, the World words' sway, knock and arrival bounce, the hat's wobble and spin, and the spinning record stay still, and in between they are slower or smaller. At 0 the World words also just fade when they leave, fall, join a flowing line or make room for each other, with no rising, falling, sliding or gliding. It doesn't change the motion you pick in the mod itself, such as the **Rise** and **Slide** animations, **Float up** or **Sky speed**.

When the flashes are on, the ones at a new line start at most three times a second, and so do the World words' white flashes: words that come faster land in their own colour, and clicking quickly at a word doesn't flash it on every click.

**Notification Time** (same screen) keeps the song's name up longer after a song change, on the top bar and as the World layout's title, and the song library's messages too.

## Does it work with Distant Horizons?

It may show some night sky shapes in front of far-away terrain. See [Troubleshooting](TROUBLESHOOTING.md#distant-horizons) for which skies can do that and what to pick instead.

## Why don't I see lyrics in caves?

With the **Arc** and **Lines** layouts, the lyrics stay visible underground at first, because **See through blocks** (Look tab) is on. With it off, **Show underground too** on the **Display** tab keeps them visible, and it works while **Draw on top** (Look tab) is on **Auto**. If you can't see them in a cave, check these:

- **Show underground too** is on. It only shows up when the layout is Arc or Lines, **Draw on top** is **Auto**, **See through blocks** is off (with it on, the lyrics are already on top everywhere) and **Hide in caves** is off.
- **Hide in caves** (Display tab) is off. It hides the lyrics on purpose when there is no open sky above you, and it wins over Show underground too.
- **Draw on top** is not on **Never**, which keeps the lyrics inside the world, where rock can hide them.

The **World** layout puts its words in the world around you, so with **See through blocks** off, blocks can hide them. It is on at first; keep it on (Look tab) to see them through walls.

## In the World layout, a long line keeps starting over at the top. Is that a bug?

No. With **Flowing line** on (World layout, Lyrics tab), a long line is written in rows. It starts over at the top, and its old words fade out, when it runs into a hillside, when a word finds no clear spot, or when its next row would run off the bottom of the screen. That last check follows where you look and your field of view, and keeps clear of the hotbar. If you look steeply down (bridging, digging), the line starts a little above where you look, so it stays in view. In a tunnel or right by a wall it carries on instead, lifted out of the rock where there is room. For fewer restarts, turn **Size** down.

## Can I pin the lyrics in one place?

Yes. Turn on **View lock** under **Lyrics → Position**. The lyrics are pinned where you are looking right now and stay there while you look around. You can also give a key to **Lock lyrics here** (Options → Controls → Key Binds → Beli's SkyLyrics) and press it to lock and unlock without opening the settings. Press it again, or turn the switch off, to let the lyrics follow you again (with **Direction** on N, E, S or W they go back to that side of the sky, and the message says "Lyrics unlocked"). In the World layout, new words gather around the spot you locked. View lock shows when the lyrics are in the sky or the layout is World. With **Floating** lyrics (Arc or Lines) nothing can be pinned, so the key only says "Only lyrics in the sky can be locked".

## How do I open the settings?

Press **J** in game. If you have [Mod Menu](https://modrinth.com/mod/modmenu), you can also open them from the mod list: find Beli's SkyLyrics and click the settings button. Changes show right away on the lyrics behind the screen. The **Preview** button hides the settings for a moment so you can see the whole sky (click or press any key to come back). The **Music** tab also has previous, play/pause and next buttons, and you can click the progress bar to jump in the song.

## Can I use the settings with the keyboard?

Yes, you don't need the mouse. **Tab** or **Up** and **Down** move from row to row, **Left** and **Right** change a choice or a slider, **Enter** or **Space** switches the row or presses its button, and **Page Up** and **Page Down** scroll. **Ctrl+Tab** goes to the next tab (**Ctrl+Page Down** and **Ctrl+Page Up** go to the next and previous tab, **Cmd** on a Mac) and **Ctrl+F** (**Cmd+F** on a Mac) starts a search. Ctrl+C, Ctrl+X and Ctrl+F go by the letter on your keyboard layout (Dvorak, Colemak, AZERTY), also on Minecraft 26.3. The song library's grid, shelf and carousel buttons, recent searches and **Open Music tab** button can be reached with the keyboard (press Up from the top row of songs, then Left and Right; Down goes back). In the song library, Space plays the chosen song, and the sign-in and Try again buttons work from the keyboard too.

## Does it work with the Narrator?

Yes. With Minecraft's **Narrator** on (**Narrates All** or **Narrates System**), each settings row reads out its name, the kind of control and its value (like "Layout, choice, Arc, 1 of 3"), and, as the keyboard lands on it, its place among the settings that show, in Minecraft's own words ("Selected list row 3 out of 12"). With nothing playing, the **Now playing** card on the **Music** tab also says where to play a song when the keyboard reaches it. A press of **Reset all** says "Click again to reset", and the second one "Settings reset". The song library reads out the song you are on and its place in the whole list ("50 of 300"), which song is playing now, how many songs a list has once it has loaded, each section with its place ("Liked songs, 2 of 3"), the buttons, the search box, how many songs a search found, and what Enter does. The intro tour reads its page too.

It also reads what the mod writes above the hotbar when you press one of its keys (lyrics on or off, a song's timing, the view lock), its pop-ups (a new version, that it can't read your music player, or that something went wrong in one of its screens) and its line in chat about a new version.

## Can I change the keys?

Yes. In Minecraft go to **Options → Controls → Key Binds** and scroll to **Beli's SkyLyrics**. Only **Open settings** (J) and **Open song library** (N) have a key at first. These have no key until you pick one:

- Show or hide lyrics
- Show now-playing card
- Play / pause music
- Next song
- Previous song
- Lock lyrics here (View lock)
- Lyrics are late (this song)
- Lyrics are early (this song)

Each key acts once per press, except the two timing keys, which keep stepping while you hold them.

## What if a song's lyrics have no timing, or come with a translation?

Lyrics without timing are spread evenly over the song, as long as **Untimed lyrics** on the **Music** tab is on (it is at first). In the World layout they come out word by word along each line, and the last line leaves when the song is over. Some lyrics files give a line and its translation (or its romanization) the same time. They show together as "line / translation", in every layout.

## The lyrics come too early or too late. Can I fix it?

Yes, and the fix is remembered for that song. See "Lyrics come early or late" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## What can I change about the look?

- **Lyrics tab:** the layout: **Arc** (the line being sung on a gentle curve), **Lines** (the previous, current and next lines, straight) or **World** (each word or two appears in the world around you as it's sung). Then in the sky far away or floating close by, which direction, height, distance (in blocks in the sky, never beyond 80% of your render distance, and nearer at a low render distance so they stay in front of the night sky; the slider shows how far they really are; floating, how far in front of you, 4 to 16; in the World layout a multiplier from 0.5x to 2x) and size, whether to show the previous and next lines (with Lines), the karaoke fill, a bigger current word, the animation (with Lines: Words, Fade, Rise, Pop, Type or Slide), how World words arrive (Pop, Drop, Zoom, Fade or Type, letter by letter) and leave (Shrink, Float, Blow, Sink or Dissolve), the pulse when a new line starts, capital letters, **Soft edges** (World words with only a glow and no dark outline), and **View lock** (under **Position**). Also under **Position**: **Lazy follow** (in the sky with **Direction** on **Follow**, for the Arc and Lines layouts: the lyrics wait until you turn away, then glide over) and, in the World layout, **Elytra mode** (the words travel with you when you glide, ride or fly). The World layout has more rows of its own: under **Position**, **Words at once**, **Linger**, **Tilt**, **Float up**, **Flowing line** with its **Line curve**, **Keep the whole line**, **Keep the middle clear**, **Slide apart**, **Keep off players**, **Hit to knock**, **Sky tint**, **Death animation** and **Song title**; under **Lines**, **One big word**.
- **Look tab:** **Themes**, one-click looks to start from; your own colour (a soft periwinkle at first), the album cover's colour, **Time of day**, **Each line** (a new neon colour with every line) or rainbow; **Two-tone**, where each line fades into a second colour you pick; the letter style (Bold, Smooth, Round, Handwritten or Minecraft); **Depth** (Arc layout only) for a solid 3D edge on the letters; glow (a deeper shade of the letters' colour, or one you pick), shadow and opacity; the night sky (Off, Aurora, Nebula, Synthwave, Eclipse, Aurora + nebula, Moonlight, Galaxy, Comet or Random) with **Sky colours** (Classic, Album or Lyrics), **Sky speed**, its strength, **Shooting stars** and **Twinkling stars**; **Constellations**, star patterns joined by thin lines; a **Hat** for your character; and a **Crosshair** style.
- **Display tab:** under **Hide**, hide the lyrics with F1, in menus or when the music is paused, hide them in the Nether, the End (on a server, its extra Nether and End worlds, such as a resource Nether, count too), underwater or in caves, keep them visible underground (**Show underground too**), hide them in multiplayer (**Hide in multiplayer**, so they only show in your own worlds). The Nether, End, underwater, cave and multiplayer switches hide the top bar, the **Record card** and the night sky too. Under **Top bar**, turn the bar on or off, and choose what it shows: the line being sung, a clock on its left, and your frames per second and ping on its right. **Song name only on change** is on at first (the song name hides after a few seconds, longer with Minecraft's **Notification Time** turned up), **Show bar** on **Lyrics out of view** hides the bar while you can see the lyrics in the world, and **Bar position** puts the bar at the top or just above the hotbar. **Bar size**, **Background** (how dark the bar and the Record card are), **Accent colour**, **Album cover** and **Spin like a record** change its look. The **Record card** switch adds a small vinyl card with the song and its progress, **Corner** picks where it sits, **Lyric line** shows the line being sung under the record, and **Card lines** (with Lyric line on) can show the line before and the line after it too. The top bar can also show an **Equaliser** (on by default) and a **Song progress** line. **Main menu** puts the lyrics on the title screen. **Performance → Effects** (Auto, Full or Light) trades some glow for more frames per second.

In the World layout, some settings are hidden because that layout doesn't use them: **Draw on top**, **Depth**, **Shadow**, **Pulse** and **Bigger current word** (and **Karaoke fill**, unless the top bar shows lyrics, where it still lights up the bar's words). In Arc and Lines, **Bigger current word** also needs **Karaoke fill** on. **Depth** shows only in Arc, and **Shadow** shows in Lines, and in Arc when **Depth** is off. Switch to Arc or Lines to see them again; your values are kept.

## Is the mod in Hebrew, Spanish, Portuguese, German, French, Russian or Ukrainian?

The settings screen and the top bar are in English, Spanish, Portuguese (Brazilian and European), German, French, Russian, Ukrainian and Hebrew. They follow your Minecraft language, so pick one of them in Minecraft's language settings to get it. Spanish comes in the Minecraft choices for Spain, Mexico, Argentina, Chile, Ecuador, Uruguay and Venezuela, Portuguese for Brazil and Portugal (each has its own texts), German for Germany, Austria and Switzerland, and French for France and Canada. Russian and Ukrainian each have their own texts too, with Minecraft's own names for its menus. Any other Minecraft language shows English. Numbers follow your Minecraft language too: in one that writes a decimal comma (German, French, Russian, Ukrainian, Portuguese and most Spanish, for example), the settings' numbers and the timing message above the hotbar have one ("1,25×", "+0,25 s"). A Hebrew or Arabic lyric line with an English phrase in it (or the other way round) reads each part in its own direction in the Arc layout, and in World with **Flowing line** on. Invisible direction marks in copied lyrics no longer show as boxes, and the time left on the **Record card** reads "-2:35" with the game in Hebrew or Arabic.

## Why does the clock show 24 hours, or AM and PM?

The clock on the top bar writes the time the way your Minecraft language does, not the way your computer does: 24 hours ("21:07") in German, French, Russian, Hebrew, British English and most other languages, and AM and PM in a few, such as Latin American Spanish ("9:07 p. m.") or Australian English. In American English, Minecraft's starting language, and in its made-up languages such as Pirate Speak, it follows your computer's language and region instead. There is no separate 12 or 24 hour switch; **Clock** under **Display → Top bar** turns the clock off.

## Can I try it without music?

Yes. Turn on **Demo song** in the **Music** tab. It plays a short built-in song with made-up lyrics, so you can try the settings. The top bar shows "Demo" next to the song while it plays, and turning **Demo song** on starts it from the top.

## Does it need to be on the server?

No. It only runs on your own computer (client side). It works in single player and on any server, and the server doesn't need it.

## Where are my settings saved?

In a file called `skylyrics.json` in the `config` folder of your Minecraft folder. Saved lyrics and the **Recently heard** list are in `.cache/skylyrics` in the same Minecraft folder. See "Where the files are" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

Two Minecraft games open at once can share one folder: when one saves, it keeps what the other changed. If Windows, an antivirus or OneDrive holds a file for a moment, the mod tries again, and settings are saved in the background, so the game doesn't pause while they are written; a settings file it still can't read is left alone, not overwritten with defaults.

## Can two Minecraft versions share one folder?

Yes. If two games (for example 1.21.11 and a newer version) use the same Minecraft folder, they share one settings file. Each keeps the settings the other one saved, so they don't undo each other. Going back to SkyLyrics 0.8.0 keeps your per-song timings, but 0.8.0 and older versions don't know the settings added after it: once one of them saves (when you change something in it), those settings are back at their defaults when you update again. See "Two Minecraft games open at once" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md#two-minecraft-games-open-at-once).

## Is this the Neon Lyrics mod?

No. It's a free, open-source mod with a similar idea, written from scratch. It isn't connected to Neon Lyrics or its maker.

## Is it free?

Yes, and it always will be. The code is open source under the [AGPL-3.0 license](../LICENSE). The lyrics themselves belong to their writers and are not part of this project.
