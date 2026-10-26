# Beli's SkyLyrics — work plan

Free, open-source (AGPL-3.0) Fabric mod for Minecraft 1.21.11: synced lyrics of whatever song
is playing on your PC, glowing in the sky. Linux first. Must work with Sodium, Iris and shaders.

Owner's request: free recreation of the paid "Neon Lyrics" mod, tasteful config GUI,
photos + videos, keep improving until it's perfect, then upload to GitHub (FOSS).

## Rules for whoever picks this up (incl. the cron loop)
- Build: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew build` (Loom needs Java 25 to run; the mod targets Java 21).
- Test in game WITHOUT stealing the owner's screen: run the game on a Hyprland headless
  output (`hyprctl output create headless SKYLYRICS`, window rule `workspace name:skylyrics silent`).
  Never test while the owner is playing (`pgrep -fa 'java.*minecraft'` shows their game).
- Demo screenshots/videos use the built-in DEMO song with original lyrics, never real lyrics.
- Never hard-code real lyrics. Lyrics come from LRCLIB at runtime.
- Keep the owner's main Prism instance ("donut") untouched until a release is ready.
- Update the checklist below after each work session, then commit and push to GitHub
  (github.com/arbelonson-source/belis-skylyrics) so the owner can see progress.

## Checklist

### v0.1 — core
- [x] Project skeleton (Mojang mappings, Fabric API, client-only, Mod Menu hook)
- [x] Now-playing detection on Linux via MPRIS (D-Bus): title, artist, album, length, position, playing/paused, art URL
- [x] Smooth position clock (interpolate between polls, handle seek/pause/song change)
- [x] LRCLIB client: /api/get then /api/search fallback, disk cache, plain-lyrics fallback, "no lyrics" state
- [x] LRC parser (multi-timestamp lines, offsets, empty lines = breaks) + unit tests
- [x] Sky renderer: big glowing text far away in the sky, billboard, fade/rise animation, karaoke sweep
- [x] Modes: Sky (fixed direction in the world) / Floating (in front of you, follows view)
- [x] Works with Iris shaders on + off, Sodium
- [x] Top bar (top of screen): round album art, the line being sung with the current word highlighted, equaliser; title · artist on song change
- [x] Config screen: sidebar tabs, cards, toggles, segmented, sliders, colour swatches, live preview, player controls
- [x] Keybind (J) to open config, Mod Menu button
- [x] Demo mode (built-in song with original lyrics) for screenshots/videos and for trying it without music
- [x] Config saved to config/skylyrics.json

### v0.2 — polish
- [x] Sync offset per song (+/- buttons), remembered
- [x] Album-art colour theme (lyrics colour matches the cover)
- [x] Custom skies (aurora, nebula, …) that work with/without shaders
- [x] Beat-ish pulse (glow on each new line)
- [x] Translations (en_us + he_il)

### Release
- [x] Screenshots + videos in docs/ (shaders on/off, GUI)
- [x] README with install steps for normal people
- [x] GitHub repo arbelonson-source/belis-skylyrics, release jar, CI build
- [x] Install into the owner's "donut" instance

### After 0.1.0 (small things)
- [x] Lyrics search for songs with several artists ("A, B"): try the first artist alone when the full name finds nothing
- [x] Daytime lyrics without shaders look a little pale; give them a bit more contrast against a bright sky

### Windows (owner's top priority, 2026-10-25)
- [x] Song detection on Windows 10/11 through the media controls (System Media Transport Controls), via a hidden bundled PowerShell helper (`src/client/resources/skylyrics/windows-media.ps1`), no native code
- [x] Play/pause, next, previous and seek on Windows
- [x] Album covers from Windows (saved to `.cache/skylyrics/windows/covers`, read like any `file://` cover)
- [x] Player choice in the Music tab lists Windows players on Windows; About text says Windows
- [x] Tests: the helper script runs against a made-up media manager (needs PowerShell; `pwsh` on Linux, built in on Windows)
- [x] CI builds and tests on windows-2025 too (talks to the real Windows media API there)
- [x] README, FAQ, Troubleshooting, store pages and v0.2.0 notes mention Windows
- [ ] First run on a real Windows PC: Spotify, Chrome/Edge, covers, controls, no console window flashing, Hebrew user names
- [ ] Settings screen on Windows: "Media Player" fits in the Player button

### macOS
- [x] Spotify and Apple Music through AppleScript (osascript), only asked while they're open; Automation permission explained in the Music tab
- [x] CI runs the real AppleScript on macos-15
- [ ] First run on a real Mac: the permission prompt, Spotify and Music, controls

### Polish ideas (the loop keeps going)
- [x] Optional handwritten "Script" font for the sky lyrics, like the brush lettering in the Neon Lyrics videos (OFL font, bundled)
- [x] Top bar: "now playing" look between lines (song title · artist) instead of an empty bar
- [x] Top bar: optional clock on the left of the bar
- [x] Current word slightly larger while it is being sung (optional)
- [ ] Settings screen: live preview of the sky lyrics inside the menu looks right at every GUI scale

