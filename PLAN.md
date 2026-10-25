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
- [ ] Works with Iris shaders on + off, Sodium
- [x] Now-playing pill (top of screen): album art, title · artist, progress
- [x] Config screen: sidebar tabs, cards, toggles, segmented, sliders, colour swatches, live preview, player controls
- [x] Keybind (J) to open config, Mod Menu button
- [x] Demo mode (built-in song with original lyrics) for screenshots/videos and for trying it without music
- [x] Config saved to config/skylyrics.json

### v0.2 — polish
- [ ] Sync offset per song (+/- buttons), remembered — global offset slider exists, per-song memory still missing
- [x] Album-art colour theme (lyrics colour matches the cover)
- [ ] Custom skies (aurora, nebula, …) that work with/without shaders
- [ ] Beat-ish pulse (glow on each new line)
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
