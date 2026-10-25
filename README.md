# Beli's SkyLyrics

Synced lyrics for the song you're listening to, glowing in the Minecraft sky.
A free, open-source Fabric mod for Minecraft 1.21.11.

![Demo lyrics in the night sky](docs/sky-night.png)

> **Work in progress.** It builds and runs, but it isn't released yet.
> Progress is tracked in [PLAN.md](PLAN.md).

## What it does

- Sees what's playing on your PC (Spotify, a browser, any player that supports MPRIS on Linux).
- Fetches the synced lyrics from [LRCLIB](https://lrclib.net) and shows them line by line, in time with the song.
- **Sky mode** puts the lyrics far away in the sky. **Floating mode** keeps them in front of you.
- Takes its colour from the album cover (or pick your own, or rainbow).
- A small now-playing card with the cover, title, artist and progress.
- A settings screen (press **J**, or use Mod Menu) with a live preview and play/pause/next buttons.
- Demo mode with a built-in song, so you can try it without any music playing.
- Being tested to work with Sodium and Iris shaders.

## Install

Releases aren't out yet. When they are:

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the SkyLyrics `.jar` in your `mods` folder.
3. Start the game, play a song, and look up.

## Building it yourself

Needs Java 25 to run Gradle (the mod itself targets Java 21):

```
JAVA_HOME=/path/to/java-25 ./gradlew build
```

The jar ends up in `build/libs/`.

## License

[GNU AGPL v3](LICENSE). You can use, change and share it, as long as you share your changes under the same license. Lyrics are not included in this project. They're fetched from LRCLIB while you play.
