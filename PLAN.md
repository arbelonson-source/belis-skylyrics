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
- [ ] Custom skies (aurora, nebula, …) that work with/without shaders
- [x] Beat-ish pulse (glow on each new line)
- [ ] Translations (en_us + he_il)

### Release
- [ ] Screenshots + videos in docs/ (shaders on/off, GUI)
- [ ] README with install steps for normal people
- [ ] GitHub repo arbelonson-source/belis-skylyrics, release jar, CI build
- [ ] Install into the owner's "donut" instance

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
