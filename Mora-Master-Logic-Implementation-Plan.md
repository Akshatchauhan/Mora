# Mora (by Intellect Explorer) — Unified Master Logic Implementation Specification

**Target Repository:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie`  
**App Name:** `Mora`  
**Developer / Publisher:** `Intellect Explorer` (`https://intellectexplorer.in`)  
**Application ID:** `com.intellectexplorer.mora`  
**Objective:** Implement **all** remaining core logic upgrades across the existing app in **one unified pass** (visual/UI polish will be done in a separate pass after all logic is verified).

---

## Executive Summary of What Is Already Done vs. What Must Be Built Now

### Already Present in the Repository (Keep & Build Upon)
- `app/build.gradle.kts` already sets `applicationId = "com.intellectexplorer.mora"`, `versionCode = 2`, `versionName = "1.1.0"`, and configures `signingConfigs.create("moraRelease")` pointing to `rootProject.file("keystore/mora-release.jks")`.
- `keystore/mora-release.jks` is already generated (`CN=intellectexplorer.in, OU=Mobile, O=Intellect Explorer, L=India, ST=IN, C=IN`).
- `NotionPayloadMapper.kt` and `NotionApiClient.kt` already implement the 4-column **"Mora — Today's Plan"** schema (`Task`, `Today's Plan`, `When`, `Notes`) and `POST /v1/search` auto-discovery.

### Must Be Implemented in This Unified Pass (6 Pillars)
1. **Pillar 1 — Signing & Rebrand Completion:**
   - Populate `gradle.properties` with the `MORA_KEYSTORE_PASSWORD`, `MORA_KEY_PASSWORD`, and `MORA_KEY_ALIAS` credentials and apply `moraRelease` signing to **both** `debug` and `release` buildTypes in `app/build.gradle.kts` so every APK built is officially signed by **Intellect Explorer**.
