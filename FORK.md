# Jellyfin AndroidTV — Curator Fork

This is a personal fork of [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv), modified to work with the [Jellyfin Curator](https://github.com/d3fragg3d/jellyfin-curator) server plugin. It adds Netflix-style curated collection rows to the home screen, a curated genre/collection browser, and subtitle downloading via OpenSubtitles.

---

## Branch structure

| Branch | Purpose |
|--------|---------|
| `master` (our fork) | Our single working branch — all Curator changes live here |
| `upstream/release-0.19.z` | The Jellyfin Play Store release branch — the only upstream source we ever merge from |

**We never merge from `upstream/master`.**

- `upstream/release-0.19.z` is the Play Store build — stable, performant, QA'd.
- `upstream/master` is pre-release development code. It is significantly less performant and has not been through QA. We learned this the hard way — master-based builds are noticeably slower than the Play Store release.

Our `master` branch is seeded from `upstream/release-0.19.z` and contains our Curator additions on top. Syncing upstream means merging the latest `upstream/release-0.19.z` patches into our `master`.

---

## What's different from upstream

### New files (zero conflict risk — upstream doesn't have these)

| File | What it does |
|------|-------------|
| `app/.../ui/browsing/CuratorMovieGenrePickerFragment.kt` | Genre/collection picker shown when tapping Movies or TV Shows. Calls `GET /Curator/genre-collections`, shows a 6-column grid of curated collections with backdrop images. |
| `app/.../ui/home/HomeFragmentCuratorRow.kt` | Renders one home screen row per featured Curator collection (`isFavorite=true` BoxSets). |
| `app/.../ui/presentation/GenreCardPresenter.kt` | Compose-based 260×146dp card presenter for the genre picker grid. Includes focus border via `MutableState<Boolean>`. |
| `app/.../ui/itemdetail/SubtitleHelper.kt` | Subtitle download for the detail screen. Shows existing subtitle tracks, language picker, OpenSubtitles search results (hash matches first), and downloads via Jellyfin server. |
| `app/.../ui/playback/PlaybackSubtitleHelper.kt` | Same subtitle download flow wired into the player CC button. After download, stops and restarts playback at current position. |
| `app/src/main/res/drawable/selector_card_focus.xml` | Focus border drawable (created but superseded by the `onFocusChanged` approach in code). |
| `app/src/main/res/drawable/curator_symbol.png` | Transparent-bg symbol PNG for the Toolbar logo. |
| `app/src/main/res/drawable/curator_symbol_sq.png` | Square transparent-bg symbol PNG for the adaptive icon foreground. |
| `app/src/main/res/mipmap-*/app_icon.png` | Curator icon (logo only, no text). |
| `app/src/main/res/mipmap-*/app_banner.png` | Curator banner (full logo + text, 16:9). |
| `app/src/main/res/mipmap-anydpi-v26/app_icon.xml` | Adaptive icon XML — background=black, foreground=curator_symbol_sq.png. |
| `app/src/main/res/drawable/app_logo.png` | Replaced Jellyfin vector with Curator PNG (splash screen logo). |
| `CLAUDE.md` | AI assistant context — not relevant to the app. |
| `FORK.md` | This file. |

### Modified upstream files (conflict risk on merge)

| File | Risk | What we changed |
|------|------|-----------------|
| `app/.../ui/home/HomeRowsFragment.kt` | **Medium** | Fetches `isFavorite=true` BoxSets and appends one curator row per collection after all standard home sections. Also added scroll-aware image loading via `RecyclerView.OnScrollListener` + `ScrollStateManager`. |
| `app/.../ui/itemhandling/ItemLauncher.java` | **Medium** | `MOVIES` case → `movieGenrePicker(baseItem)`, `TVSHOWS` case → `tvShowGenrePicker(baseItem)`. Removed `LibraryPreferences` dependency from those two cases. |
| `app/.../ui/itemdetail/FullDetailsFragment.java` | Low | Added subtitle button for `MOVIE` and `EPISODE` types, wired to `SubtitleHelperKt.showSubtitlePicker()`. |
| `app/.../ui/card/LegacyImageCardView.java` | Low | Added `onFocusChanged` override to draw a 3dp `#00A4DC` border on `binding.mainImage` when focused. Also added `GradientDrawable` and `ContextCompat` imports. |
| `app/.../ui/browsing/BrowseGridFragment.java` | Low | Reads optional `Extras.GenreName` arg in `setupQueries()` and passes it to `BrowsingUtils`. |
| `app/.../ui/browsing/BrowsingUtils.kt` | Low | Added optional `genre: String?` param to `createBrowseGridItemsRequest()`. |
| `app/.../ui/navigation/Destinations.kt` | Low | Added `movieGenrePicker()`, `tvShowGenrePicker()`, and `libraryBrowserByGenre()` destinations. |
| `app/.../ui/playback/overlay/action/ClosedCaptionsAction.kt` | Low | Added "Download Subtitles" menu item at the bottom of the CC popup. Wired to `PlaybackSubtitleHelperKt.showSubtitleDownloader()`. |
| `app/.../constant/Extras.kt` | Low | Added `GenreName` constant. |
| `app/.../preference/UserPreferences.kt` | Low | `backdropEnabled` default changed to `false`. |
| `app/.../data/repository/UserViewsRepository.kt` | Low | Added `CollectionType.BOXSETS` to `unsupportedCollectionTypes` — hides the Collections tile from the home screen. |
| `app/.../ui/startup/StartupActivity.kt` | Low | Added 5s minimum splash duration, timer starts from session-found. |
| `app/.../ui/startup/fragment/SplashFragment.kt` | Low | Background changed to `Color.Black`. |
| `app/.../ui/shared/toolbar/Toolbar.kt` | Low | Logo uses `curator_symbol.png` at 64dp height instead of the full Jellyfin logo. |
| `app/src/main/AndroidManifest.xml` | Low | `StartupActivity` uses `Theme.Jellyfin.Splash`. |
| `app/src/main/res/layout/activity_main.xml` | Low | Toolbar logo tweaks. |
| `app/src/main/res/values/strings.xml` | Low | Added curator branding strings and subtitle download strings. |
| `app/src/main/res/values/theme_jellyfin.xml` | Low | Added `Theme.Jellyfin.Splash` for black window background on startup. |
| `app/src/main/res/drawable/app_icon_foreground.xml` | Low | Updated to reference `curator_symbol_sq.png`. |
| `app/src/main/res/drawable/app_icon_background.xml` | Low | Background colour set to pure black. |
| `app/build.gradle.kts` | Low | `applicationId = "tv.curator.app"`; release resValues use curator package. |

---

## Keeping in sync with upstream Jellyfin

### Step 0 — check if Jellyfin has cut a new major release branch

There is no automatic tracking. Run this first to see all available release branches:

```bash
git fetch upstream && git branch -r | grep "upstream/release"
```

If a new branch appears (e.g. `upstream/release-0.20.z`), **do not switch to it blindly**. Build it, run it on the TV, and compare performance before rebasing onto it. Update the branch name in the commands below once you've validated it.

### Regular sync (patch releases on the current branch)

```bash
# 1. Fetch latest from official repo
git fetch upstream

# 2. Check what they've added since your last sync (RELEASE BRANCH ONLY)
git log --oneline master..upstream/release-0.19.z

# 3. Preview which files differ
git diff master upstream/release-0.19.z --name-only

# 4. Merge their release branch changes
git merge upstream/release-0.19.z

# 5. Resolve conflicts if any (see below)

# 6. ALWAYS build after a merge — catches any API/signature breakage early
export JAVA_HOME=/opt/android-studio/jbr && ./gradlew assembleDebug

# 7. Push
git push origin master
```

**Do NOT run `git merge upstream/master`** — that branch is pre-release development code and will reintroduce performance regressions.

When a new major release branch appears (e.g. `upstream/release-0.20.z`), build and test it before switching. Don't assume new major versions are better — we learned this lesson.

### Resolving conflicts

Conflicts will only occur in the modified upstream files listed above. The two medium-risk ones:

**`HomeRowsFragment.kt`** — our curator additions are a self-contained block at the end of the main coroutine launched in `onCreate`. Upstream changes are almost always elsewhere (imports, other row logic). Keep both. If upstream removes an import we use, add it back.

**`ItemLauncher.java`** — our change replaces the `MOVIES`/`TVSHOWS` cases with genre picker destinations. If upstream restructures the switch/when, re-apply our routing on top of their new structure.

For all other files, our changes are isolated single blocks that are clearly distinct from upstream's typical changes.

---

## Remotes

| Remote | URL | Purpose |
|--------|-----|---------|
| `origin` | `https://github.com/d3fragg3d/jellyfin-androidtv-curator.git` | Our fork |
| `upstream` | `https://github.com/jellyfin/jellyfin-androidtv.git` | Official Jellyfin repo |

---

## Guiding principle for new features

**Prefer new files over modifying existing upstream files.**

- New Curator-specific UI → new Fragment/Kotlin file, not edits to existing ones
- If you must hook into an existing upstream file, keep the change minimal and in one clearly-marked block
- Add every modified upstream file to the table above immediately so the conflict surface stays documented
