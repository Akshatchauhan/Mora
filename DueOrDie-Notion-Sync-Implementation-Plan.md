# DueOrDie: Automatic Notion Sync & Notion Tab Implementation Specification

**Target Project:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie`  
**Package Namespace:** `com.example.dueordie`  
**Goal:** Add a dedicated **Notion** bottom-navigation tab and **zero-friction automatic Notion synchronization** so that every task created, edited, completed, marked overdue, or deleted inside `DueOrDie` is automatically created/updated/archived inside the user's Notion workspace without adding extra steps to task creation.

---

## 1. Product & UX Requirements ("Ease of Access First")

1. **Zero Extra Steps During Task Creation:**
   - When the user creates a task in `TaskDialog` -> `TaskViewModel.saveTask(...)` -> `TaskRepository.insertTask(...)`, the local Room insert must remain instant (offline-first).
   - Immediately after local insertion (and whenever a task is edited, marked complete, undone, marked overdue, or deleted), the app must automatically push the change to Notion in the background if Notion is connected and auto-sync is enabled.
   - Do **not** sync tasks whose title starts with `"[Example] "` or equals `"Submit_quarterly_report"` (the built-in onboarding/test fixtures).

2. **One-Tap Notion Database Provisioning (No Manual Column Setup by the User):**
   - Users should **not** have to manually create 7 matching columns in Notion.
   - In the new **Notion** tab, the user simply pastes:
     1. Their **Notion Internal Integration Token** (`ntn_...` or `secret_...`).
     2. Any **Notion Page URL** (or Database URL / 32-character ID) shared with that integration.
   - When the user taps **"Connect & Auto-Setup"**, the app inspects the ID:
     - If the ID points to a **Notion Page**, the app calls `POST https://api.notion.com/v1/databases` to automatically create a child database titled **`DueOrDie Tasks`** with the exact required schema, stores the resulting `databaseId`, and immediately syncs existing local tasks.
     - If the ID already points to a **Notion Database**, the app calls `PATCH https://api.notion.com/v1/databases/{database_id}` to ensure all required properties exist, stores the `databaseId`, and syncs existing local tasks.

3. **Dedicated `Notion` Tab in Bottom Navigation:**
   - Add a `NOTION` destination to `MainTab` in `AppNavigation.kt` alongside `Tasks`, `Progress`, and `Settings`.
   - The `Notion` tab provides:
     - Connection status badge (`Connected`, `Syncing…`, `Offline — queued`, `Not connected`, or actionable error).
     - Quick 2-field setup (`Integration Token` + `Notion Page or Database Link`) with one-tap **Paste** buttons and a concise 3-bullet collapsible guide explaining how to get an Internal Integration token from `notion.so/my-integrations` and share a page via `••• -> Connections`.
     - **Auto-Sync Tasks** master switch (enabled by default once connected).
     - Sync summary metrics (`Synced tasks`, `Queued / pending sync`, `Last synced time`) and a **"Sync Now"** button to retry/backfill at any time.
     - A **"Disconnect Notion"** action that clears stored credentials without deleting local tasks.

---

## 2. Exact Architecture & File-by-File Implementation Plan

