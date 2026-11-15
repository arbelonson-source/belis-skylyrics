### Fixed

- **No more lyrics from the wrong song.** When the lyrics site (LRCLIB) doesn't have the song, the mod no longer shows lyrics from a different song, even one by the same artist. A title with a translation or romanisation in brackets still finds its lyrics.
- **Long titles.** A very long song title, especially one with a bracket left open, no longer makes the lyrics lookup hang.
- **Quick skips.** After a quick skip, a late lyrics retry for the old song can no longer get in the way of the new song's lyrics.
- **Lyrics on time on a Mac.** On a Mac, lyrics no longer run a little late.
- **Steady timing on Windows.** On Windows, a player that resumes no longer makes the lyrics skip ahead and back, and one that never says when it updated no longer freezes the lyrics.
- **World words stay put.** On Linux, a music player that answers slowly, or Spotify right after a pause or resume, no longer makes the World words vanish for a moment. Moving the **Timing** slider no longer makes them blink.
- **World title.** In the World layout the song title shows for its full time even when the lyrics take a moment to load, and a long title is shrunk to fit.
- **World after respawning.** Respawning or changing world during the end of a line no longer flashes its last words, and a word more than 48 blocks away fades out instead of vanishing. Swinging your arm while the World lyrics are hidden no longer knocks a word when they show again.
- **Song library.** Songs in a long list show up right away as you scroll, and can't be clicked before they appear. If more of a list fails to load, the library tries again a few times by itself; in the grid view you can also click the error line. Spotify playlists with local files or podcasts no longer repeat songs or stop loading partway.
- **Library keys.** Pressing Space on a playlist opens it without jumping to search. Pressing Enter after changing the search no longer plays a song from the old results.
- **Open song library key.** With Iris installed, an **Open song library** key you set to K (the same key as Iris's shader switch) is moved back to N only once, not on every start.
- **Album covers.** A cover shows even when it can't be saved for next time, and a damaged saved cover is fetched again. A cover that stalls while downloading is cut off instead of blocking the next song's cover. A cover the game can't read no longer leaves the last song's colour.
- **Bottom bar.** The bottom bar sits above the held item's name. A bigger **Bar size** grows upward instead of off the screen.
- **Big bars fit.** A big **Bar size** on a narrow screen now fits: the bar shrinks and, if needed, hides the stats and then the clock.
- **Bar and F3.** A bar at the top hides while the debug screen (F3) is showing.
- **Record card.** The Record card in a top corner moves below the boss bars on a narrow screen.
- **Minecraft 1.21.1 to 1.21.5.** On 1.21.1 the top bar's text is clipped right at every **Bar size**, and the lyrics, bar and card no longer cover the chat. On 1.21.1 to 1.21.5, setting descriptions are no longer cut off at the edge of the list.
- **Caves and the Nether.** **Show underground too** now also works in the Nether (when **See through blocks** is off). Swimming deep underwater no longer counts as being in a cave.
- **Sky effects.** With **Lazy follow** on or off, the sparkles and shooting stars now follow your view the same way the lyrics do, and they change line at the same moment as the words. They stay visible in front of the eclipse.
- **Sky fades.** The sky fades back in after leaving the Nether or the End, or coming up out of water, instead of popping in. It fades out under Blindness and Darkness, as the vanilla sky does. The Comet sky no longer flickers on 26.2 and later.
- **Propeller.** With **Glow with the lyrics** off, the propeller cap turns at its calm speed.
- **Lyrics stuck after a bad camera value.** A bad camera value can no longer pin the lyrics in the wrong place or stick the **View lock**.
- **Clicks on 26.3.** On Minecraft 26.3, a click outside the game window no longer counts as a click inside it.
- **Spotify asks less.** With Spotify signed in, when the lyrics are off and none of the mod's screens is open, the mod asks Spotify for the song only every 15 seconds. When Spotify says it is busy or keeps failing, the mod waits longer before asking again.
- **Spotify 403.** A Spotify app that doesn't know your account now tells you to add it under User Management on the Spotify dashboard, instead of "error 403".
- **No crash from a drawing error.** A rare error in the mod's drawing no longer crashes the game. Only the part that failed (lyrics, title-screen lyrics, sky, bar, card or crosshair) pauses for 5 seconds, and the error goes to the log.
- **Font mods.** Fixes a clash with other font mods on Minecraft 1.21.1 and 1.21.4, and a small memory leak when a letter can't be loaded.
- **Intro tour.** The intro tour fits small windows and big GUI scales. It no longer opens over a screen that replaced the title screen, and its last page says the keys work in a world.
- **Reset all.** **Reset all** keeps your intro tour done, your update choice, your Spotify app id, your per-song timings and your recent searches.
- **Settings scroll.** **Open Music tab** in the song library now takes you down to the Spotify card every time. Settings tabs also keep their place after you visit a shorter tab.
- Reset all keeps your intro tour done, your update choice, your Spotify app id and your per-song timings.
- Moving the Timing slider no longer makes the World words blink.
- Spotify playlists with local files or podcasts no longer repeat songs or stop loading partway.
- If loading more of a list fails, the library tries again.
- Pressing Space on a playlist opens it without jumping to search.
- Songs in a long list show up right away as you scroll, and can't be clicked before they appear.
- An album cover shows even when it can't be saved for next time, and a damaged saved cover is fetched again.
- Pressing Enter after changing the search no longer plays a song from the old results.
- The album cover is called the same everywhere in the settings and help pages.
- With Hat pulse off, the propeller cap turns at its calm speed instead of a faster one.
- With Follow, the sky sparkles and shooting stars move with the lyrics again.
- Show underground too also works in the Nether.
- Diving deep no longer counts as being underground.
- An album cover the game can't read no longer leaves the last song's colour.
- An album cover that stalls while downloading is cut off instead of blocking the next song's cover.
- The Record card in a top corner moves below the boss bars on a narrow screen.
- A bar at the top hides while the debug screen (F3) is showing.
- The bottom bar sits above the held item's name, and a bigger bar grows upward instead of off the screen.
- A bigger top bar now shrinks to fit the screen instead of overflowing it.
- "Set up Spotify" scrolls to the Spotify card even when the settings screen has just opened, and tabs keep their scroll after you visit a shorter one.
- When LRCLIB doesn't have the song, the mod no longer shows the lyrics of another song by the same artist.
- A very long song title from a player or a lyrics site can no longer hold up the lyrics lookup.
- A rare error in the lyrics drawing no longer crashes the game; the lyrics pause for a few seconds and the error goes to the log.
- Works alongside other mods that change how fonts are uploaded on 1.21.1 and 1.21.4.
- In the World layout the song title shows for its full time even when the lyrics take a moment to load, and a long title is shrunk to fit.
- In the World layout, respawning or changing world during the end of a line no longer flashes its last words, and a word that is far away fades out instead of vanishing.
- Hitting mobs while the World lyrics are hidden no longer knocks a word when they show again.
- With Spotify signed in, the mod asks Spotify much less often while nothing shows the song, and slows down when Spotify is having trouble.
- A Spotify app that isn't set up for your account now says what to do instead of "error 403".
- Lyrics with an hour in their time stamps, extra digits after the seconds, or wide brackets no longer lose those lines.
- A line that is only "(Instrumental)" or "[music]" now counts as a pause instead of being shown as words.
- A song whose lyrics merely mention "纯音乐" no longer loses them to the instrumental check.

### Faster

- **Lighter lyrics and Galaxy.** Lines and Arc lyrics and the Galaxy sky do less work each frame.
- Lines and Arc lyrics and the Galaxy sky do less work each frame.

### Changed

- **One name for the album cover.** The album picture is called "album cover" everywhere in the settings and help pages.
