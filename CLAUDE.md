# Claude Context — Jellyfin Android TV (Curator Fork)

## What this project is

A fork of the official Jellyfin Android TV app, modified to display Netflix-style curated collection rows on the home screen. The collections are driven by the **Jellyfin Curator** server-side plugin (`/home/chris/projects/jellyfin-curator`), which creates Jellyfin BoxSet collections from metadata rules and controls which ones are "featured" (visible on the home screen) per user.

**The core mechanism:** Jellyfin's `isFavorite = true` on a BoxSet's UserData = that collection appears as a home screen row. The Curator plugin sets/unsets this flag via rotation and blocks. The Android TV app reads it via the standard Jellyfin Items API.

## Infrastructure

| Thing | Value |
|-------|-------|
| Jellyfin server | `http://10.0.20.2:8096` (TrueNAS Scale) |
| Curator plugin dir | `/mnt/Media/Jellyfin/plugins/Curator/` on TrueNAS |
| Android TV (Sony) | `10.0.0.62:5555` (ADB over WiFi) |
| Branch | `master` (tracks `upstream/release-0.19.z` — **NOT** upstream/master) |
| App package ID | `tv.curator.app` |
| Signing | Debug keystore for sideloading |

## Build & Deploy

```bash
# Debug build
export JAVA_HOME=/opt/android-studio/jbr && ./gradlew assembleDebug

# Release build (signed with debug key for sideloading)
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease \
  -Pkeystore.file=/home/chris/.android/debug.keystore \
  -Pkeystore.password=android \
  -Psigning.key.alias=androiddebugkey \
  -Psigning.key.password=android

# Deploy via ADB
adb connect 10.0.0.62:5555
adb install -r app/build/outputs/apk/release/jellyfin-androidtv-v0.0.0-dev.1-release.apk
```

## What's already been changed

### Key files modified/added

| File | What it does |
|------|-------------|
| `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt` | Fetches featured BoxSets and passes them to `HomeFragmentCuratorRow` after all standard home sections |
| `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragmentCuratorRow.kt` | New file — loops featured collections, builds `GetItemsRequest` per collection, creates a `HomeFragmentBrowseRowDefRow` for each |

### How the collection rows work

1. `HomeRowsFragment.onCreate` calls the standard Jellyfin Items API to get all `isFavorite=true` BoxSets for the current user
2. These are passed to `HomeFragmentCuratorRow`, which adds one row per collection **below all standard home sections** (Recently Added, Resume, etc.)
3. Each row fetches its movies via `parentId = collection.id`, `includeItemTypes = Movie`, `limit = 100`

### Bugs already fixed

- `isFavorite` query was hitting `/Items` without `userId` — fixed by adding `userId = currentUser.id` to the request
- Movie rows were self-removing — `chunkSize = 0` was sending `limit=0` to Jellyfin returning 0 items; fixed to `chunkSize = 100`

## Curator Plugin — API Reference

All Curator endpoints are at `/Curator/...` and require standard Jellyfin Bearer token auth (same token the SDK already uses). The Android TV SDK client handles auth automatically.

### Currently used by Android TV (standard Jellyfin SDK, not Curator-specific)

```
GET /Items
  ?userId={userId}
  &isFavorite=true
  &includeItemTypes=BoxSet
  &recursive=true
→ Returns featured collections as BaseItemDto[]

GET /Items
  ?userId={userId}
  &parentId={collectionId}
  &includeItemTypes=Movie
  &limit=100
→ Returns movies in a collection as BaseItemDto[]
```

### Curator plugin endpoints (available, not yet used by Android TV)

#### Collections & Status

```
GET  /Curator/collections
     → CollectionReport[] — live state of all managed collections

GET  /Curator/status
     → { isRebuilding, rebuildProgress (0-100), lastRun, duration,
         totalMoviesScanned, collections }

POST /Curator/rebuild
     → 202 Accepted — starts full rebuild in background
     → 409 Conflict — if already rebuilding

POST /Curator/rotate
     → { featured: string[] } — picks 5 random collections, marks them
       isFavorite=true for all users, un-features the rest

POST /Curator/purge
     → Deletes all Jellyfin BoxSet collections (use before a fresh rebuild)
```

#### Per-user Blocks

Blocks filter which collections get *featured* at rotation time — not the content of collections. If a user blocks Horror, horror-only collections won't be featured for them. Applies to ALL collections including Picks of the Week.

```
GET    /Curator/users/{userId}/blocks
       → UserBlocks { genres: string[], movieIds: string[], folderPaths: string[] }

POST   /Curator/users/{userId}/blocks
       body: UserBlocks — replaces all blocks for the user

POST   /Curator/users/{userId}/blocks/genres/{genre}
DELETE /Curator/users/{userId}/blocks/genres/{genre}

POST   /Curator/users/{userId}/blocks/movies/{movieId}
DELETE /Curator/users/{userId}/blocks/movies/{movieId}

POST   /Curator/users/{userId}/blocks/folders
DELETE /Curator/users/{userId}/blocks/folders
       body: { path: string }

POST   /Curator/blocks/apply
       → Immediately un-features any featured collection that violates any user's blocks
```

