### Fixed

- **No more lyrics from the wrong song.** When the lyrics site (LRCLIB) doesn't have the song, the mod no longer shows lyrics from a different song, even one by the same artist. A title with a translation or romanisation in brackets still finds its lyrics.
- **Long titles.** A very long song title, especially one with a bracket left open, no longer makes the lyrics lookup hang.
- **Quick skips.** After a quick skip, the new song never shows the old song's lyrics, cover or colour, a late lyrics retry for the old song can no longer get in the way of the new song's lyrics, and the new cover isn't held up by the old one's slow download.
- **Rare lyric files.** Lyrics with an hour in their time stamps, extra digits after the seconds, or wide (full-width) brackets no longer lose those lines.
- **Instrumental lines.** A line that is only "(Instrumental)", "(music)" or "Instrumental" now counts as a pause instead of being shown as words. A line that is just "Music" is still sung.
- **NetEase lyrics.** When the lyrics come from the NetEase backup, a song whose words contain "纯音乐" (Chinese for "instrumental") no longer loses them; only NetEase's own "instrumental" placeholder counts.
- **Effects after loading.** With **Effects** on Auto, reloading resources or switching shaders no longer leaves the lyrics on Light effects for up to two minutes, and a single stutter no longer counts as a slow game (stutter that keeps coming still does).
- **Lyrics on time on a Mac.** On a Mac, lyrics no longer run a little late.
- **Steady timing on Windows.** On Windows, a player that resumes no longer makes the lyrics skip ahead and back, and one that never says when it updated no longer freezes the lyrics.
- **Windows and Mac music readers.** On Windows, a cover that stalls can no longer make the music reader time out and restart, a player that is slow to answer once no longer makes the lyrics blink away, a cover that was still the last song's is read again a few seconds later, and Pause, Skip and Seek never reach another app when the one you follow has closed. On a Mac, a song with something missing (for example a local file or podcast with no cover) still shows its title and lyrics. The Windows changes were tested on GitHub's Windows machine with a made-up player, not with a real one.
- **World words stay put.** On Linux, a music player that answers slowly, or Spotify right after a pause or resume, no longer makes the World words vanish for a moment. Moving the **Timing** slider no longer makes them blink.
- **World title.** In the World layout the song title shows for its full time even when the lyrics take a moment to load, and a long title is shrunk to fit.
- **World after respawning.** Respawning or changing world during the end of a line no longer flashes its last words, and a word more than 48 blocks away fades out instead of vanishing. Swinging your arm while the World lyrics are hidden no longer knocks a word when they show again.
- **Song library.** Songs in a long list show up right away as you scroll, and can't be clicked before they appear. If more of a list fails to load, the library tries again a few times by itself; in the grid view you can also click the error line. Spotify playlists with local files or podcasts no longer repeat songs or stop loading partway.
- **Library keys.** Pressing Space on a playlist opens it without jumping to search. Pressing Enter after changing the search no longer plays a song from the old results.
- **Open song library key.** With Iris installed, an **Open song library** key you set to K (the same key as Iris's shader switch) is moved back to N only once, not on every start.
- **Album covers.** A cover shows even when it can't be saved for next time, and a damaged saved cover is fetched again. A cover that stalls while downloading is cut off instead of blocking the next song's cover. A cover the game can't read no longer leaves the last song's colour. A cover that fails to save no longer leaves a stray file behind, and a cover scrolled past can no longer cause a second download of a newer one.
- **Bottom bar.** The bottom bar sits above the held item's name. A bigger **Bar size** grows upward instead of off the screen.
- **Bottom bar and card.** A bar at the bottom of the screen now rises above the hearts and armour when you have several rows of them, and above the text that a jukebox or a command shows over the hotbar. The Record card in a bottom corner now sits above the bar instead of covering it, and above the armour and the held item's name.
- **Big bars fit.** A big **Bar size** on a narrow screen now fits: the bar shrinks and, if needed, hides the stats and then the clock.
- **Bar and F3.** A bar at the top hides while the debug screen (F3) is showing.
- **Record card.** The Record card in a top corner moves below the boss bars on a narrow screen.
- **Minecraft 1.21.1 to 1.21.5.** On 1.21.1 the top bar's text is clipped right at every **Bar size**, and the lyrics, bar and card no longer cover the chat. On 1.21.1 to 1.21.5, setting descriptions are no longer cut off at the edge of the list.
- **Caves and the Nether.** **Show underground too** now also works in the Nether (when **See through blocks** is off). Swimming deep underwater no longer counts as being in a cave.
- **Spyglass and sky lines.** The thin lines of the painted skies (Synthwave, Moonlight) and the constellations keep their width while you zoom with a spyglass, and with Speed or sprinting, instead of getting about 11 times too thick when zoomed.
- **Twinkling stars in World.** With the World layout the sparkles stay with the words even when **Lazy follow** was left on from another layout.
- **Sky effects.** With **Lazy follow** on or off, the sparkles and shooting stars now follow your view the same way the lyrics do, and they change line at the same moment as the words. They stay visible in front of the eclipse.
- **Sky fades.** The sky fades back in after leaving the Nether or the End, or coming up out of water, instead of popping in. It fades out under Blindness and Darkness, as the vanilla sky does. The Comet sky no longer flickers on 26.2 and later.
- **Propeller.** With **Glow with the lyrics** off, the propeller cap turns at its calm speed.
- **Lyrics stuck after a bad camera value.** A bad camera value can no longer pin the lyrics in the wrong place or stick the **View lock**.
- **Clicks on 26.3.** On Minecraft 26.3, a click outside the game window no longer counts as a click inside it.
- **Spotify asks less.** With Spotify signed in, when the lyrics are off and none of the mod's screens is open, the mod asks Spotify for the song only every 15 seconds. When Spotify says it is busy or keeps failing, the mod waits longer before asking again.
- **Spotify gaps.** With Spotify, a moment when Spotify says nothing is playing (around an ad, a track change or a switch to another device) no longer makes the lyrics blink out and come back up to 4 s late: the mod asks again after a second. An ad is no longer taken for a song.
- **Same name, two songs.** When one Spotify song follows another with the same title and artist (like two "Interlude"s), the mod now sees a new song: it looks up the second one's lyrics and starts them from the top instead of carrying on with the first one's. The two still share their per-song **Timing**.
- **Spotify odd answers.** A Spotify answer without a play state no longer causes an error, and Spotify's own ads no longer look up lyrics for "Spotify - Spotify".
- **Two game copies, one Spotify login.** When two copies of the game share a folder, one renewing the Spotify login no longer signs the other one out.
- **Spotify 403.** A Spotify app that doesn't know your account now tells you to add it under User Management on the Spotify dashboard, instead of "error 403".
- **Spotify signed out.** If Spotify refuses your sign-in even after the mod renewed it, the mod now says "Spotify signed you out, sign in again" instead of "error 401", and waits longer before trying again.
- **No crash from a drawing error.** A rare error in the mod's drawing no longer crashes the game. Only the part that failed (lyrics, title-screen lyrics, sky, bar, card, hat or crosshair) pauses for 5 seconds, and the error goes to the log.
- **Font mods.** Fixes a clash with other font mods on Minecraft 1.21.1 and 1.21.4, and a small memory leak when a letter can't be loaded.
- **Intro tour.** The intro tour fits small windows and big GUI scales. It no longer opens over a screen that replaced the title screen, and its last page says the keys work in a world.
- **Reset all.** **Reset all** keeps your intro tour done, your update choice, your Spotify app id, your per-song timings and your recent searches.
- **Hand-edited settings file.** A blank or very long recent search in the settings file no longer breaks the song library, and a hand-typed player name like "Spotify-Web" now picks that player.
- **Settings kept when Windows locks the file.** If Windows, an antivirus or OneDrive briefly holds your settings or recently heard file at start, the mod now tries again and, if it still can't read it, leaves the file alone instead of saving defaults over your settings. A file nobody can read at all (for example left owned by another user) is moved aside to a .bad copy so settings save again. A damaged file's backup copy is no longer replaced by an empty one, and the recently heard file is saved the same careful way as the settings.
- **Settings scroll.** **Open Music tab** in the song library now takes you down to the Spotify card every time. Settings tabs also keep their place after you visit a shorter tab.
- **Settings text and sliders.** The **Timing** and **This song** sliders now reach 5 seconds either way, and a sky **Strength** of 0 in the settings file now shows as 10%, where the slider starts. Settings search also finds English words in a Hebrew game, and pasted search text is cleaned up like typed text. A few English and Hebrew texts say what they mean (the previous line, **Preview**, **Aurora**).
- **Height and Arc.** The **Height** setting is hidden for Arc when it has no effect (Floating, or Direction Follow), but still found by search. Changing Direction, Placement, Height or the view lock moves the Arc lines that use them right away instead of from the next line.
- **Last line fades.** In Lines and Arc, a line that ended more than a second and a half ago (the last lyric during the outro, or a line before a long instrumental part) fades out.
- **Floating distance.** With Floating placement, every step of the **Distance** slider now moves the lyrics (before, 8 to 24 looked the same, and so did 96 to 160). The default looks the same as before.
- **Faint glow on 1.21.1 to 1.21.5.** A very faint glow (the previous and next lines, the end of the pulse) no longer flashes as solid colour.

### Faster

- **Lighter lyrics and Galaxy.** Lines and Arc lyrics and the Galaxy sky do less work each frame.
- **Lighter Aurora and Nebula.** The default Aurora sky, the Nebula clouds and stars, and the constellations skip the parts behind you or off the side of the screen. They look the same and cost less each frame.

### Changed

- **One name for the album cover.** The album picture is called "album cover" everywhere in the settings and help pages.
