# Mora - User Requests and Specifications Log

This document tracks all requested changes, design specifications, and product decisions in simple language. It serves as the master reference for all future updates.

---

## 1. Top Bar and Settings Icon
- **Rounded Circular Container:** The Settings icon must always sit inside a clean circular container (`Surface(shape = CircleShape)`), not standalone.
- **Icon Style:** Uses a rounded gear icon (`Icons.Rounded.Settings`).
- **Pixel-Perfect Alignment:** The Settings button must sit at the exact same location on both the Tasks screen and the Balance screen (`42dp` size, anchored at `statusBarTop + 16dp` from the top, and `16dp` from the right edge). It must not shift or jump when switching tabs.

---

## 2. Bottom Navigation Dock and Add Tasks Button
- **Apple Music Dock Style:** The bottom dock consists of two side-by-side elements on a single horizontal row:
  1. The 2-tab navigation pill on the left (Tasks and Balance).
  2. The separate "Add tasks" action pill on the right.
- **No Plus Icon:** The add button must display the text "Add tasks" without any plus icon.
- **Theme-Aware Colors:** The dock container and active pill dynamically adapt to the active app theme:
  - **Light Mode:** Frosted white container (`#FFFFFF`) with deep forest jade active pill (`#2A4D3B`).
  - **Dark Mode:** Deep spruce container (`#1A2720`) with crisp cream active pill (`#FAF9F5`).
  - **AMOLED Mode:** Pitch spruce container (`#0E1611`) with luminous mint sage active pill (`#9CD2B0`).
- **Wobbly Interaction Physics:** Interacting with the "Add tasks" pill triggers a tactile jelly squash on press down and an elastic spring rebound upon release, accompanied by a haptic tick.
- **No Shadow Glitch:** The entrance animation uses clean horizontal expansion and fade-in without scale clipping, preventing the elevation shadow from flickering or glitching when returning to the Tasks tab.

---

## 3. Pull-to-Refresh Animation (Notion Sync)
- **No App Logo:** Removed the Notion icon completely.
- **iOS Liquid Arc Style:** Pulling down draws a fluid circular arc matching pull distance. Once triggered, it displays a smooth, silky spinning indicator with clean text ("Pull to sync", "Release to sync", "Syncing...").

---

## 4. Screen Time and Digital Wellbeing Accuracy
- **1-to-1 Match with Digital Wellbeing:** Fixed inaccurate daily usage calculations by switching from `INTERVAL_DAILY` to a strict calendar-day `UsageEvents` parser (`UsageStatsHelper.processEventRecords`). Screen time totals and per-app stats match Android Digital Wellbeing exactly.

---

## 5. Pop-ups and Dialogs (Liquid Glass Redesign)
All dialogs across the app and Settings page follow modern Apple / ColorOS 17 design standards:
- **Shape:** 28dp rounded corners (`RoundedCornerShape(28.dp)`).
- **Borders:** Subtle outline border (`1.dp` with 50% opacity).
- **Pill Buttons:** All action buttons (Save, Cancel, Close, OK, Done) use full pill shapes (`RoundedCornerShape(999.dp)`).
- **Pill Search Bars:** Search fields in app selectors use full capsule shapes (`999.dp`).
- **Updated Dialogs:**
  - Date and Time Pickers in `TaskDialog`
  - `HowItWorksDialog` and `PauseSettingsTourDialog` in Settings
  - `AccessibilityDisclosureDialog` and `PausePromptDialog` in Mindful Pause
  - `SelectBlockedAppsDialog` and `SelectPauseAppsDialog`

---

## 6. Two User Modes: Strict Mode vs. Casual Mode
Mora provides two distinct operating modes configured in Settings:

### Strict Mode (For Focused Commitment & High Accountability)
- **Deadlines Locked:** Once created, task deadlines and priorities cannot be pushed back or rescheduled.
- **Hold to Complete:** Tasks require a sustained press-and-hold (1200ms) with escalating heartbeat haptics.
- **Card Tap:** Tapping the task card expands notes only. No edit dialog and no reschedule options.
- **Task Deletion:** Locked or hidden to prevent quitting commitments.
- **Focus Lock Overdue Activation:** Overdue tasks immediately trigger full app lockouts over distracting apps.