#### Users & Collections state

```
GET /Curator/users
    → UserInfo[] { id, name }

GET /Curator/collections/{ruleId}/set-featured
POST /Curator/collections/{ruleId}/set-featured
     ?featured=true|false
     &userId={userId}   ← omit to apply to ALL users
     → 200 OK

GET /Curator/movie/{itemId}
    → { movie: MovieMatch, collections: CollectionReport[] }
    which collections a specific movie belongs to

GET /Curator/movies/search?q={query}
    → MovieMatch[] (limit 20)

GET /Curator/genres
    → string[] — all genres across the library
```

#### Rules CRUD (for reference — managed via web UI, not Android TV)

```
GET    /Curator/rules             → CuratorRules (full rules.json)
PUT    /Curator/rules/{ruleId}    body: CollectionRule
DELETE /Curator/rules/{ruleId}
PUT    /Curator/global-settings   body: { excludeKeywords: string[], excludePaths: string[] }
```

#### Admin UI

```
GET /Curator/ui   → HTML page (anonymous, no auth required)
    Accessible at http://10.0.20.2:8096/Curator/ui
```

### Data models

```typescript
CollectionReport {
  ruleId: string            // kebab-case rule ID from rules.json
  name: string              // display name
  movieCount: number
  featuredForUsers: string[] // usernames for whom this is isFavorite=true
  movies: MovieMatch[]
}

MovieMatch {
  id: UUID
  title: string
  year?: number
  rating?: number           // TMDb community rating
}

UserBlocks {
  genres: string[]          // e.g. ["Horror", "Documentary"]
  movieIds: string[]        // Jellyfin item IDs (no hyphens, "N" format)
  folderPaths: string[]     // e.g. ["/mnt/Media/Emby/Movies/Foreign/"]
}

UserInfo {
  id: UUID
  name: string
}
```

## How the Curator plugin works (server side)

- **rules.json** — 133 collection definitions stored at `/mnt/Media/Jellyfin/plugins/Curator/rules.json`. Each rule defines criteria (genres, TMDb keywords, path filters, rating thresholds, etc.). Edited via the web admin UI or directly.
- **Rebuild** — `POST /Curator/rebuild` fetches TMDb keyword/vote/language data for every movie, evaluates each rule against every movie, creates/updates Jellyfin BoxSets. Takes ~40s cold, much faster with warm cache.
- **Rotation** — `POST /Curator/rotate` picks 5 random collections and marks them `isFavorite=true` for all users. This is what drives the home screen rows.
- **LibraryEventConsumer** — server-side, fires on `ItemUpdated` when Jellyfin imports a new movie (after metadata fetch). Automatically adds the new movie to any matching collections. 30-minute debounce to avoid running on every metadata refresh.
- **Blocks** — stored as `user-blocks-{userId}.json` in the plugin dir. Applied at rotation time: if a collection would be featured but violates a user's blocks, it's skipped for that user.

## What needs to be built next (Android TV side)

### 1. Collection Preferences UI (deferred — next Android TV work)

Per-user weighted rotation — users say "I want more Horror, less Romance" and the rotation respects it. Needs:

**New server-side API (NOT YET BUILT in the plugin):**
```
GET  /Curator/users/{userId}/preferences
     → { preferredRuleIds: string[] }   // e.g. ["horror", "sci-fi-subgenres"]

POST /Curator/users/{userId}/preferences
     body: { preferredRuleIds: string[] }
```

**Server storage:** `user-prefs-{userId}.json` in plugin data dir (to be implemented in Curator plugin).

**Rotation behaviour (to be implemented in plugin):**
- 1 guaranteed slot from preferred categories per rotation
- Remaining slots weighted toward preferences
- Currently 5 rotating slots + Picks of the Week always on

**Android TV UI:**
- Settings screen where the signed-in user picks preferred categories from the list of rule IDs/names
- Calls `GET /Curator/users/{userId}/preferences` to load, `POST` to save
- No special display changes needed on home screen itself — rotation handles it

### 2. Performance investigation (outstanding)

App is noticeably slower than Emby on startup. Likely cause: 5+ concurrent collection row fetches all launching at once. Next step: install the *official* Jellyfin Android TV app on the same TV as a baseline. If official is also slow → base app issue not our changes. If official is fast → our `HomeFragmentCuratorRow` parallel fetches are the culprit (consider sequential loading or lazy init).

### 3. Subtitle download (deferred — large feature)

Emby has OpenSubtitles integration built in; Jellyfin Android TV does not. Significant work — deferred indefinitely.

## Upstream sync process

