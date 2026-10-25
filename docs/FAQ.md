# Questions and answers

Short answers about Beli's SkyLyrics. If something isn't working, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## What does the mod do?

While you play Minecraft and listen to music on the same computer, it shows the words of the song in the sky, in time with the music. A bar at the top of the screen shows the album cover, the song name and the line being sung.

## Does it work on Windows or macOS?

Not yet. The mod finds your song through MPRIS, a Linux feature (the same one that makes the media keys and the music controls on your desktop work). On Windows and macOS the mod still loads, but it can't see your music, so only the built-in demo song works.

## Which music players work?

Anything that shows up in your Linux desktop's media controls. For example:

- Spotify, including the Flatpak version
- Firefox, Chrome, Chromium, Brave and other browsers, so YouTube, YouTube Music, SoundCloud and similar sites in a tab
- VLC, Rhythmbox, Elisa, Strawberry and most other music apps
- mpv, once the mpv-mpris plugin is installed

If you're not sure, see "Check that your player can be seen" in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Can I choose which player it follows?

Yes. Open the settings with **J**, go to the **Music** tab and change **Player**. The choices are Auto, Spotify, Firefox, Chrome, mpv and VLC. **Auto** follows whatever is playing, and sticks with the same player while it keeps playing, so two browser tabs don't fight over it.

## Where do the lyrics come from?

From [LRCLIB](https://lrclib.net), a free lyrics database run by volunteers. You don't need an account. The mod sends the song's title, artist, album and length to LRCLIB to find a match. Lyrics it finds are saved on your computer, so the same song doesn't have to be looked up again.

If LRCLIB doesn't have a song, the mod can't show lyrics for it.

## Does it need internet?

Only to look up lyrics for songs it hasn't seen before, and to download album covers that your player gives as a web link. Songs whose lyrics are already saved work offline.

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

- **Lyrics tab:** in the sky far away or floating close by, which direction, height, distance and size, whether to show the previous and next lines, the karaoke fill, the animation (Words, Fade, Rise, Pop or Type), the pulse when a new line starts, and capital letters.
- **Look tab:** colour from the album cover, your own colour or rainbow; the letter style (Smooth, Handwritten or Minecraft); glow, shadow and opacity; and the night sky (Off, Aurora or Nebula) with its strength.
- **Display tab:** hide the lyrics with F1, in menus or when the music is paused, and turn the top bar on or off.

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
