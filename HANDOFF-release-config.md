# Handoff: release-config changes and follow-ups for Dark Mahjong

**From:** a Claude Code cloud review session. **For:** the local agent working in this repository.

**Base:** branch `dark-mahjong` at `b5204e4` ("Keep the title photo's original and the tool that grades it").

## Background

- The cloud session reviewed the whole app across several passes and found 39 issues. All 39 have since been addressed.
- A final pass checked the storage-error fix and the release build setup. The storage fix is sound; no changes are needed.
- The release check led to **three small changes**. The cloud session made and committed them, but couldn't push, so they are **not on GitHub**. Don't look for a `claude/release-config` branch: it only existed in that cloud container. Make these changes here.

## Task 1: apply the three release-config changes

1. **`app/build.gradle.kts`, in `dependenciesInfo`:** set `includeInBundle = true`, and keep `includeInApk = false`.
   - Only Google Play receives the bundle's dependency metadata, encrypted, and Play Console uses it to flag outdated or vulnerable libraries.
   - F-Droid builds APKs, which still leave the metadata out.
2. **`app/src/main/AndroidManifest.xml`:** add `android:enableOnBackInvokedCallback="true"` to `<application>`.
   - This opts into predictive back on Android 14 and 15. Android 16 turns it on by itself for this target SDK.
   - It was checked that nothing overrides `onBackPressed` or handles `KEYCODE_BACK`. The flag stops those being called, so check again if that has changed.
3. **`tools/build-play-bundle.sh`:** add `-PperfHooks=false` to the `gradlew bundlePlayRelease` command.
   - A command-line `-P` overrides `gradle.properties`, so a leftover `perfHooks=true` can never reach a Play build.

The exact change as a patch against `b5204e4`. Save it as `release-config.patch` and run `git apply release-config.patch`. If it doesn't apply cleanly, make the three edits by hand as described above.

```diff
diff --git a/app/build.gradle.kts b/app/build.gradle.kts
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -44,8 +44,9 @@ android {
     dependenciesInfo {
         // Disables dependency metadata when building APKs (for F-Droid)
         includeInApk = false
-        // Disables dependency metadata when building Android App Bundles
-        includeInBundle = false
+        // Kept in the App Bundle, which only Google Play receives (encrypted), so Play Console can
+        // flag outdated or vulnerable libraries
+        includeInBundle = true
     }
     compileOptions {
         sourceCompatibility = JavaVersion.VERSION_11
diff --git a/app/src/main/AndroidManifest.xml b/app/src/main/AndroidManifest.xml
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@ -3,9 +3,11 @@
 
     <uses-permission android:name="android.permission.VIBRATE" />
 
-    <!-- A game, so large screens (tablets, unfolded foldables) keep its portrait lock -->
+    <!-- A game, so large screens (tablets, unfolded foldables) keep its portrait lock. Predictive
+         back is opted into for Android 14 and 15; Android 16 turns it on itself for this target -->
     <application
         android:appCategory="game"
+        android:enableOnBackInvokedCallback="true"
         android:allowBackup="true"
         android:dataExtractionRules="@xml/data_extraction_rules"
         android:icon="@mipmap/ic_launcher"
diff --git a/tools/build-play-bundle.sh b/tools/build-play-bundle.sh
--- a/tools/build-play-bundle.sh
+++ b/tools/build-play-bundle.sh
@@ -15,9 +15,10 @@ KEYSTORE="$HOME/.config/dark-mahjong/upload-keystore.p12"
 DARK_MAHJONG_UPLOAD_KEY_PASS="$(python3 "$HOME/server_secrets.py" get DARK_MAHJONG_UPLOAD_KEY_PASS)"
 export DARK_MAHJONG_UPLOAD_KEY_PASS
 
-# Memory-capped, as for every Gradle build on this server
+# Memory-capped, as for every Gradle build on this server. Perf hooks are forced off, whatever
+# gradle.properties says
 systemd-run --user --collect --wait --pipe -p MemoryMax=6G -p MemorySwapMax=1G \
-    --working-directory="$PWD" --unit=mahjong-build "$PWD/gradlew" bundlePlayRelease --no-daemon --console=plain -q
+    --working-directory="$PWD" --unit=mahjong-build "$PWD/gradlew" bundlePlayRelease -PperfHooks=false --no-daemon --console=plain -q
 
 VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
 mkdir -p dist
```

Suggested commit message:

```
Release build: dependency info for Play, predictive back on Android 14 and 15, perf hooks forced off
```

**Verify:**
- The unit tests pass.
- `tools/build-play-bundle.sh` still produces a signed bundle in `dist/`.
- On an Android 14 or 15 phone, a back swipe shows the app's predictive back preview.

None of these could be run in the cloud session, because it can't build the Android app.

## Task 2 (optional): two hardening lines in `PreferencesRepository`

These only matter for hand-edited or damaged preference values the app never writes itself, but each is a one-line guard against a crash on every launch.
- **`migrateLevelOrder`:** `LayoutDifficulty.highestAfterReorder` calls `levelsBeforeMeasuredOrder.take(highest)`, which throws for a negative `highest`. Pass `highest.coerceAtLeast(0)`.
- **`dealFromBag`:** a negative stored `position` makes `order()[position]` throw. Use `.coerceAtLeast(0)` when reading it.

## Task 3: check that these earlier review notes were covered

The reviews also listed these outside their main findings. If any is still open, ask the user whether to fix it.
- **Stalemate dialog:** it swallows Back (`onDismissRequest = {}`).
- **Restart:** it has no confirmation, and it deals a new board rather than replaying the same one. This may be by design.
- **Accessibility:** the board has no semantics, so TalkBack users can't play.
- **`FireplaceSound`:** it creates its MediaPlayers with a synchronous `prepare()` on the main thread at each move between menu screens. `playWin` does the same at the moment of the win.
- **Audio focus:** `AudioManager` never requests it, so other apps' audio and calls don't pause the music.

## Before the next release: manual checks for the user

1. **Upgrade test.** Install the last Play release, play a few levels, change some settings and leave a game half-finished. Then install the new build over it without uninstalling, and check that:
   - the unlocked levels are unchanged;
   - the settings are kept;
   - Continue restores the game.
2. **Win flow.** Clear a board, then tap all around the celebration. Nothing should un-win the board.
3. **Tutorial.** Swipe the app away mid-tutorial and reopen it. It should resume on the same step.
4. **Tablet or foldable.** The app stays portrait.
5. **Release build.** Install a release build, not just a debug one, since R8 issues only show up there.