### Casual Mode (For Normal Everyday Audience)
- **Flexible Rescheduling:** Tapping a card opens edit options and flexible timing adjustments.
- **Quick Complete:** Single tap on the squircle checkbox marks a task complete immediately.
- **Full Editing:** Users can edit titles, notes, and deadlines anytime.
- **Easy Deletion:** Swipe left to delete with undo.

### Task Card Appearance (Notes vs. No Notes)
- **Card Without Notes:** Clean, compact card layout displaying only title, priority tag, status label, and deadline countdown. No awkward empty whitespace or placeholder gaps.
- **Card With Notes:** Displays a clean 2-line description preview in muted secondary text, expandable to view complete instructions.

---

## 7. Daily Usage Limit for Chosen Apps
- **Cumulative Screen Time Cap:** Applies across all apps selected by the user to monitor or pause.
- **Default Limit:** 30 minutes total per day across all chosen apps combined.
- **Configurable Range:** User can adjust the limit in Settings: 15m, 30m (default), 45m, 1h, 1h 30m, up to a hard ceiling of 2 hours maximum (cannot exceed 2 hours).
- **Enforcement:** When today's combined usage of the chosen apps reaches the limit, opening any chosen app triggers the block / mindful pause intervention. In Strict Mode, they remain locked until midnight.

---

## 8. Microsoft Clarity Integration with Privacy Masking
- **Project ID:** `z4f5ufifl3`
- **SDK:** Official `com.microsoft.clarity:clarity-compose` integration for screen analytics, session heatmaps, and funnel drop-offs.
- **Masked Content (100% Private via `Modifier.clarityMask()`):**
  - Task titles and description notes.
  - Actual personal screen time numbers and personal app names.
- **Unmasked Content (Tracked for App Improvements):**
  - Tab navigation (Tasks vs Balance).
  - Add task button clicks and modal openings.
  - Settings page toggles, sliders, and tour progress.
  - Button taps and layout interaction heatmaps.

---

## 9. Dynamic Multi-Stage Mindful Pause Screen (Full-Screen Immersive Canvas)
The mindful pause overlay follows an elegant, choreographed multi-stage progression:
- **True Full-Screen Immersive Canvas:** 100% edge-to-edge canvas matching the app theme without floating card borders, cut-off corners, or background screens peeking through. Handled with `statusBarsPadding()` and `navigationBarsPadding()`.
- **Stage 1 (Reflection & Context):** 
  Fades in smoothly full screen with ambient edge caustics, presenting the target app icon, today's app usage stats (e.g. "Instagram - 22 mins today / 30 mins limit"), and the mindful prompt phrase.
- **Stage 2 (Liquid Breathing Mandala):** 
  Sacred liquid breathing mandala with 6 overlapping translucent organic petals (`BlendMode.Screen`), inward-floating starlight particles, luminous glowing central core orb, and lung-swell haptics. Phase text ("Breathe in", "Hold", "Breathe out", "Rest") and cycle progress ("Cycle 1 of 2") are rendered in crisp, high-contrast white text directly over the central orb.
- **Stage 3 (Conscious Choice - Fork in the Road):** 
  Once breathing concludes, displays a serene glowing checkmark orb ("Mindful") and presents two clear options:
  - Hero Button: "Step away & return to Tasks" with Home icon. Clean, bold pill button without any crushed badge or vertical line artifact.
  - Secondary Button: "Continue to [App]" with an elegant outlined pill shape and clear text contrast.
  - Strict Mode (Daily Cap Reached): No continue option; locked until tomorrow with an explanatory shield card.
- **Edge-to-Edge Preview Dialog Window:** Preview dialogs use `decorFitsSystemWindows = false`, `FLAG_LAYOUT_NO_LIMITS`, and transparent window background to prevent Android from wrapping the pause screen in a default dialog card.

---

## 10. Typography and Formatting Rule
- Strict ban on em dashes, en dashes, and bullet symbols across all UI copy and documentation. Plain hyphens (-) or slashes (/) are used exclusively.

---

## 11. Notification Scheduler Architecture
- **Task Deadlines (Exact Alarms):** Handled by `DeadlineAlarmScheduler` using Android `AlarmManager.setExactAndAllowWhileIdle()`. These fire at the exact second to enforce Focus Lock, even during deep Doze mode.
- **Daily Check-ins (9am, 1pm, 5pm, 9pm):** Managed by `WorkManagerScheduler` using periodic background jobs. Android batches these to preserve battery life, meaning notifications may arrive slightly approximate (+/- 10-20 minutes) if the device is resting.