### Step 1: Add Internet Permission
**File:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\AndroidManifest.xml`
- Add `<uses-permission android:name="android.permission.INTERNET" />` alongside the existing `<uses-permission>` declarations at the top of `AndroidManifest.xml`.

---

### Step 2: Extend `Task` Entity & Add Room Migration `MIGRATION_2_3`
**Files:**
- `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\data\model\Task.kt`
- `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\data\local\AppDatabase.kt`
- `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\data\local\TaskDao.kt`

1. **In `Task.kt`:**
   Add nullable/defaulted Notion tracking columns to `Task` so all existing call sites compile without changes:
   ```kotlin
   val notionPageId: String? = null,
   val notionSyncDirty: Boolean = true,
   val lastSyncedAtTimestamp: Long? = null
   ```
2. **In `AppDatabase.kt`:**
   - Increment `@Database(entities = [Task::class], version = 3, exportSchema = false)`.
   - Add `MIGRATION_2_3` using the same defensive `PRAGMA table_info(\`tasks\`)` check used in `MIGRATION_1_2`:
     ```kotlin
     val MIGRATION_2_3 = object : Migration(2, 3) {
         override fun migrate(db: SupportSQLiteDatabase) {
             val existingColumns = mutableSetOf<String>()
             db.query("PRAGMA table_info(`tasks`)").use { cursor ->
                 val nameIndex = cursor.getColumnIndex("name")
                 while (cursor.moveToNext()) {
                     if (nameIndex >= 0) {
                         existingColumns.add(cursor.getString(nameIndex))
                     }
                 }
             }
             if ("notionPageId" !in existingColumns) {
                 db.execSQL("ALTER TABLE `tasks` ADD COLUMN `notionPageId` TEXT")
             }
             if ("notionSyncDirty" !in existingColumns) {
                 db.execSQL("ALTER TABLE `tasks` ADD COLUMN `notionSyncDirty` INTEGER NOT NULL DEFAULT 1")
             }
             if ("lastSyncedAtTimestamp" !in existingColumns) {
                 db.execSQL("ALTER TABLE `tasks` ADD COLUMN `lastSyncedAtTimestamp` INTEGER")
             }
         }
     }
     ```
   - Register `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` in `AppDatabase.getDatabase(context)`.
3. **In `TaskDao.kt`:**
   - Update `updateTaskStatus` and `markOverdueTasks` queries so they also set `notionSyncDirty = 1`:
     ```kotlin
     @Query("UPDATE tasks SET status = :status, completedAtTimestamp = :completedAt, notionSyncDirty = 1 WHERE id = :id")
     suspend fun updateTaskStatus(id: Long, status: TaskStatus, completedAt: Long?)

     @Query("UPDATE tasks SET status = 'OVERDUE', notionSyncDirty = 1 WHERE status = 'PENDING' AND deadlineTimestamp <= :currentTimeMs")
     suspend fun markOverdueTasks(currentTimeMs: Long): Int
     ```
   - Add DAO methods for Notion sync:
     ```kotlin
     @Query("SELECT * FROM tasks ORDER BY createdAtTimestamp ASC")
     suspend fun getAllTasksOneShot(): List<Task>

     @Query("SELECT * FROM tasks WHERE notionSyncDirty = 1 OR notionPageId IS NULL ORDER BY createdAtTimestamp ASC")
     suspend fun getUnsyncedTasksOneShot(): List<Task>

     @Query("UPDATE tasks SET notionPageId = :notionPageId, notionSyncDirty = :dirty, lastSyncedAtTimestamp = :syncedAt WHERE id = :id")
     suspend fun updateNotionSyncMetadata(id: Long, notionPageId: String?, dirty: Boolean, syncedAt: Long?)
     ```

---

### Step 3: Build the Notion Preferences & Queue Store
**New File:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\data\notion\NotionPreferences.kt`

Persist Notion configuration and pending archive queue in `SharedPreferences` (`"due_or_die_notion_prefs"`):
- `integrationToken: String` (trimmed)
- `rawTargetInput: String` (the original URL or ID pasted by the user)
- `parentPageId: String?`
- `tasksDatabaseId: String?`
- `databaseTitle: String?`
- `isAutoSyncEnabled: Boolean` (default `true`)
- `lastSyncTimestamp: Long?`
- `lastSyncError: String?`
- `pendingArchivedNotionPageIds: Set<String>` — when a local `Task` with a non-null `notionPageId` is deleted from Room, record its `notionPageId` here so the background sync worker archives (`"archived": true`) the Notion page even if the device was offline at the moment of deletion.
- Include a pure utility function `extractNotionUuid(input: String): String?`:
  - Strips query parameters (`?v=...`) and fragments.
  - Matches a 32-character hexadecimal ID (with or without hyphens, e.g. `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` or `[0-9a-fA-F]{32}` at the end of a Notion slug URL) and formats it into canonical `8-4-4-4-12` lowercase UUID format.

---

### Step 4: Build the Zero-Dependency Notion REST API Client
**New File:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\data\notion\NotionApiClient.kt`

Use standard `java.net.HttpURLConnection` + `org.json.JSONObject` / `org.json.JSONArray` (no extra Gradle dependencies required) on `Dispatchers.IO`:
- **Base URL:** `https://api.notion.com/v1`
- **Headers:**
  - `Authorization: Bearer $token`
  - `Notion-Version: 2022-06-28`
  - `Content-Type: application/json; charset=utf-8`
- **Timeouts:** `connectTimeout = 15_000`, `readTimeout = 20_000`.

#### Required Notion Database Schema (`DueOrDie Tasks`)
When creating a database (`POST /v1/databases`) or ensuring properties on an existing database (`PATCH /v1/databases/{id}`):
- **Title property:**
  - If creating a new database, name the `"title"` property `"Task"`.
  - If linking an existing database, inspect its `properties` JSON object from `GET /v1/databases/{id}` to find whichever property has `"type": "title"` (e.g., `"Name"` or `"Task"`), and store that title property name in `NotionPreferences` so `DueOrDie` works seamlessly with *any* existing Notion database!
- **Additional properties (auto-created via `POST` or `PATCH`):**
  - `"Status"`: `select` with options `Pending` (blue), `Completed` (green), `Overdue` (red)
  - `"Due Date"`: `date` (ISO-8601 timestamp with local timezone offset, e.g. `2026-10-11T18:00:00+05:30`)
  - `"Completed At"`: `date` (ISO-8601 timestamp when `status == COMPLETED` and `completedAtTimestamp != null`, else `null`)
  - `"Outcome"`: `select` with options `On time` (green), `Completed late` (orange), `Pending` (gray), `Overdue` (red)
  - `"Focus Lock"`: `checkbox` (`task.isHostageModeEnabled`)
  - `"Notes"`: `rich_text` (`task.description`)
  - `"App Task ID"`: `number` (`task.id`)

#### Required API Operations in `NotionApiClient.kt`:
1. `suspend fun connectAndEnsureDatabase(token: String, rawPageOrDbInput: String): Result<NotionDatabaseInfo>`
   - Extracts the 32-char UUID via `extractNotionUuid(rawPageOrDbInput)`.
   - First tries `GET /v1/databases/$uuid`:
     - If HTTP 200: detects the existing `"title"` property name, sends `PATCH /v1/databases/$uuid` to ensure `"Status"`, `"Due Date"`, `"Completed At"`, `"Outcome"`, `"Focus Lock"`, `"Notes"`, and `"App Task ID"` exist, and returns `NotionDatabaseInfo(databaseId = uuid, titlePropertyName = detectedTitleProp, databaseTitle = ...)`.
   - If `GET /v1/databases/$uuid` returns 404 or `object_not_found` / invalid type, tries `GET /v1/pages/$uuid`:
     - If HTTP 200: searches or creates a child database via `POST /v1/databases` with `"parent": { "type": "page_id", "page_id": uuid }`, `"title": [{ "type": "text", "text": { "content": "DueOrDie Tasks" } }]`, and the schema above, then returns its `databaseId`.
     - If HTTP 401/403/404: returns a clear, human-readable error message explaining whether the token is invalid (`401`) or the Notion page hasn't been shared with the integration yet via `••• -> Connections` (`404`).
2. `suspend fun upsertTaskPage(token: String, databaseId: String, titlePropName: String, task: Task): Result<String>`
   - If `task.notionPageId == null`: calls `POST /v1/pages` with `"parent": { "database_id": databaseId }` and `"properties": buildTaskPropertiesJson(titlePropName, task)`. Returns the new `id` from the response.
   - If `task.notionPageId != null`: calls `PATCH /v1/pages/${task.notionPageId}` with `"properties": buildTaskPropertiesJson(titlePropName, task)` and `"archived": false`. If Notion returns `404` (e.g. user deleted the row in Notion), falls back to `POST /v1/pages` to recreate it and returns the new `id`.
3. `suspend fun archiveTaskPage(token: String, notionPageId: String): Result<Unit>`
   - Calls `PATCH /v1/pages/$notionPageId` with `{"archived": true}` (treats 404 as already deleted/success).

---

### Step 5: Build the Background Sync Coordinator & WorkManager Worker
**New Files:**
- `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\data\notion\NotionSyncManager.kt`
- `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\worker\NotionSyncWorker.kt`

1. **`NotionSyncManager`:**
   - Coordinates syncing all dirty/unsynced tasks (`taskDao.getUnsyncedTasksOneShot()`) and any `pendingArchivedNotionPageIds` in `NotionPreferences`.
   - Skips fixture tasks (`title.startsWith("[Example] ") || title == "Submit_quarterly_report"`).
   - Uses a `Mutex` so concurrent triggers (e.g. rapid task creation + manual sync) never create duplicate Notion rows for the same local `Task.id`!
   - After each task is created/updated in Notion, calls `taskDao.updateNotionSyncMetadata(id = task.id, notionPageId = remotePageId, dirty = false, syncedAt = System.currentTimeMillis())`.
   - Exposes `triggerBackgroundSync()` which both launches an immediate coroutine on an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.IO)` **and** enqueues a unique `OneTimeWorkRequestBuilder<NotionSyncWorker>()` with `Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()` (`ExistingWorkPolicy.REPLACE`) so if the user creates a task while offline, Android `WorkManager` automatically pushes it to Notion the moment network connectivity returns!
2. **Hook into `TaskRepositoryImpl.kt` (and `DeadlineCheckWorker.kt`):**
   - Inject `NotionSyncManager? = null` into `TaskRepositoryImpl` (wired in `DueOrDieApplication.kt`).
   - In `insertTask`: set `notionSyncDirty = true`, insert into `taskDao`, then call `notionSyncManager?.triggerBackgroundSync()`.
   - In `updateTask`: set `notionSyncDirty = true`, update `taskDao`, then call `notionSyncManager?.triggerBackgroundSync()`.
   - In `markAsCompleted`, `markAsPending`, `markAsOverdue`: update `taskDao`, then call `notionSyncManager?.triggerBackgroundSync()`.
   - In `deleteTask` and `deleteTaskById`: look up the task before deleting; if `task.notionPageId != null`, add `task.notionPageId` to `notionPreferences.addPendingArchivePageId(task.notionPageId)`, delete from Room, and call `notionSyncManager?.triggerBackgroundSync()`.
   - In `checkAndUpdateOverdueTasks`: if `updatedCount > 0`, call `notionSyncManager?.triggerBackgroundSync()` so tasks that transition to `OVERDUE` in the background also update their Notion status to `Overdue`.

---

### Step 6: Add the `Notion` Tab to UI & Navigation
**Files:**
- **New:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\ui\notion\NotionSyncViewModel.kt`
- **New:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\ui\notion\NotionSyncScreen.kt`
- **Modify:** `C:\Users\aksha\AndroidStudioProjects\DueOrDie\app\src\main\java\com\example\dueordie\ui\navigation\AppNavigation.kt`

1. **In `AppNavigation.kt`:**
   - Update `MainTab` enum:
     ```kotlin
     enum class MainTab(
         val label: String,
         val icon: ImageVector
     ) {
         TASKS("Tasks", Icons.Rounded.Checklist),
         SCREEN_TIME("Progress", Icons.Rounded.BarChart),
         NOTION("Notion", Icons.Rounded.Sync),
         RULES("Settings", Icons.Rounded.Settings)
     }
     ```
   - In the `Crossfade(targetState = selectedTab)` block, render `NotionSyncScreen(modifier = contentModifier)` when `activeTab == MainTab.NOTION`.
2. **In `NotionSyncScreen.kt` & `NotionSyncViewModel.kt`:**
   - Follow the calm Material 3 card layout used in `RulesAndBlocklistScreen.kt` and `GuiltTripScreen.kt`.
   - **Connection Status Header Card:**
     - Shows whether Notion is **Connected** (with database title + last sync time), **Syncing…**, or **Not Connected**.
   - **Credentials & One-Tap Setup Card:**
     - `OutlinedTextField` for **Integration Token** (`ntn_...` / `secret_...`) with password visibility toggle and a **Paste** trailing button.
     - `OutlinedTextField` for **Notion Page or Database Link** with a **Paste** trailing button.
     - Primary button: **"Connect & Auto-Setup Notion"** (shows a progress indicator while verifying/provisioning the database, then triggers an immediate sync of all non-example tasks).
     - Expandable **"How to get your token & page link (2 mins)"** helper section with 3 concise steps.
   - **Sync Controls & Accountability Status Card (visible when connected):**
     - Switch row: **"Auto-sync tasks to Notion"** (`"Automatically create, complete, and update tasks in Notion when changed in DueOrDie"`).
     - Stats row showing: **Synced tasks count**, **Pending sync count**, and **Last synced timestamp**.
     - Secondary buttons: **"Sync All Now"** and **"Disconnect"**.

---

## 3. Verification & Test Requirements

1. **Unit Tests (`app/src/test/java/com/example/dueordie/`):**
   - `NotionIdParserTest.kt`: Verify `extractNotionUuid` handles:
     - Full Notion page URLs (`https://www.notion.so/My-Workspace/DueOrDie-Accountability-1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d?pvs=4`)
     - Database URLs with view query params (`https://www.notion.so/1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d?v=...`)
     - Raw 32-char hex strings and hyphenated UUIDs
     - Invalid strings (returns `null`).
   - `NotionPayloadMapperTest.kt`: Verify a `Task` (pending, completed on time, completed late, overdue, with Focus Lock enabled) serializes into the exact Notion `properties` JSON structure expected by Notion API `2022-06-28`.
   - `NotionSyncRepositoryTest.kt`: Verify that `insertTask`, `markAsCompleted`, `updateTask`, and `deleteTask` mark tasks dirty / queue archive IDs and invoke the sync trigger while skipping `[Example] ` fixture tasks.
2. **Build & Existing Suite Verification:**
   - Run `./gradlew testDebugUnitTest assembleDebug` and ensure zero compilation warnings/errors and 100% passing unit tests.
