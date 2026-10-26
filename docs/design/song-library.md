# Song library — design

Search Spotify, browse your Liked Songs and playlists, and start a song without leaving
Minecraft. The library has three looks you can switch between: **Neon grid**,
**Cover carousel** and **Record shelf**.

## Where it lives

- New screen `gui/LibraryScreen.java`, opened by a new key (**J** by default, vanilla
  leaves it free) and by a "Song library" button on the Music tab.
- Header: search box, three sections (Search · Liked · Playlists), the look switcher
  (three small icons), close.
- The chosen look is remembered in the config (`libraryStyle`: GRID / CAROUSEL / SHELF,
  default GRID).

## Data — `media/SpotifyLibrary.java`

Uses the existing Spotify Web sign-in (`SpotifyWebSource`, PKCE, no client secret).
The token getter becomes package-visible so the library shares one login.

- Scopes gain `user-library-read playlist-read-private playlist-read-collaborative`.
  People signed in before this change see "Sign in again to use the library" once (we
  compare the granted `scope` in the token reply).
- Search: `GET /search?type=track&q=…&limit=10&offset=…`. Spotify returns at most 10 per
  page now, so scrolling to the end loads the next page (up to 50 results).
  Typing waits 350 ms after the last key before asking.
- Liked: `GET /me/tracks?limit=50&offset=…`, more pages as you scroll.
- Playlists: `GET /me/playlists?limit=50`, then `GET /playlists/{id}/items`. Spotify only
  opens playlists you own or share; others show a small "Spotify doesn't let apps open this
  playlist" note.
- All calls run off the game thread; results come back as plain `LibraryItem` records
  (id, uri, title, artists, album, cover URL, duration, playlist context).
- 429 waits for `Retry-After`; other failures show one quiet line in the screen.

## Playing a song

1. Signed in to Spotify Web: `PUT /me/player/play` with the track `uri`, or with the
   playlist `context_uri` + `offset` so Next/Previous stay in that playlist. If Spotify says
   there's no active device, pick the first from `GET /me/player/devices` and retry.
2. Linux fallback, or when no device is found: the desktop app over MPRIS
   `OpenUri("spotify:track:…")`. That needs no Web API play rights.
3. Clear messages: "Spotify Premium is needed to start songs" (403), "Open Spotify on your
   computer or phone first" (no device).

The lyrics then follow as today (the tracker already picks up the new song).

## Covers — `render/CoverCache.java`

The current `AlbumArt` holds one texture; the library needs dozens.

- Least-recently-used cache of up to 96 covers as `DynamicTexture`s, using Spotify's
  300 px image scaled to 128 px.
- Downloads on a small worker pool (4); files are cached in `.cache/skylyrics/covers/`.
- Uploads happen on the render thread, at most 4 per frame, so scrolling never stutters.
- Each cover's glow colour is the existing `AlbumArt.pickColor`, shared rather than copied.
- A cover that hasn't loaded shows a soft gradient in a colour made from the title.

## The three looks

All motion is time-based, so it's smooth at any frame rate. All three share the same items,
the keyboard (arrows move, Enter plays, Tab changes section, Esc closes) and the
"play" finale.

**Neon grid**
- Covers pop in one after another (30 ms apart, slight overshoot) with a glow in each
  cover's own colour.
- Hover lifts the cover and brightens the glow.
- Clicking flies the cover to the centre and grows it while the rest fades into the sky,
  then the song starts.

**Cover carousel**
- The chosen cover is big in the middle; neighbours shrink, dim and lean away.
- Since the menu is flat 2D, the lean is made by drawing each side cover as thin vertical
  strips whose height shrinks toward the far edge.
- Slide with the scroll wheel, arrows or by dragging; it eases and settles on a cover.

**Record shelf**
- Sleeves stand on a shelf, a little overlapped.
- Hover pulls the record halfway up out of its sleeve.
- Clicking slides the record onto a turntable at the side, where it spins (2D rotation),
  and the song starts.

## Without Spotify sign-in

The library shows one friendly card: "Sign in to Spotify on the Music tab", with a button
that goes there. If no Client ID is set, it links to the existing setup steps.

## Testing

- Unit tests: parsing search / liked / playlist replies (invented track names only), page
  loading, the play request body, the cover cache's eviction, the carousel strip maths, the
  easing curves.
- A `DemoLibrary` with made-up songs and generated gradient covers. In-game checks and README
  pictures can then use the headless output without a Spotify account.
- Never real lyrics in tests or pictures, only the demo song.

## Not in this feature

- Better-timed lyrics (word-by-word sources plus a per-song timing nudge). This gets its
  own short design next.
- Queues, saving to Liked, or editing playlists.
- Apple Music / other players' libraries.