## Log
- 2026-10-25: started. Template from FabricMC/fabric-example-mod@1.21.11.
- 2026-10-25: core + GUI built, 14 unit tests green. Checklist caught up.
- 2026-10-25: first in-game check on the headless screen: demo lyrics glow in the night sky (Sky mode, no shaders). Added `shot <name>` to DevTestHook so screenshots skip desktop pop-ups. Note: hideWithF1 hides lyrics with F1, so screenshots keep the HUD on.
- 2026-10-25: owner asked to see progress on GitHub now and to use AGPL-3.0. Uploaded as a public work-in-progress repo; push after every session from now on.
- 2026-10-25 17:51: smooth lyrics font (Poppins SemiBold + Noto Sans Hebrew, both OFL) with a Font switch in the Style card; checked in the sky with Iris on. Still to fix: clouds draw over the lyrics and shaders make the text dark.
- 2026-10-25 18:04: shaders no longer hide the lyrics. New "Draw on top" setting (Auto/Always/Never): with a shader pack on, Auto draws the lyrics as a screen layer pinned to the same spot in the sky, so they stay bright and clouds cannot cover them. Also fixed the Animation buttons, whose labels were swapped.
- 2026-10-25 18:12: checked with shaders off too. Found two more things dimming the lyrics: rain fog (new in 1.21.6) and clouds painting over see-through text. Auto now draws on screen unless blocks should hide the lyrics; it also steps back to the world while F1 hides the HUD, so F1 screenshots keep their lyrics.
- 2026-10-25 18:32: new top bar in the style of the Neon Lyrics videos: a dark rounded pill with the round cover, the line being sung (sung words white, the current word on a highlight that glides along, upcoming words grey) and a small equaliser that rests when paused. It shows the title · artist when the song changes, and is the only thing shown if "Lyrics in the bar" is off. Checked both modes on the headless screen with Iris on.
- 2026-10-25 18:41: new default animation "Words": the old line fades out while the new line's words fade and slide in one after another, like the Neon Lyrics videos. Right-to-left lines (Hebrew) are now put in display order before drawing, so they no longer come out backwards in the sky (unit test DisplayOrderTest). Added a `scroll` command to the dev test hook.
- 2026-10-25 18:47: timing can now be fixed per song. A "This song" slider under Music, plus two keys (unbound by default, set them in Controls) that nudge the playing song by 0.1 s and say so above the hotbar. Each song keeps its own timing on top of the general one (last 500 songs remembered). Unit test SongOffsetTest.
- 2026-10-25 18:53: new "Pulse" switch (on by default, Lyrics > Lines): the glow swells and the letters brighten for a moment when a line starts; with the Words animation each word flares as it lands. Also fixed a one-frame flash of the whole new line before its words faded in. Unit test PulseTest.
- 2026-10-25 18:56: README rewritten for normal players: what it does now (top bar, Words animation, pulse, per-song timing, shader-safe lyrics), install steps for Prism Launcher and the official launcher, where the keys are, and what to check when no lyrics show.
- 2026-10-25 19:07: Hebrew translation of the whole settings screen, the top bar and the status messages (148 texts, he_il). English stays the fallback for anything missing (unit test TrTest checks both files match). Checked every tab in Hebrew on the headless screen; all rows fit. Also fixed the About page, which wrongly said MIT licence; it is AGPL.
- 2026-10-25 19:30: night sky effects. While music plays at night, a green aurora (soft curtains with rays) or a purple-blue nebula glows behind the lyrics, tinted by the song colour. Off / Aurora / Nebula plus a strength slider in Look > Sky. Hides in rain, daytime, underwater and the Nether/End, fades in and out. Checked with shaders on and off on the headless screen.
- 2026-10-25 19:41: pictures and a short clip for the project page (docs/): sunset and aurora with shaders, nebula, daytime and floating without, the four settings tabs, and a word-by-word clip (animated preview in the README, mp4 next to it). Recorded only the middle of the hidden test screen so desktop pop-ups stay out. Also shortened four settings help lines that were cut off (English and Hebrew).
- 2026-10-25 19:52: tested the real jar the way players install it (new `runProductionClient` task: built jar + published Fabric API, Sodium 0.8.4, Iris 1.10.5, Mod Menu, Complementary shaders). Lyrics, top bar, aurora and the settings screen all work; Mod Menu lists it with the right licence and opens the settings. Replaced the template "Mod ID" placeholder icon with a real one (night sky, glowing note, lyric lines; source docs/icon.svg).
- 2026-10-25 20:00: first release, v0.1.0, on GitHub with the jar (built by GitHub from the tag). Before installing, tried the released jar next to all 48 mods of the "donut" instance (ImmediatelyFast, skyboxify, polytone, C2ME, Bobby, etc.) with Complementary and with BSL: loads cleanly, lyrics, top bar, aurora and settings all fine. Then copied the release jar into donut's mods folder. `runProductionClient -PmodsDir=...` does this test with any mods folder.
- 2026-10-25 20:40: songs with several artists already work: checked live on LRCLIB (Queen & David Bowie, Shawn Mendes & Camila Cabello, The Chainsmokers & Coldplay, Earth, Wind & Fire), and the search already falls back to the first artist alone. Daytime lyrics: under a bright sky the light glow turns into a dark halo and the colours get deeper, so the words stand out; at night nothing changes. Fades with sunset and counts rain as half.
- 2026-10-25 20:25: new font choice "Handwritten" (Kaushan Script, OFL) for the sky lyrics, like the brush lettering in the Neon Lyrics videos. The top bar keeps the smooth font. Font row is now Smooth / Handwritten / Minecraft (English and Hebrew).
- 2026-10-25 20:45: player help pages (docs/FAQ.md, docs/TROUBLESHOOTING.md), ready-to-paste Modrinth and CurseForge pages (store/), draft release notes for v0.2.0, README links them and lists the Handwritten font. Nothing published to the stores.
- 2026-10-25 20:59: when LRCLIB has a hiccup (busy, too many requests, timeout, no network) the mod now keeps trying while the song plays (after 2 s, 5 s, 10 s, 20 s, then every 30-60 s) instead of staying lyric-less until the next song; a hiccup is never saved as "no lyrics". Also: skipped songs aren't looked up one by one, LRC files with a BOM, indents or old Mac line ends parse fully, "Song - Remastered 2011" browser titles keep the artist, and length/cover that browsers send late now reach the lyrics and cover art.
- 2026-10-25 (cloud session): Windows support. Windows has no MPRIS; the closest thing is the media controls behind the media-key panel (GlobalSystemMediaTransportControlsSessionManager, a WinRT API). Java can't call WinRT by itself, so the mod starts Windows PowerShell 5.1 (part of every Windows 10/11) with a bundled script that stays running hidden and answers numbered one-line commands with one-line JSON. Positions are moved on from the player's last report, since players only update the timeline on play, pause and seek. Choosing between several players is now shared with the Linux source (PlayerPicker). If PowerShell can't start, the Music tab shows why and the mod retries every 15 s. Checked here: the media classes compile and their 17 tests pass, including the real script against a fake media manager on PowerShell 7. Not checked: the full Gradle build (this cloud session can't reach maven.fabricmc.net or Mojang's servers) and a real Windows PC.
- 2026-10-25 (cloud session): v0.2.0 prep. The top bar stays up through instrumental gaps once the singing has started, showing title · artist. New Clock switch (Display > Top bar, off at first): the time in the computer's format on the left of the bar, with a thin divider before the cover. New "Bigger current word" switch (Lyrics > Lines, needs karaoke, off at first): the word being sung grows by 14% over about a letter and a half and settles after; the row is laid out again so neighbours move aside. Version 0.2.0, release notes written (Windows, fonts, clock, bigger word, LRCLIB retries, fixes). Compiled and tested by CI only; still needs an in-game look (headless screen) before release.
- 2026-10-25 (cloud session, agents): two reviews and macOS. A review of the v0.2.0 rendering found the top bar could stay up for good once singing started, and the last word of each line never shrank back; both fixed, plus font-reload caching and the clock's easing. A review of the Windows helper found controls could hit the wrong browser tab (every tab has the same id), a 1.5 s limit on waking Windows' media service, polls that could miss their deadline with slow players, no restart when the media controls stop answering, Group Policy blocking -File, stale covers, and the cause of the flaky Windows CI (PowerShell still holding its log when the test's temp folder was deleted); all fixed. macOS: MacMediaSource asks Spotify and Music with AppleScript. CI now builds on Linux, Windows and macOS, shows failed tests as annotations, and has a by-hand job that photographs the game on a virtual screen.
- 2026-10-26 02:10: World layout reworked to feel like the Neon Lyrics videos (our own code, modelled on how its words behave): each word or two lands 6.5–15 blocks away on a 12-spot golden-angle scatter a little above the crosshair, picking the spot that overlaps the other cards least and never the last two spots; cards pop in (22% bigger, settling over 0.24 s), type in, drift up to a block, turn 10–30° sideways with a small nod and lean, turn round when seen from behind, and once the next words come they grey a little and fade over 2.2 s (at most 7 at once). Glow and Size settings now apply to it. Glow everywhere now draws 10 copies of each word instead of 16 at the same brightness (smoother frame rate). v0.3.0 notes list themes, top bar options, where-to-show, night sky options and the new World layout. Merged to main; the sky/themes/topbar worktrees are gone (branches kept). Still to do before publishing v0.3.0: an in-game look at the World layout and fresh README pictures, both on the headless screen while the owner isn't playing.
