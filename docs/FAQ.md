# Questions and answers

Short answers about Beli's SkyLyrics. If something isn't working, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## What does the mod do?

While you play Minecraft and listen to music on the same computer, it shows the words of the song in the sky, in time with the music. A bar at the top of the screen shows the album cover, the song name and the line being sung.

## Does it work on Windows or macOS?

**Windows 10 and 11: yes.** The mod reads the same media controls that Windows shows when you press a media key or change the volume. To do that it runs a small hidden PowerShell helper. PowerShell comes with Windows, so there is nothing extra to install.

**macOS: with Spotify and Apple Music only.** A Mac doesn't tell other apps what's playing, so the mod asks the Spotify and Music apps directly. Browsers and other players can't be seen. The first time, macOS asks whether Minecraft may control Spotify or Music. Click **OK**. If something goes wrong, see "On macOS" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md#on-macos).

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

The app has to be open; the mod never opens it by itself. YouTube and other sites in a browser, VLC and other players don't work on a Mac.

If you're not sure, see "Check that your player can be seen" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Can I choose which player it follows?

Yes. Open the settings with **J**, go to the **Music** tab and change **Player**. On Linux the choices are Auto, Spotify, Firefox, Chrome, mpv and VLC. On Windows they are Auto, Spotify, Chrome, Edge, Firefox and Media Player. On macOS they are Auto, Spotify and Music. **Auto** follows whatever is playing, and sticks with the same player while it keeps playing, so two browser tabs don't fight over it.

## Where do the lyrics come from?

From [LRCLIB](https://lrclib.net), a free lyrics database run by volunteers. You don't need an account. The mod sends the song's title, artist, album and length to LRCLIB to find a match. Lyrics it finds are saved on your computer, so the same song doesn't have to be looked up again.

If LRCLIB doesn't have a song, the mod can't show lyrics for it.

## Does it need internet?

Only to look up lyrics for songs it hasn't seen before, and to download album covers that your player gives as a web link. Songs whose lyrics are already saved work offline. It also checks GitHub once when the game starts to see if a new version is out.

## How do I update it?

From 0.7.0 on, it updates itself. When a new version is out, it downloads in the background and a pop-up tells you to restart Minecraft; the new version is used from the next start. To choose this yourself, open the settings, go to **About → Updates** and pick **Install**, **Tell me** (just a notice) or **Off**. Versions before 0.7.0 can't update themselves: download the new jar from the [Releases page](https://github.com/arbelonson-source/belis-skylyrics/releases) and replace the old one in your `mods` folder.

## Does it work with shaders, Sodium and Iris?

Yes. Shader packs can make the sky darker and paint clouds over things, so the mod can draw the lyrics on top of the finished picture to keep them bright. This is the **Draw on top** setting under **Look**. On **Auto** it does this whenever a shader pack is on, when it rains, or when **See through blocks** is on. If the lyrics still look dim, set it to **Always**.

## How do I open the settings?

Press **J** in game. If you have [Mod Menu](https://modrinth.com/mod/modmenu), you can also open them from the mod list: find Beli's SkyLyrics and click the settings button. Changes show right away on the lyrics behind the screen. The **Preview** button hides the settings for a moment so you can see the whole sky (click or press any key to come back). The **Music** tab also has previous, play/pause and next buttons, and you can click the progress bar to jump in the song.

## Can I change the keys?

Yes. In Minecraft go to **Options → Controls → Key Binds** and scroll to **Beli's SkyLyrics**. Only **Open settings** (J) has a key at first. These have no key until you pick one:

- Show or hide lyrics
- Show now-playing card
- Play / pause music
- Next song
- Previous song
- Lyrics are late (this song)
- Lyrics are early (this song)

## The lyrics come too early or too late. Can I fix it?

Yes, and the fix is remembered for that song. See "Lyrics come early or late" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## What can I change about the look?

- **Lyrics tab:** the layout: **Arc** (the line being sung on a gentle curve), **Lines** (the previous, current and next lines, straight) or **World** (each word or two appears in the world around you as it's sung). Then in the sky far away or floating close by, which direction, height, distance and size, whether to show the previous and next lines (with Lines), the karaoke fill, a bigger current word, the animation (with Lines: Words, Fade, Rise, Pop, Type or Slide), how World words arrive (Pop, Drop, Zoom, Fade or Type, letter by letter) and leave (Shrink, Float, Blow, Sink or Dissolve), the pulse when a new line starts, and capital letters.
- **Look tab:** your own colour (a soft periwinkle at first), the album cover's colour or rainbow; the letter style (Bold, Smooth, Round, Handwritten or Minecraft); **Depth** for a solid 3D edge on the letters; glow (a deeper shade of the letters' colour, or one you pick), shadow and opacity; **Soft edges** for World words with only a glow and no dark outline; the night sky (Off, Aurora, Nebula, Synthwave, Eclipse, Aurora + nebula, Moonlight, Galaxy or Random) with its strength; and **Constellations**, star patterns joined by thin lines.
- **Display tab:** hide the lyrics with F1, in menus or when the music is paused, turn the top bar on or off, and choose what it shows: the line being sung, a clock on its left, and your frames per second and ping on its right. The **Record card** switch adds a small vinyl card with the song and its progress, **Corner** picks where it sits, and **Lyric line** shows the line being sung under the record.

## Is the mod in Hebrew?

The settings screen and the top bar are in English and Hebrew. They follow your Minecraft language, so pick Hebrew in Minecraft's language settings to get it.

## Can I try it without music?

Yes. Turn on **Demo song** in the **Music** tab. It plays a short built-in song with made-up lyrics, so you can try the settings.

## Does it need to be on the server?

No. It only runs on your own computer (client side). It works in single player and on any server, and the server doesn't need it.

## Where are my settings saved?

In a file called `skylyrics.json` in the `config` folder of your Minecraft folder. Saved lyrics are in `.cache/skylyrics` in the same Minecraft folder. See "Where the files are" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Is this the Neon Lyrics mod?

No. It's a free, open-source mod with a similar idea, written from scratch. It isn't connected to Neon Lyrics or its maker.

## Is it free?

Yes, and it always will be. The code is open source under the [AGPL-3.0 license](../LICENSE). The lyrics themselves belong to their writers and are not part of this project.
