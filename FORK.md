# Jellyfin AndroidTV — Curator Fork

This is a personal fork of [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv), modified to work with the [Jellyfin Curator](https://github.com/d3fragg3d/jellyfin-curator) server plugin. It adds Netflix-style curated collection rows to the home screen and (eventually) a richer browsing experience.

---

## What's different from upstream

| File | Status | What we changed |
|------|--------|-----------------|
| `app/.../ui/home/HomeRowsFragment.kt` | Modified | Fetches `isFavorite=true` BoxSets and appends curator rows after standard home sections |
| `app/.../ui/home/HomeFragmentCuratorRow.kt` | **New** | Renders one home row per featured collection |
| `CLAUDE.md` | **New** | AI assistant context (not relevant to the app) |
| `FORK.md` | **New** | This file |

New files we add will never conflict with upstream. Modified upstream files are where conflicts can occur — see below.

---

## Keeping in sync with upstream Jellyfin

The official repo is tracked as the `upstream` remote (already configured). Run this whenever you want to pull in Jellyfin team changes:

```bash
# 1. Fetch latest from official repo (no changes applied yet)
git fetch upstream

# 2. Check what they've added since your last sync
git log --oneline master..upstream/master

# 3. Merge their changes in
git merge upstream/master

# 4. If there are conflicts, resolve them (see below), then:
git push origin master

# 5. Optional: check what their changes touched
git diff master upstream/master --name-only
```

### When there are no conflicts

Most upstream commits are translations, dependency bumps, and unrelated UI work. These merge cleanly with no action needed.

### When there are conflicts

Conflicts will only occur in files we've modified (currently just `HomeRowsFragment.kt`). The pattern to resolve:

1. Open the conflicted file — look for `<<<<<<<` markers
2. Keep **both** sides: upstream's changes + ours
3. Our curator additions are always at specific, isolated points (end of `onCreate`, bottom of the sections loop) — upstream changes are almost always elsewhere in the same files
4. After resolving: `git add <file> && git merge --continue`

If a conflict is messy, `git diff upstream/master...master -- <file>` shows exactly what we changed vs what they changed.

---

## Guiding principle for new features

**Prefer new files over modifying existing upstream files.**

- New Curator-specific UI → new Fragment/Activity subclasses, not edits to existing ones
- If you must hook into an existing upstream file, keep the change minimal and in one clearly-marked block
- This keeps the conflict surface small as upstream evolves

As our UI changes grow (nested folder browser, custom detail screens, etc.), we'll accumulate a list of modified files here so it's easy to see the full conflict surface at a glance.

---

## Conflict risk map (updated as we add features)

| File | Risk | Notes |
|------|------|-------|
| `HomeRowsFragment.kt` | Low | Our addition is at the bottom of the coroutine block; JF rarely touches this |
| *(future browse files)* | Medium | Will grow as we build nested folder browsing |

---

## Remotes

| Remote | URL | Purpose |
|--------|-----|---------|
| `origin` | `https://github.com/d3fragg3d/jellyfin-androidtv-curator.git` | Our fork |
| `upstream` | `https://github.com/jellyfin/jellyfin-androidtv.git` | Official Jellyfin repo |