Two remotes are configured:
- `origin` → our fork (`https://github.com/d3fragg3d/jellyfin-androidtv-curator.git`)
- `upstream` → official Jellyfin repo (`https://github.com/jellyfin/jellyfin-androidtv.git`)

**IMPORTANT: We track `upstream/release-0.19.z`, NOT `upstream/master`.**

The Play Store ships from the release branch. The master branch is pre-release development code that is significantly less performant and has not been through QA. We discovered this after comparing our fork (master-based) against the Play Store build — the release branch is dramatically smoother.

To sync upstream changes (new patch releases on the 0.19.z branch):
```bash
git fetch upstream
git log --oneline master..upstream/release-0.19.z   # preview what's coming
git merge upstream/release-0.19.z
git push origin master
```

**Never merge from `upstream/master`** — it will reintroduce the performance regressions and potentially break our cherry-picked changes.

When a new major release branch appears (e.g. `upstream/release-0.20.z`), evaluate it first by building and testing before rebasing onto it.

Conflicts will only occur in files listed in the "Files we own" table below. Resolve by keeping both sides — upstream changes are almost always in different parts of the same file from ours.

## Files we own — conflict risk map

These are the files that differ from upstream. New files we add are never a conflict risk; only modified upstream files are.

| File | Risk | What we changed |
|------|------|-----------------|
| `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt` | Low | Added curator BoxSet fetch + one row append at the bottom of the main coroutine block |
| `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragmentCuratorRow.kt` | None | New file — no upstream equivalent |
| `app/src/main/java/org/jellyfin/androidtv/ui/browsing/CuratorMovieGenrePickerFragment.kt` | None | New file — genre picker grid shown when Movies library is opened |
| `app/src/main/java/org/jellyfin/androidtv/constant/Extras.kt` | Low | Added `GenreName` constant |
| `app/src/main/java/org/jellyfin/androidtv/ui/navigation/Destinations.kt` | Low | Added `movieGenrePicker()` and `libraryBrowserByGenre()` destinations |
| `app/src/main/java/org/jellyfin/androidtv/ui/browsing/BrowsingUtils.kt` | Low | Added optional `genre` param to `createBrowseGridItemsRequest()` |
| `app/src/main/java/org/jellyfin/androidtv/ui/browsing/BrowseGridFragment.java` | Low | Reads optional `GenreName` arg in `setupQueries()` and passes it to `BrowsingUtils` |
| `app/src/main/java/org/jellyfin/androidtv/ui/itemhandling/ItemLauncher.java` | Low | `MOVIES` collection type now routes to `movieGenrePicker` instead of grid/smart screen |
| `app/src/main/java/org/jellyfin/androidtv/ui/itemhandling/ItemRowAdapter.java` | Low | `loadMoreItemsIfNeeded()` skips pagination during scroll |
| `app/src/main/res/values/strings.xml` | Low | Added `lbl_all_movies`; replaced user-visible "Jellyfin" brand strings with "Curator" |
| `app/build.gradle.kts` | Low | `applicationId = "tv.curator.app"`; release resValues use curator package |
| `app/src/main/res/values/theme_jellyfin.xml` | Low | Added `Theme.Jellyfin.Splash` for black window background on startup |
| `app/src/main/AndroidManifest.xml` | Low | `StartupActivity` uses `Theme.Jellyfin.Splash` |
| `app/src/main/res/drawable/app_logo.png` | None | Replaced Jellyfin vector with Curator PNG (splash screen logo) |
| `app/src/main/res/mipmap-*/app_icon.png` | None | Replaced with Curator icon (logo only, no text) |
| `app/src/main/res/mipmap-*/app_banner.png` | None | Replaced with Curator banner (full logo + text, 16:9) |
| `app/src/main/res/drawable/app_icon_background.xml` | Low | Background colour set to pure black |
| `CLAUDE.md` | None | New file |
| `FORK.md` | None | New file |

**Principle for new features:** prefer adding new files over modifying existing upstream files. When a modification to an existing file is unavoidable, keep it in one clearly-delimited block (ideally at the end of a function) so it's obvious during conflict resolution.

As we add the nested browser UI, settings screens, and other changes, add every modified upstream file to this table immediately so the conflict surface stays documented.

## Git conventions

- Do **not** add `Co-Authored-By: Claude` lines to commit messages. Write commits as if the user authored them directly.

## Known constraints

- The Jellyfin Android TV SDK (`org.jellyfin.sdk`) handles auth token injection automatically — all API calls go through `ApiClient` which is injected via Koin. No manual auth header management needed.
- Collections that aren't `isFavorite=true` for a user don't appear on their home screen — this is the whole mechanism. The Curator plugin manages the `isFavorite` state server-side.
- `isFavorite` queries **must** include `userId` — without it the request isn't user-scoped and returns global state.
- The Curator web admin UI is at `http://10.0.20.2:8096/Curator/ui` — useful for triggering rebuilds and managing rules/blocks without touching the Android TV app.