---

## 12. Notification Polish and Dynamic Pause Overlay Fix
- **Single Check-in Notification Card:** Daily check-ins (Morning, Afternoon, Evening, Night) share a single notification ID (`1005`). A new check-in replaces the previous one rather than stacking 4 duplicate cards in the drawer.
- **Check-in Throttling:** Check-in notifications are guarded with a 3-hour minimum interval to prevent multiple check-ins from firing simultaneously when the device wakes.
- **Clean Notification Copy:** Removed bracketed priority tags like `[High]` from overdue alerts.
- **Strict Mode Compliance:** Overdue notifications in Strict Mode completely hide the `Reschedule (+1h)` button to prevent bypassing locked commitments.
- **No Duplicate Task Alerts:** Posting an overdue alarm immediately cancels any earlier "Due soon" notification for that same task.
- **Direct Dynamic Pause Window Overlay:** `OngoingNotificationService` hosts `DynamicMindfulPauseContent` directly in `WindowManager` using a dedicated lifecycle owner. This completely eliminates the old green circle layout and bypasses Android background activity launch restrictions.

---

## 13. Operating Mode Architecture & Design System Consistency (Audit Resolution)
- **Authoritative Policy Layer (`InteractionModePolicy`):** One single domain policy governs all mode decisions across UI, ViewModels, and Notification Receivers. UI asks the policy what to show, ViewModels validate every save/update, and receivers validate actions.
- **Default Mode:** New installs start safely in Casual Mode (`AppInteractionMode.CASUAL`).
- **Observable Mode State:** `OnboardingPreferences.interactionModeFlow` exposes a reactive `StateFlow` so the task list, settings screen, badges, and gestures update synchronously without stale states.
- **Strict Mode Bypass Elimination:**
  - `TaskActionReceiver` drops and cancels `ACTION_RESCHEDULE` in Strict Mode.
  - `TaskViewModel.saveTask` locks deadlines and priorities in Strict Mode via `TaskDomainRules.resolveEditedTask(canEditDeadline = false)`.
  - Right swipe on task cards is completely disabled in Strict Mode.
  - Card tap in Strict Mode expands task notes only; does not open edit dialog.
  - In `TaskDialog`, date, time, and priority pickers are locked for saved tasks in Strict Mode, accompanied by explanatory copy ("Deadlines are locked in Strict mode to preserve committed focus.").
- **Casual Mode Disentangled Gestures:**
  - Tapping the squircle checkbox immediately completes the task and does not trigger parent card clicks or open the editor.
  - Tapping the card body opens the task editor.
  - Right swipe opens the task editor.
  - Hold-to-complete timer is disabled in Casual Mode.
- **Standardized Hold-to-Complete:**
  - Standardized to 1200ms (`InteractionModePolicy.STRICT_HOLD_DURATION_MS`) regardless of task overdue status.
  - Screen reader / TalkBack support via `semantics`: provides `CustomAccessibilityAction("Complete task")` for full accessibility compliance.
- **Single Master Switch in Settings:**
  - Removed duplicate `Mindful pause` toggle in `RulesAndBlocklistScreen.kt`. A single master switch `Pause before apps` acts as the source of truth, with progressive disclosure for app selection, duration, and breathing settings.
  - Settings mode chips updated to 48dp minimum touch target height.
  - Factual, supportive copy replaces punitive phrasing.
- **Theme-Aware Status Tokens:**
  - Migrated task status and completion icons from hardcoded constants (`SemanticSuccessGreen`, `SemanticWarningAmber`) to theme-aware `MaterialTheme.colorScheme.primary` (Forest in Light, Sage in Dark/AMOLED) and `MaterialTheme.colorScheme.tertiary` for warnings.
- **Dead Photo-Proof Removal:**
  - Removed dead photo-proof UI elements (`TaskHomeScreen.kt`) and creation hooks while preserving database schema integrity.
- **Discreet Header Badge:**
  - Added a compact `[Casual]` / `[Strict]` badge in the top header beside "Tasks" to clearly indicate the active operating mode.