2. **Pillar 2 — Eliminate Google Play Protect Warning + 4-Layer Low-RAM & Anti-Bypass Architecture:**
   - Remove `DueOrDieAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`) from `AndroidManifest.xml` (which triggers Google Play Protect's sideloaded Accessibility warning).
   - Add `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and `VIBRATE` permissions.
   - Create `MoraKeepAliveListenerService.kt` (`BIND_NOTIFICATION_LISTENER_SERVICE`) so Android's `system_server` keeps `Mora` alive and auto-respawns it on low-RAM devices without triggering Play Protect.
   - Upgrade `OngoingNotificationService.kt` to run the unified foreground app monitor (`UsageStatsHelper` + `MindfulPauseStateMachine` + `TaskDomainRules.evaluateFocusLock`) with **Dual-Mode Interception**:
     - **Primary (Standard RAM + Overlay granted):** Lightweight custom-Canvas window (`TYPE_APPLICATION_OVERLAY`) with `FLAG_NOT_FOCUSABLE` **removed** so Back/Home gestures cannot bypass it.
     - **Low-RAM / Older-Device Fallback:** Full-screen `HostageBlockActivity` (`singleInstance`, `excludeFromRecents="true"`, `USE_FULL_SCREEN_INTENT`) which elevates `Mora` to `FOREGROUND_APP_ADJ = 0` so Android's Low Memory Killer (`lmkd`) never kills the intervention.
     - **Self-Healing Watchdog:** Override `onTaskRemoved()` in `OngoingNotificationService` to schedule an immediate `AlarmManager.ELAPSED_REALTIME_WAKEUP` restart if swiped from Recents.
   - Update `PermissionHelper.kt` so `isAccessibilityServiceEnabled(context)` and `openAccessibilitySettings(context)` seamlessly bridge to the new Play-Protect-safe permissions (`UsageStats` + `Overlay` / `FullScreenIntent` + `BatteryWhitelist`) without breaking any existing Settings/Onboarding UI callers.
3. **Pillar 3 — 1-Tap Browser Notion OAuth + True Two-Way Bi-Directional Notion Sync (`Phone ↔ Notion`):**
   - Add `NotionOAuthConfig.kt` + `NotionOAuthHelper.kt` (`https://api.notion.com/v1/oauth/authorize`, loopback `http://127.0.0.1:45890/notion-callback` + custom scheme `mora://oauth/notion` in `AndroidManifest.xml` and `MainActivity.kt`) and `exchangeOAuthCode()` in `NotionApiClient.kt`.
   - Upgrade `NotionPayloadMapper.kt`, `NotionApiClient.kt` (`queryDatabasePages`), `TaskDao.kt` (`getTaskByNotionPageIdOneShot`), and `NotionSyncManager.kt` (`pullFromNotion` + `syncNow`) so changes made in Notion (`Task` title, `Today's Plan` status flips, `When` deadline edits, `Notes` edits, new rows created in Notion, and rows deleted/archived in Notion) sync back to the phone automatically.
   - Trigger automatic background pulls when `MainActivity.onStart()` runs (with 10s debounce) and every 15 minutes in `DeadlineCheckWorker.kt`.
4. **Pillar 4 — Slow Breath-Rhythm Haptics & Press-and-Hold Completion Haptics (`MoraHaptics.kt`):**
   - Create `com.example.dueordie.util.MoraHaptics` (`Vibrator` / `VibratorManager` + `VibrationEffect.createOneShot`).
   - **Breathing Haptics:** Swelling micro-pulses during `Breathe in` (`0.00 -> 0.88` progress, amplitude `28 -> 135`, stillness at the top of the breath `0.88 -> 1.00`), decelerating release wave during `Breathe out` (`0.00 -> 0.85` progress, amplitude `95 -> 18 -> 0`), and a gentle double-pulse confirmation when the breathing cycle finishes. Wire into both `OngoingNotificationService` overlay and `MindfulPausePreviewDialog`.
   - **Press-and-Hold Completion Haptics:** Accelerating heartbeat cadence (`220ms -> 65ms`) and quadratic amplitude ramp (`35 -> 235`) while holding to complete a task, culminating in a crisp double-thud on completion or a soft cancel tick on early release.
5. **Pillar 5 — 100% Private On-Device Anti-Cheat, Press-and-Hold Task Completion, 10-Minute Overdue Unblock Cooldown & Duolingo-Style Nudges:**
   - **100% Privacy First (No Camera / No Cloud AI):** Remove the `"Optional photo attachment"` toggle from `TaskDialog.kt` and remove `PhotoAttachmentPromptDialog` from `TaskHomeScreen.kt`.
   - **Press-and-Hold Completion Gesture:** Replace the 1-tap checkbox in `TaskHomeScreen.kt` with a `HoldToCompleteButton` that requires a **1.4-second press-and-hold** for pending tasks (`2.6 seconds` for overdue tasks) with a live circular progress ring and `MoraHaptics` ramp. (Tapping an already-completed task still unchecks it immediately.)
   - **10-Minute Overdue Unblock Cooldown:** When the user completes an **overdue** task that had Focus Lock enabled, set `overdueCooldownUntilWallMs = now + 10 * 60 * 1000L` in `OnboardingPreferences`. Update `TaskDomainRules.shouldRunFocusLockMonitor` and `TaskDomainRules.evaluateFocusLock` to accept `cooldownUntilMs` so distracting apps stay locked for a 10-minute focus cooldown after late completion (killing the instant-dopamine incentive to fake-check an overdue task just to open Instagram).
   - **Zero-Bypass `HostageBlockActivity`:** Remove the `"Turn off Focus Lock"` bypass button from `HostageBlockActivity.kt` and block the system Back button (`OnBackPressedCallback(true)` routing to Home).
   - **Duolingo-Style Context-Aware Nudge Engine (`MoraNudgeEngine.kt`):** Generate witty, direct, context-aware accountability copy referencing the blocked app name (`Instagram`, `YouTube`, etc.), active/overdue task title, minutes remaining/overdue, and repeat blocked attempts today (`recordBlockedAttemptAndGetCount` in `OnboardingPreferences`). Display in both the `OngoingNotificationService` overlay and `HostageBlockActivity`.
6. **Pillar 6 — Unit Tests & Verification:**
   - Add unit tests for two-way Notion sync parsing/pulling, 10-minute overdue cooldown enforcement, and `MoraNudgeEngine` copy selection.
   - Verify `./gradlew testDebugUnitTest assembleDebug` passes with 0 errors.

---

## Pillar 1: Signing & Rebrand Completion (`gradle.properties` & `app/build.gradle.kts`)

### 1.1 Update `gradle.properties`
Append the keystore properties to `gradle.properties` so Gradle signs builds automatically without requiring environment variables:

```properties
MORA_KEYSTORE_PASSWORD=MoraIntellect2026!
MORA_KEY_PASSWORD=MoraIntellect2026!
MORA_KEY_ALIAS=mora_intellect_explorer
```

### 1.2 Apply `moraRelease` Signing Config to Both `release` and `debug` in `app/build.gradle.kts`
In `app/build.gradle.kts`, inside `buildTypes`:
- Ensure both `debug` and `release` use `signingConfig = signingConfigs.getByName("moraRelease")` when `moraKeystoreFile.exists()` so that even `assembleDebug` APKs carry the official **Intellect Explorer** certificate (`CN=intellectexplorer.in, O=Intellect Explorer`).

```kotlin
    buildTypes {
        debug {
            if (moraKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("moraRelease")
            }
        }
        release {
            isMinifyEnabled = false
            if (moraKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("moraRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

---

## Pillar 2: Play Protect Fix + 4-Layer Low-RAM & Anti-Bypass Interception Architecture

### 2.1 Update `app/src/main/AndroidManifest.xml`
1. **Add Permissions** at the top of `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
   <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
   <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
   <uses-permission android:name="android.permission.VIBRATE" />
   <uses-permission
       android:name="android.permission.PACKAGE_USAGE_STATS"
       tools:ignore="ProtectedPermissions" />
   ```
2. **Add Deep-Link Intent Filter on `.MainActivity`** for 1-Tap Notion OAuth (`mora://oauth/notion`) and set `android:launchMode="singleTop"`:
   ```xml
   <activity
       android:name=".MainActivity"
       android:exported="true"
       android:launchMode="singleTop"
       android:label="@string/app_name"
       android:theme="@style/Theme.DueOrDie">
       <intent-filter>
           <action android:name="android.intent.action.MAIN" />
           <category android:name="android.intent.category.LAUNCHER" />
       </intent-filter>
       <intent-filter>
           <action android:name="android.intent.action.VIEW" />
           <category android:name="android.intent.category.DEFAULT" />
           <category android:name="android.intent.category.BROWSABLE" />
           <data
               android:scheme="mora"
               android:host="oauth"
               android:pathPrefix="/notion" />
       </intent-filter>
   </activity>
   ```
3. **Update `.ui.hostage.HostageBlockActivity`** declaration for zero-bypass & low-RAM lockscreen/full-screen launch:
   ```xml
   <activity
       android:name=".ui.hostage.HostageBlockActivity"
       android:exported="false"
       android:launchMode="singleInstance"
       android:excludeFromRecents="true"
       android:taskAffinity=""
       android:showOnLockScreen="true"
       android:turnScreenOn="true"
       android:theme="@style/Theme.DueOrDie" />
   ```
4. **Remove `.service.DueOrDieAccessibilityService` from `AndroidManifest.xml` entirely** (do not declare `BIND_ACCESSIBILITY_SERVICE` in the manifest; this completely eliminates the Google Play Protect warning).
5. **Add `.service.MoraKeepAliveListenerService`** (`BIND_NOTIFICATION_LISTENER_SERVICE`) so `system_server` binds to `Mora` and keeps the process alive on low-RAM phones:
   ```xml
   <service
       android:name=".service.MoraKeepAliveListenerService"
       android:label="@string/app_name"
       android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
       android:exported="true">
       <intent-filter>
           <action android:name="android.service.notification.NotificationListenerService" />
       </intent-filter>
   </service>
   ```

### 2.2 Create `app/src/main/java/com/example/dueordie/service/MoraKeepAliveListenerService.kt`
```kotlin
package com.example.dueordie.service

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * System-bound NotificationListenerService that keeps Mora's process bound to Android's
 * system_server so Low Memory Killer (lmkd) on low-RAM devices does not kill the foreground
 * monitor, and automatically respawns OngoingNotificationService if reclaimed.
 * Never reads or stores notification text.
 */
class MoraKeepAliveListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        OngoingNotificationService.startOrUpdate(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                requestRebind(ComponentName(applicationContext, MoraKeepAliveListenerService::class.java))
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // No-op: Mora never reads notification contents.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        fun ensureRebound(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    val cn = ComponentName(context, MoraKeepAliveListenerService::class.java)
                    context.packageManager.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    requestRebind(cn)
                }
            }
        }
    }
}
```

### 2.3 Update `app/src/main/java/com/example/dueordie/util/PermissionHelper.kt`
Update `PermissionHelper.kt` to add Play-Protect-safe permission checks and settings intents while keeping `isAccessibilityServiceEnabled(context)` and `openAccessibilitySettings(context)` as smart delegates so existing UI screens work seamlessly:
- `isOverlayPermissionGranted(context: Context): Boolean` -> `Settings.canDrawOverlays(context)`
- `isFullScreenIntentAllowed(context: Context): Boolean` -> on Android 14+ (`UPSIDE_DOWN_CAKE`), checks `NotificationManager.canUseFullScreenIntent()`, else `true`.
- `isLowRamDevice(context: Context): Boolean` -> `(context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true`
- `isIgnoringBatteryOptimizations(context: Context): Boolean` -> `(context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) == true`
- `isPauseAndFocusAccessGranted(context: Context): Boolean`:
  ```kotlin
  fun isPauseAndFocusAccessGranted(context: Context): Boolean {
      return isUsageStatsPermissionGranted(context) &&
          (isOverlayPermissionGranted(context) || isFullScreenIntentAllowed(context))
  }
  ```
- Update `isAccessibilityServiceEnabled(context: Context): Boolean` to return `isPauseAndFocusAccessGranted(context)`.
- Update `openAccessibilitySettings(context: Context)` so it opens the next needed Play-Protect-safe permission screen in order:
  1. If `!isUsageStatsPermissionGranted(context)` -> `openUsageAccessSettings(context)`
  2. Else if `!isOverlayPermissionGranted(context)` -> `openOverlaySettings(context)` (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`, `Uri.parse("package:${context.packageName}")`)
  3. Else if `!isIgnoringBatteryOptimizations(context)` -> `openUnrestrictedBatteryPrompt(context)` (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `Uri.parse("package:${context.packageName}")`)
  4. Else -> `openOverlaySettings(context)`
- Add `openOverlaySettings(context: Context)` and `openUnrestrictedBatteryPrompt(context: Context)` as public methods on `PermissionHelper`.

---

## Pillar 3: 1-Tap Browser Notion OAuth + True Two-Way Bi-Directional Notion Sync (`Phone ↔ Notion`)

### 3.1 Create `NotionOAuthConfig.kt` & `NotionOAuthHelper.kt`
Create `app/src/main/java/com/example/dueordie/data/notion/NotionOAuthConfig.kt`:
```kotlin
package com.example.dueordie.data.notion

object NotionOAuthConfig {
    const val CLIENT_ID = "" // Optional public OAuth client ID; when blank, UI uses 1-tap token paste or browser portal opener
    const val CLIENT_SECRET = ""
    const val REDIRECT_URI = "http://127.0.0.1:45890/notion-callback"
    const val DEEP_LINK_SCHEME = "mora"
    const val DEEP_LINK_HOST = "oauth"
    const val LOOPBACK_PORT = 45890

    val isPublicOAuthConfigured: Boolean
        get() = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()
}
```

Create `app/src/main/java/com/example/dueordie/data/notion/NotionOAuthHelper.kt`:
- Builds the Notion OAuth URL (`https://api.notion.com/v1/oauth/authorize?client_id=...&response_type=code&owner=user&redirect_uri=...&state=...`).
- Provides `startLoopbackCallbackServer(expectedState: String, onCodeReceived: (Result<String>) -> Unit)` using `java.net.ServerSocket(45890, 1, InetAddress.getByName("127.0.0.1"))` on `Dispatchers.IO` that captures `GET /notion-callback?code=...&state=...`, responds with an HTML page that auto-redirects to `mora://oauth/notion?code=...`, and invokes `onCodeReceived(Result.success(code))`.
- Add `exchangeOAuthCode(clientId: String, clientSecret: String, code: String, redirectUri: String): Result<String>` to `NotionApiClient.kt` (`POST https://api.notion.com/v1/oauth/token` with HTTP Basic Auth `Base64(clientId:clientSecret)`, returning `access_token`).

### 3.2 Upgrade `NotionPayloadMapper.kt` for Two-Way Sync (`Notion → Phone`)
In `app/src/main/java/com/example/dueordie/data/notion/NotionPayloadMapper.kt`:
1. Add data class `RemoteNotionTask`:
   ```kotlin
   data class RemoteNotionTask(
       val pageId: String,
       val title: String,
       val planOption: String?,
       val deadlineEpochMs: Long?,
       val hasExplicitTime: Boolean,
       val notes: String,
       val isArchived: Boolean,
       val lastEditedEpochMs: Long
   )
   ```
2. Add `parseRemotePage(pageJson: JSONObject, titlePropertyName: String = PROP_TASK, zoneId: ZoneId = ZoneId.systemDefault()): RemoteNotionTask?`:
   - Reads `id`, `archived` / `in_trash`, `last_edited_time` (`Instant.parse(...).toEpochMilli()`).
   - Reads `properties`:
     - Title property (`titlePropertyName` or fallback to any property with `"type": "title"`): joins `plain_text`. Returns `null` if blank and not archived.
     - `Today's Plan` (`select.name` or legacy `Status` `select.name`).
     - `When` (`date.start` or legacy `Due` `date.start`):
       - If ISO string contains `'T'`, parse via `OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()` with `hasExplicitTime = true`.
       - If date-only (`yyyy-MM-dd`), parse via `LocalDate.parse(dateStr).atTime(18, 0).atZone(zoneId).toInstant().toEpochMilli()` with `hasExplicitTime = false`.
     - `Notes` (`rich_text` joined `plain_text`).
3. Add `reconcileRemoteTaskWithLocal(...)`:
   ```kotlin
   fun reconcileRemoteTaskWithLocal(
       remote: RemoteNotionTask,
       existingLocal: Task?,
       nowMs: Long = System.currentTimeMillis(),
       zoneId: ZoneId = ZoneId.systemDefault()
   ): Task
   ```
   - **Deadline resolution:**
     - If `remote.deadlineEpochMs != null`:
       - If `!remote.hasExplicitTime && existingLocal != null`: preserve `existingLocal`'s hour/minute on the remote date (`LocalDate` from `remote.deadlineEpochMs` + `LocalTime` from `existingLocal.deadlineTimestamp`).
       - Else use `remote.deadlineEpochMs`.
     - Else: use `existingLocal?.deadlineTimestamp ?: (nowMs + 2 * 60 * 60 * 1000L)`.
   - **Today's Plan status flip resolution:**
     - If `remote.planOption == PLAN_DONE || remote.planOption == "Completed"`:
       - `status = TaskStatus.COMPLETED`
       - `completedAtTimestamp = existingLocal?.completedAtTimestamp ?: nowMs`
     - Else:
       - If `remote.planOption == PLAN_LATER` and `resolvedDeadline <= endOfTodayMs`: shift `resolvedDeadline` to tomorrow keeping the same local time-of-day.
       - If `remote.planOption == PLAN_TODAY` and `resolvedDeadline > endOfTodayMs`: shift `resolvedDeadline` to today keeping the same local time-of-day (or `nowMs + 2h` if that time today already passed).
       - `status = if (resolvedDeadline <= nowMs) TaskStatus.OVERDUE else TaskStatus.PENDING`
       - `completedAtTimestamp = null`
   - Return `Task` with `notionPageId = remote.pageId`, `notionSyncDirty = false`, `lastSyncedAtTimestamp = nowMs`.

### 3.3 Add `queryDatabasePages` to `NotionApiClient.kt`
In `app/src/main/java/com/example/dueordie/data/notion/NotionApiClient.kt`, add:
```kotlin
fun queryDatabasePages(
    token: String,
    databaseId: String,
    titlePropertyName: String = NotionPayloadMapper.PROP_TASK
): Result<List<RemoteNotionTask>>
```
- Paginates `POST https://api.notion.com/v1/databases/$databaseId/query` (`page_size = 100`, `start_cursor`) and parses each page object with `NotionPayloadMapper.parseRemotePage(item, titlePropertyName)`.

### 3.4 Add `getTaskByNotionPageIdOneShot` to `TaskDao.kt`
In `app/src/main/java/com/example/dueordie/data/local/TaskDao.kt`, add:
```kotlin
@Query("SELECT * FROM tasks WHERE notionPageId = :notionPageId LIMIT 1")
suspend fun getTaskByNotionPageIdOneShot(notionPageId: String): Task?
```

### 3.5 Upgrade `NotionSyncManager.kt` for Bi-Directional Sync (`Push + Pull`)
In `app/src/main/java/com/example/dueordie/data/notion/NotionSyncManager.kt`:
- Add `suspend fun pullFromNotion(): Boolean`:
  1. Queries all remote pages via `apiClient.queryDatabasePages(token, databaseId, preferences.titlePropertyName)`.
  2. Builds `seenRemotePageIds = HashSet<String>()`.
  3. For each `remote` page:
     - Skip if `remote.pageId in preferences.pendingArchivedNotionPageIds` or `isNotionSyncFixture(remote.title)`.
     - Add `remote.pageId` to `seenRemotePageIds`.
     - Look up `localTask = taskDao.getTaskByNotionPageIdOneShot(remote.pageId)`.
     - If `remote.isArchived`: if `localTask != null && !localTask.notionSyncDirty`, delete `localTask` locally.
     - If `localTask == null`: insert `NotionPayloadMapper.reconcileRemoteTaskWithLocal(remote, null, nowMs)` into `taskDao`.
     - If `localTask != null`:
       - If `localTask.notionSyncDirty`: keep local changes so the push step uploads them.
       - Else: compute `reconciled = NotionPayloadMapper.reconcileRemoteTaskWithLocal(remote, localTask, nowMs)` and call `taskDao.updateTask(reconciled)` if `title`, `description`, `deadlineTimestamp`, `status`, or `completedAtTimestamp` changed.
  4. For any local task where `notionPageId != null && !notionSyncDirty && notionPageId !in seenRemotePageIds`: delete locally (since it was deleted/archived in Notion).
  5. If any local task changed, call `OngoingNotificationService.startOrUpdate(appContext)` and `DueOrDieWidgetProvider.updateAllWidgets(appContext)`.
- Update `syncNow(forceAll: Boolean)` to execute:
  1. Push local pending archives + dirty/unsynced local tasks (`Phone → Notion`).
  2. Call `pullFromNotion()` (`Notion → Phone`).
  3. Update `preferences.lastSyncTimestamp = System.currentTimeMillis()`.

### 3.6 Auto-Pull Triggers in `MainActivity.kt` and `DeadlineCheckWorker.kt`
- In `MainActivity.kt`:
  - Override `onStart()` to call `(applicationContext as? DueOrDieApplication)?.notionSyncManager?.triggerForegroundPullIfNeeded()` (which triggers `triggerBackgroundSync()` when Notion is connected, auto-sync is enabled, and `now - (preferences.lastSyncTimestamp ?: 0L) > 10_000L`).
- In `DeadlineCheckWorker.kt`:
  - When `notionPrefs != null && !notionPrefs.tasksDatabaseId.isNullOrBlank() && notionPrefs.isAutoSyncEnabled`, call `app.notionSyncManager.syncNow(forceAll = false)` so Notion edits are pulled in the background every 15 minutes even when the app hasn't been opened.

---

## Pillar 4: Slow Breath-Rhythm Haptics & Press-and-Hold Completion Haptics (`MoraHaptics.kt`)

### 4.1 Create `app/src/main/java/com/example/dueordie/util/MoraHaptics.kt`
```kotlin
package com.example.dueordie.util

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Tactile haptic engine for Mora:
 * 1. Slow Breath-Rhythm Haptics synced to the breathing circle (inhale swell, top-of-breath stillness, exhale release).
 * 2. Press-and-Hold Completion Haptics (accelerating heartbeat ramp + crisp completion thud).
 */
class MoraHaptics(context: Context) {

    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastBreathPulseElapsedMs: Long = 0L
    private var lastHoldPulseElapsedMs: Long = 0L

    fun resetBreathCycle() {
        lastBreathPulseElapsedMs = 0L
    }

    fun resetHoldCycle() {
        lastHoldPulseElapsedMs = 0L
    }

    /**
     * Called on animation frames during the breathing cycle.
     * @param phaseProgress 0.0f..1.0f within current phase
     * @param isInhale true during "Breathe in", false during "Breathe out"
     */
    fun onBreathingFrame(
        phaseProgress: Float,
        isInhale: Boolean,
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val p = phaseProgress.coerceIn(0f, 1f)

        if (isInhale) {
            // Top-of-breath stillness
            if (p > 0.88f) return
            val normalized = (p / 0.88f).coerceIn(0f, 1f)
            val intervalMs = (210f - 100f * normalized).toLong()
            if (elapsedRealtimeMs - lastBreathPulseElapsedMs < intervalMs) return
            lastBreathPulseElapsedMs = elapsedRealtimeMs

            val curved = normalized.pow(1.4f)
            val amplitude = (28f + 107f * curved).roundToInt().coerceIn(1, 255)
            val durationMs = (24L + (22f * curved).toLong())
            vibrateOneShot(vib, durationMs, amplitude)
        } else {
            // Bottom-of-breath rest
            if (p > 0.85f) return
            val normalized = (p / 0.85f).coerceIn(0f, 1f)
            val intervalMs = (140f + 180f * normalized).toLong()
            if (elapsedRealtimeMs - lastBreathPulseElapsedMs < intervalMs) return
            lastBreathPulseElapsedMs = elapsedRealtimeMs

            val fade = (1f - normalized).pow(1.6f)
            val amplitude = (18f + 77f * fade).roundToInt().coerceIn(1, 255)
            val durationMs = (36L - (16f * normalized).toLong()).coerceAtLeast(16L)
            vibrateOneShot(vib, durationMs, amplitude)
        }
    }

    fun onBreathingCycleComplete() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 28L, 65L, 45L),
                        intArrayOf(0, 85, 0, 145),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(45L)
            }
        }
    }

    /**
     * Called continuously while the user presses and holds to complete a task.
     * @param holdProgress 0.0f..1.0f
     */
    fun onHoldToCompleteProgress(
        holdProgress: Float,
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val p = holdProgress.coerceIn(0f, 1f)
        if (p <= 0.01f || p >= 1.0f) return

        val intervalMs = (220f - 155f * p).toLong().coerceAtLeast(60L)
        if (elapsedRealtimeMs - lastHoldPulseElapsedMs < intervalMs) return
        lastHoldPulseElapsedMs = elapsedRealtimeMs

        val curved = p.pow(1.7f)
        val amplitude = (35f + 200f * curved).roundToInt().coerceIn(1, 255)
        val durationMs = (16L + (20f * curved).toLong())
        vibrateOneShot(vib, durationMs, amplitude)
    }

    fun onHoldTaskCompleted() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 22L, 48L, 65L),
                        intArrayOf(0, 120, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(70L)
            }
        }
    }

    fun onHoldCancelled() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        vibrateOneShot(vib, 14L, 45)
    }

    fun cancel() {
        runCatching { vibrator?.cancel() }
    }

    private fun vibrateOneShot(vib: Vibrator, durationMs: Long, amplitude: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                } else {
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }
}
```

### 4.2 Wire `MoraHaptics` into `MindfulPausePreviewDialog` (`MindfulPauseComponents.kt`)
In `app/src/main/java/com/example/dueordie/ui/permissions/MindfulPauseComponents.kt`:
- Inside `MindfulPausePreviewDialog`, create `val haptics = remember(context) { MoraHaptics(context) }` and cancel it in `DisposableEffect(Unit) { onDispose { haptics.cancel() } }`.
- Call `haptics.onBreathingFrame(progress, isInhale = phaseIndex % 2 == 0)` inside a `LaunchedEffect(progress, phaseIndex, isBreathingComplete)` when `!reducedMotion && !isBreathingComplete`.
- Call `haptics.onBreathingCycleComplete()` when `isBreathingComplete` becomes `true`.

---

## Pillar 5: 100% Private Anti-Cheat, Press-and-Hold Completion, 10-Minute Cooldown, Duolingo-Style Nudges & Unified `OngoingNotificationService`

### 5.1 Add Cooldown & Blocked-Attempt Tracking to `OnboardingPreferences.kt`
In `app/src/main/java/com/example/dueordie/util/OnboardingPreferences.kt`, add:
```kotlin
    var overdueCooldownUntilWallMs: Long
        get() = prefs.getLong(KEY_OVERDUE_COOLDOWN_UNTIL_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_OVERDUE_COOLDOWN_UNTIL_MS, value).apply()

    var lastCooldownTaskTitle: String
        get() = prefs.getString(KEY_LAST_COOLDOWN_TASK_TITLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_COOLDOWN_TASK_TITLE, value).apply()

    fun isInOverdueCooldown(nowWallMs: Long = System.currentTimeMillis()): Boolean {
        return overdueCooldownUntilWallMs > nowWallMs
    }

    fun remainingCooldownMinutes(nowWallMs: Long = System.currentTimeMillis()): Int {
        val diff = overdueCooldownUntilWallMs - nowWallMs
        if (diff <= 0L) return 0
        return ((diff + 59_999L) / 60_000L).toInt()
    }

    fun recordBlockedAttemptAndGetCount(
        packageName: String,
        dayKey: String = java.time.LocalDate.now().toString()
    ): Int {
        val storedDay = prefs.getString(KEY_ATTEMPT_DAY, "")
        val key = "${KEY_ATTEMPT_PREFIX}$packageName"
        val nextCount = if (storedDay != dayKey) {
            prefs.edit().putString(KEY_ATTEMPT_DAY, dayKey).putInt(key, 1).apply()
            1
        } else {
            val c = prefs.getInt(key, 0) + 1
            prefs.edit().putInt(key, c).apply()
            c
        }
        return nextCount
    }
```
(Add the corresponding private `const val` keys in `OnboardingPreferences.Companion` and `const val OVERDUE_UNBLOCK_COOLDOWN_MS = 10 * 60 * 1000L`.)

### 5.2 Update `TaskDomainRules.kt` to Enforce the 10-Minute Overdue Unblock Cooldown
In `app/src/main/java/com/example/dueordie/data/model/TaskDomainRules.kt`:
1. Add `val isCooldownActive: Boolean = false` and `val cooldownRemainingMinutes: Int = 0` to `FocusLockDecision`:
   ```kotlin
   data class FocusLockDecision(
       val shouldIntervene: Boolean,
       val blockingTask: Task? = null,
       val overdueMinutes: Long = 0L,
       val isCooldownActive: Boolean = false,
       val cooldownRemainingMinutes: Int = 0
   )
   ```
2. Update `shouldRunFocusLockMonitor`:
   ```kotlin
   fun shouldRunFocusLockMonitor(
       isGlobalFocusLockEnabled: Boolean,
       hasPermission: Boolean,
       tasks: List<Task>,
       nowMs: Long = System.currentTimeMillis(),
       cooldownUntilMs: Long = 0L
   ): Boolean {
       return isGlobalFocusLockEnabled &&
           hasPermission &&
           (hasQualifyingFocusLockTasks(tasks, nowMs) || cooldownUntilMs > nowMs)
   }
   ```
3. Update `evaluateFocusLock` to accept `cooldownUntilMs: Long = 0L` and `cooldownTaskTitle: String = "Recently completed task"`:
   - If `qualifyingOverdueTasks` is empty **and** `cooldownUntilMs > nowMs`:
     - Calculate `remMins = ((cooldownUntilMs - nowMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)`
     - Synthesize a placeholder `Task(id = -1L, title = cooldownTaskTitle.ifBlank { "Overdue task cooldown" }, description = "10-minute focus cooldown active", deadlineTimestamp = nowMs - remMins * 60_000L, status = TaskStatus.OVERDUE, isHostageModeEnabled = true)`
     - Return `FocusLockDecision(shouldIntervene = true, blockingTask = placeholder, overdueMinutes = remMins.toLong(), isCooldownActive = true, cooldownRemainingMinutes = remMins)`.

### 5.3 Create `MoraNudgeEngine.kt` (Duolingo-Style On-Device Context-Aware Nudges)
Create `app/src/main/java/com/example/dueordie/util/MoraNudgeEngine.kt`:
```kotlin
package com.example.dueordie.util

import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import kotlin.math.abs

data class MoraNudgeCopy(
    val headline: String,
    val subtext: String,
    val badgeText: String? = null
)

object MoraNudgeEngine {

    fun buildPauseNudge(
        appLabel: String,
        activeTasks: List<Task>,
        attemptCountToday: Int,
        fallbackPrompt: String,
        nowMs: Long = System.currentTimeMillis()
    ): MoraNudgeCopy {
        val nextTask = activeTasks
            .filter { it.status != TaskStatus.COMPLETED }
            .minByOrNull { it.deadlineTimestamp }

        if (nextTask == null) {
            return if (attemptCountToday >= 3) {
                MoraNudgeCopy(
                    headline = "$appLabel again? That's visit #$attemptCountToday today.",
                    subtext = fallbackPrompt.ifBlank { "Take two slow breaths and make sure you're opening $appLabel intentionally." },
                    badgeText = "Visit #$attemptCountToday today"
                )
            } else {
                MoraNudgeCopy(
                    headline = "Pause before $appLabel",
                    subtext = fallbackPrompt.ifBlank { "Take two slow breaths before continuing." }
                )
            }
        }

        val diffMinutes = (nextTask.deadlineTimestamp - nowMs) / 60_000L
        return when {
            diffMinutes < 0 -> {
                val lateMins = abs(diffMinutes).coerceAtLeast(1L)
                MoraNudgeCopy(
                    headline = "\"${nextTask.title}\" is ${formatDuration(lateMins)} overdue.",
                    subtext = "$appLabel won't finish it for you. Breathe in, close the feed, and knock it out.",
                    badgeText = "Overdue · Visit #$attemptCountToday"
                )
            }
            diffMinutes <= 120 -> {
                MoraNudgeCopy(
                    headline = "\"${nextTask.title}\" is due in ${formatDuration(diffMinutes.coerceAtLeast(1L))}.",
                    subtext = "A 5-minute scroll on $appLabel usually turns into 40. Finish \"${nextTask.title}\" first?",
                    badgeText = "Due soon"
                )
            }
            attemptCountToday >= 3 -> {
                MoraNudgeCopy(
                    headline = "Visit #$attemptCountToday to $appLabel today.",
                    subtext = "\"${nextTask.title}\" is still waiting on your list. Give it 10 focused minutes first.",
                    badgeText = "Visit #$attemptCountToday today"
                )
            }
            else -> {
                MoraNudgeCopy(
                    headline = "Pause before $appLabel",
                    subtext = "\"${nextTask.title}\" is still on today's plan. Take two slow breaths before deciding.",
                    badgeText = "1 task waiting"
                )
            }
        }
    }

    fun buildFocusLockNudge(
        appLabel: String,
        taskTitle: String,
        overdueMinutes: Long,
        attemptCountToday: Int,
        isCooldownActive: Boolean = false,
        cooldownRemainingMinutes: Int = 0
    ): MoraNudgeCopy {
        if (isCooldownActive) {
            return MoraNudgeCopy(
                headline = "10-minute focus cooldown ($cooldownRemainingMinutes min left)",
                subtext = "Nice job finishing \"$taskTitle\"! Because it was overdue, $appLabel unlocks in $cooldownRemainingMinutes min so the momentum sticks.",
                badgeText = "Anti-cheat cooldown"
            )
        }

        val durationText = formatDuration(overdueMinutes.coerceAtLeast(1L))
        val lines = listOf(
            "$appLabel is locked until \"$taskTitle\" is done ($durationText late).",
            "Plot twist: \"$taskTitle\" is still waiting ($durationText overdue). $appLabel can wait.",
            "Future you called — they want \"$taskTitle\" finished before you open $appLabel."
        )
        val picked = lines[(attemptCountToday - 1).coerceAtLeast(0) % lines.size]
        return MoraNudgeCopy(
            headline = "$appLabel is on Focus Lock",
            subtext = picked,
            badgeText = if (attemptCountToday > 1) "Blocked attempt #$attemptCountToday today" else "$durationText overdue"
        )
    }

    private fun formatDuration(minutes: Long): String {
        return if (minutes < 60L) {
            "${minutes}m"
        } else {
            val h = minutes / 60L
            val m = minutes % 60L
            if (m == 0L) "${h}h" else "${h}h ${m}m"
        }
    }
}
```

### 5.4 Remove Photo Proof UI & Add Press-and-Hold Completion + Cooldown (`TaskDialog.kt`, `TaskViewModel.kt`, `TaskHomeScreen.kt`)
1. **In `TaskDialog.kt`:**
   - Remove the `"Optional photo attachment"` `Row` and `Switch` (lines 317–367) so users are never asked for camera/photo proof. Keep the `onSave` lambda signature intact (passing `requirePhotoProof = false, photoPath = null`) so all call sites remain clean.
2. **In `TaskViewModel.kt`:**
   - Update `toggleTaskStatus(task: Task)`:
     - Remove the `if (task.requirePhotoProof && task.photoPath.isNullOrBlank())` branch so completing a task never opens a camera dialog.
     - When completing a task (`task.status != TaskStatus.COMPLETED`), check if the task was overdue (`task.isOverdueAt(now)`):
       - If `task.isOverdueAt(now)` and `(task.isHostageModeEnabled || OnboardingPreferences(appContext).isGlobalHostageModeEnabled)`:
         - Set `val prefs = OnboardingPreferences(appContext)`
         - `prefs.overdueCooldownUntilWallMs = now + OnboardingPreferences.OVERDUE_UNBLOCK_COOLDOWN_MS`
         - `prefs.lastCooldownTaskTitle = task.title`
         - Set `snackbarMessage = "Marked \"${task.title}\" complete · Focus Lock cools down for 10m"`
       - Else set `snackbarMessage = "Marked \"${task.title}\" complete"`
3. **In `TaskHomeScreen.kt`:**
   - Remove `PhotoAttachmentPromptDialog` invocation and definition.
   - Replace the 1-tap `IconButton` checkbox inside `TaskItemCard` with a **`HoldToCompleteButton`** composable:
     - If `isCompleted == true`: a normal tap calls `onToggleStatus()` to uncheck immediately.
     - If `isCompleted == false`: uses `pointerInput(task.id, isOverdue)` with `detectTapGestures(onPress = { ... })`:
       - Required hold duration: `val holdDurationMs = if (isOverdue) 2400L else 1300L`.
       - While pressed, loops every `16ms` updating `holdProgress = ((SystemClock.elapsedRealtime() - startMs).toFloat() / holdDurationMs).coerceIn(0f, 1f)` and calling `haptics.onHoldToCompleteProgress(holdProgress)`.
       - Draws a `CircularProgressIndicator(progress = { holdProgress })` around the radio icon while `holdProgress > 0f`.
       - If held to `1.0f`: calls `haptics.onHoldTaskCompleted()` and `onToggleStatus()`.
       - If released early (`tryAwaitRelease()` before `1.0f`): calls `haptics.onHoldCancelled()`, resets `holdProgress = 0f`, and shows a brief hint `"Hold to mark complete"` in the card subtitle or snackbar.

### 5.5 Upgrade `HostageBlockActivity.kt` (Zero-Bypass + Duolingo Nudge + Cooldown Support)
In `app/src/main/java/com/example/dueordie/ui/hostage/HostageBlockActivity.kt`:
1. Block the system Back button in `onCreate` using `onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { navigateToHomeScreen() } })`.
2. Support both **Focus Lock mode** and **Low-RAM Mindful Pause fallback mode** via Intent extras:
   - `EXTRA_IS_FOCUS_LOCK` (`Boolean`, default `true`)
   - `EXTRA_BLOCKED_PACKAGE` (`String`)
   - `EXTRA_BLOCKING_TASK_TITLE` (`String`)
   - `EXTRA_OVERDUE_MINUTES` (`Long`)
   - `EXTRA_IS_COOLDOWN` (`Boolean`, default `false`)
   - `EXTRA_COOLDOWN_MINUTES` (`Int`, default `0`)
   - `EXTRA_NUDGE_HEADLINE` (`String`)
   - `EXTRA_NUDGE_SUBTEXT` (`String`)
   - `EXTRA_NUDGE_BADGE` (`String`)
3. **Remove the `"Turn off Focus Lock"` button (`onTurnOffFocusLock`) completely** from `HostageBlockActivity.kt` so the user cannot disable Focus Lock from the block screen.
4. Wire `MoraHaptics` into `HostageBlockScreen`'s breathing animation so low-RAM devices that use `HostageBlockActivity` get the exact same slow breath-rhythm haptics.
5. Primary button: `"Open Mora tasks"` (launches `MainActivity` so the user can complete the task). Secondary button: `"Return to Home Screen"` (routes to `Intent.CATEGORY_HOME`).

### 5.6 Upgrade `OngoingNotificationService.kt` to Run the 4-Layer Low-RAM & Anti-Bypass Monitor
In `app/src/main/java/com/example/dueordie/service/OngoingNotificationService.kt`:
1. Keep the existing persistent task notification logic, and expand `shouldRunService(context, activeTasks, nowMs)` so the service also runs whenever:
   - `prefs.isMindfulPauseEnabled && prefs.pauseSelectedPackages.isNotEmpty()`, OR
   - `prefs.isGlobalHostageModeEnabled && (TaskDomainRules.hasQualifyingFocusLockTasks(activeTasks, nowMs) || prefs.isInOverdueCooldown(nowMs))`
2. Add the foreground app monitor loop (`UsageStatsHelper.getForegroundApp(context)`) using `MindfulPauseStateMachine`:
   - Poll every `750ms` while the screen is interactive (`PowerManager.isInteractive`) and either Mindful Pause or Focus Lock is active.
   - Evaluate `FocusLockDecision` using `TaskDomainRules.evaluateFocusLock(..., cooldownUntilMs = prefs.overdueCooldownUntilWallMs, cooldownTaskTitle = prefs.lastCooldownTaskTitle)`.
   - Feed `(pkgName, SystemClock.elapsedRealtime(), focusDecision)` into `MindfulPauseStateMachine.onForegroundPackageChanged(...)`.
3. **Dual-Mode Interception when transitioning to `MindfulPauseState.PauseVisible`:**
   - Compute Duolingo-style nudge copy via `MoraNudgeEngine.buildFocusLockNudge(...)` or `MoraNudgeEngine.buildPauseNudge(...)`.
   - **If `FocusLock` is triggered (`pauseState.isFocusLock == true`)** OR **`!PermissionHelper.isOverlayPermissionGranted(this)`** OR **`PermissionHelper.isLowRamDevice(this)`**:
     - Launch `HostageBlockActivity` with `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP` and all nudge/cooldown extras.
   - **Else (Standard RAM + `Settings.canDrawOverlays(this)` for Mindful Pause):**
     - Show the custom-Canvas breathing overlay window using `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` with `WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN` (and **NO** `FLAG_NOT_FOCUSABLE`, so Back/keys are intercepted), wired with `MoraHaptics.onBreathingFrame(...)` and `MoraHaptics.onBreathingCycleComplete()`, and **NO** `"Turn off Focus Lock"` button.
4. **Override `onTaskRemoved(rootIntent: Intent?)`:**
   - Schedule an immediate self-healing restart via `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1500L, pendingIntent)` and call `MoraKeepAliveListenerService.ensureRebound(applicationContext)`.

### 5.7 Close the 5 Real-World Bypass & Timing Loopholes
1. **Boot & App-Update Auto-Start (`MoraBootReceiver.kt` + `AndroidManifest.xml`):**
   - Create `app/src/main/java/com/example/dueordie/service/MoraBootReceiver.kt` extending `BroadcastReceiver`.
   - On `Intent.ACTION_BOOT_COMPLETED`, `Intent.ACTION_LOCKED_BOOT_COMPLETED`, `"android.intent.action.QUICKBOOT_POWERON"`, or `Intent.ACTION_MY_PACKAGE_REPLACED`:
     - Call `WorkManagerScheduler.scheduleAllWorkers(context)`
     - Call `OngoingNotificationService.startOrUpdate(context)`
     - Call `MoraKeepAliveListenerService.ensureRebound(context)`
   - Register `.service.MoraBootReceiver` in `AndroidManifest.xml` (`android:exported="true"`, `android:directBootAware="true"`) with those intent actions so rebooting the phone or installing an update never disables Focus Lock or Mindful Pause.
2. **Close the "Delete / Reschedule / Toggle-Off" Escape Hatch (`TaskViewModel.kt` & `RulesAndBlocklistScreen.kt`):**
   - In `TaskViewModel.saveTask(...)` (when editing an existing task) and `TaskViewModel.deleteTask(task)`:
     - If `existingTask.isOverdueAt(now)` and `(existingTask.isHostageModeEnabled || OnboardingPreferences(appContext).isGlobalHostageModeEnabled)`:
       - Deleting the overdue task, rescheduling its deadline into the future (`deadlineTimestamp > now`), or turning off its `isHostageModeEnabled` toggle **must also trigger the 10-minute Focus Lock cooldown**:
         ```kotlin
         val prefs = OnboardingPreferences(appContext)
         prefs.overdueCooldownUntilWallMs = now + OnboardingPreferences.OVERDUE_UNBLOCK_COOLDOWN_MS
         prefs.lastCooldownTaskTitle = existingTask.title
         ```
   - In `TaskViewModel.undoLastAction()`: never clear `overdueCooldownUntilWallMs`.
   - In `RulesAndBlocklistScreen.kt`: when the user toggles `Overdue Focus Lock` (`isFocusLockEnabled`) from `true` to `false`, check if any task is currently overdue (`TaskDomainRules.hasQualifyingFocusLockTasks(tasks, now)`) and if so, set `onboardingPrefs.overdueCooldownUntilWallMs = now + OnboardingPreferences.OVERDUE_UNBLOCK_COOLDOWN_MS` before disabling, or keep `overdueCooldownUntilWallMs` active in `TaskDomainRules.evaluateFocusLock` even if `isGlobalFocusLockEnabled` was just flipped off during an active cooldown (`val effectiveFocusLock = isGlobalFocusLockEnabled || cooldownUntilMs > nowMs`).
3. **Exact-Second Deadline Trigger (`OngoingNotificationService.kt`):**
   - Do not wait up to 15 minutes for `WorkManager` (`DeadlineCheckWorker`) to notice a task just became overdue.
   - Inside `OngoingNotificationService`, whenever `activeTasks` updates, find the earliest pending deadline (`val nextPendingDeadlineMs = activeTasks.filter { it.status == TaskStatus.PENDING && it.deadlineTimestamp > now }.minOfOrNull { it.deadlineTimestamp }`).
   - Post a `mainHandler` callback for `(nextPendingDeadlineMs - System.currentTimeMillis() + 250L).coerceAtLeast(250L)` that immediately runs `app.taskRepository.checkAndUpdateOverdueTasks(System.currentTimeMillis())`, posts the overdue notification, and re-evaluates the current foreground app so Focus Lock intercepts the user on the **exact second** a deadline passes (even if they are already mid-scroll in Instagram).
4. **Default `isHostageModeEnabled = true` for New Tasks Created in Notion (`NotionPayloadMapper.kt`):**
   - Because the 4-column Notion table (`Task`, `Today's Plan`, `When`, `Notes`) has no separate `Focus Lock` column, in `NotionPayloadMapper.reconcileRemoteTaskWithLocal(...)`:
     - Preserve `existingLocal?.isHostageModeEnabled` when updating an existing task.
     - Default `isHostageModeEnabled = existingLocal?.isHostageModeEnabled ?: true` for new tasks created remotely in Notion so tasks planned on desktop are automatically enforced by Focus Lock on the phone.
5. **Duolingo-Style Proactive Notifications (`MoraNudgeEngine.kt`, `DeadlineCheckWorker.kt`, `DailyReminderWorker.kt`):**
   - Add `buildReminderNotificationCopy(task: Task, minutesLeft: Long, streakDays: Int): Pair<String, String>` and `buildOverdueNotificationCopy(taskTitle: String, overdueCount: Int, streakDays: Int): Pair<String, String>` to `MoraNudgeEngine.kt`.
   - Use `MoraNudgeEngine` in `DeadlineCheckWorker.kt` and `DailyReminderWorker.kt` so due-soon, overdue, and daily check-in notifications use witty, streak-aware Duolingo-style nudges instead of generic text.

---

## Pillar 6: Unit Tests & Build Verification

### 6.1 Add / Update Unit Tests under `app/src/test/java/com/example/dueordie/`
1. **Update `NotionPayloadMapperTest.kt`:**
   - Test `parseRemotePage` & `reconcileRemoteTaskWithLocal` for:
     - A Notion row marked `✅ Done` -> reconciles local task to `TaskStatus.COMPLETED`.
     - A Notion row marked `⏳ Not Today (Later)` -> shifts today's deadline to tomorrow while preserving local hour/minute.
     - A Notion row with a date-only `When` (`2026-10-15`) -> preserves the existing local task's time-of-day (`14:30`) on the new date.
     - A brand-new Notion row (`existingLocal = null`) -> defaults `isHostageModeEnabled = true`.
2. **Update `TaskDomainRulesTest.kt`:**
   - Test `evaluateFocusLock` and `shouldRunFocusLockMonitor` when all overdue tasks are completed, rescheduled, or deleted **but** `cooldownUntilMs > nowMs` -> verifies `shouldIntervene == true`, `isCooldownActive == true`, and `cooldownRemainingMinutes == 10` (even if `isGlobalFocusLockEnabled` was toggled off mid-cooldown).
3. **Create `MoraNudgeEngineTest.kt`:**
   - Verify `buildFocusLockNudge` returns cooldown copy when `isCooldownActive = true`.
   - Verify `buildPauseNudge` references the overdue/upcoming task title and blocked attempt count (`Visit #3 today`).
   - Verify `buildReminderNotificationCopy` and `buildOverdueNotificationCopy` include task title and streak context.

### 6.2 Final Verification Command
Run from `C:\Users\aksha\AndroidStudioProjects\DueOrDie`:
```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```
Confirm:
- All unit tests pass (`BUILD SUCCESSFUL`).
- `app/build/outputs/apk/debug/app-debug.apk` is generated, signed by `CN=intellectexplorer.in, O=Intellect Explorer`, with `applicationId = com.intellectexplorer.mora`, and contains **zero** `BIND_ACCESSIBILITY_SERVICE` declarations in its merged manifest.
