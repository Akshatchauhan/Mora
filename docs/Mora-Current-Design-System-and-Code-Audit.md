# Mora: Current Design System Rules and Code Consistency Audit

**Reviewed:** 29 October 2026  
**Scope:** Current Android implementation in `app/src/main/java`, existing `DESIGN_LANGUAGE.md`, task interactions, operating modes, settings, themes, and pause entry flow. This is an audit and design-system specification; it does not change app code. It does not include a live device visual QA pass.

## Executive summary

Mora already has a coherent foundation: forest green and sage, warm cream, dark spruce, true AMOLED black, shared Material 3 typography, and a calm task-and-pause concept. The current build does not apply that foundation consistently. Several screens contain local palettes and typography overrides, and Strict/Casual rules are spread between composables, preferences, notification creation, and receivers.

The highest-risk issue is behavioral: **Strict mode is enforced mainly by the visible screen, not by the task and notification action layer.** Some routes still allow editing or rescheduling. There are also retained photo-proof fields and UI even though that feature was dropped, and two settings rows toggle the same Mindful Pause preference.

The guiding rule should be: **one Mora visual system; two clear interaction contracts; one authoritative policy layer.** Modes should differ in commitment mechanics, copy, and a restrained mode cue. They should not become separate palettes or separate products.

## 1. Product and mode principles

### Product promise

Mora helps a person choose the next useful task, keep a realistic deadline, and return attention to it with respectful prompts. It is supportive and direct, never punitive. Every interruption must explain why it appeared and what the user can do next.

### Shared app identity

- Keep the same layout, navigation, component shapes, type scale, status colors, and three appearance themes in both modes.
- Do not create a separate Casual theme and Strict theme. Casual and Strict are **operating modes**, not appearance themes.
- A mode change may use one subtle cue: a compact `Casual` or `Strict` badge and a small accent treatment in the current green/sage family. Do not recolor the page, navigation, error states, or completion states.
- Show the selected mode somewhere persistent enough to prevent surprise, such as a small header badge on Tasks and a clear selection in Settings. Do not repeat the badge on every task card.
- Explain the effect before the user changes modes. When switching into Strict, state which existing tasks become locked and what actions remain possible.

### Mode contract

| Action | Casual | Strict |
|---|---|---|
| Complete a task | One tap; show a short Undo affordance | Deliberate press-and-hold with visible progress; expose an equivalent accessible action for assistive technology |
| Open a task | Tap opens the editor/details | Tap opens read-only details; editing is unavailable through every route while locked |
| Change a deadline | Simple, visible reschedule action | No routine snooze action; provide a clearly named, deliberate exception path if the user needs to correct a commitment |
| Mindful Pause | Breath, then a clear Continue choice | Same respectful pause; stronger blocking only when the user explicitly enabled Focus Lock and its conditions are met |
| Tone | Reassuring and flexible | Clear and commitment-focused, without shame or threats |
| Visual styling | Shared Mora components with a soft existing sage cue | Shared Mora components with a deeper existing forest cue |

Strict should increase deliberate choice, not remove agency or make the app unsafe. A focus feature must not trap the person away from essential communication or create a punishment for a missed deadline. Any stricter limit needs a clear explanation, scope, expiry, and a user-controlled emergency route.

### Mode implementation contract

- Put decisions in one policy layer, for example `InteractionModePolicy`, with named answers such as `canEditTask`, `canReschedule`, and `completionGesture`.
- The UI asks the policy what to show; the ViewModel/domain layer validates every edit and completion; notification receivers validate again before applying an action.
- Do not infer mode independently in multiple composables from `SharedPreferences`.
- Make the current mode observable to Compose (`StateFlow` or equivalent). The mode selector, task list, task checkbox, and notification refresh must all update from that same source.
- Choose the initial mode explicitly during onboarding. If no choice is made, use the less restrictive default and explain the choice. The current preference fallback silently selects Strict.

## 2. Visual foundations

### Color roles

Use the existing values in `ui/theme/Color.kt` through `MaterialTheme.colorScheme`. Do not paste palette values into screen composables.

| Role | Light | Dark | AMOLED | Use |
|---|---|---|---|---|
| Main canvas | Warm cream `#F7F7F1` | Deep pine `#121A15` | Pure black `#000000` | Page background |
| Surface | White `#FFFFFF` | Spruce `#1B261F` | Lifted near-black `#0D1310` | Cards, sheets, controls |
| Primary | Forest `#315C47` | Sage `#9CD2B0` | Sage `#9CD2B0` | Primary actions, selected state |
| Supporting content | Slate sage `#667269` | Pale slate `#B0C2B5` | Pale slate `#B0C2B5` | Supporting text and icons |
| Error | `error` scheme role | `error` scheme role | `error` scheme role | Destructive action and genuinely late/error states |
| Warning | `tertiary` scheme role | `tertiary` scheme role | `tertiary` scheme role | Time-sensitive attention, not task importance |

Rules:

- Use `primary` / `primaryContainer` for selected and active states, not as a general decoration color.
- Reserve `error` for overdue, destructive, or failed states. High task priority is importance, not an error; it should not look like a failure.
- Use theme-aware success colors. A fixed dark green such as `SemanticSuccessGreen` may disappear against the dark/AMOLED surfaces; success needs a light-on-dark and dark-on-light mapping.
- Status meaning must not depend on color alone. Pair it with a short label or shape/icon and preserve readable contrast.
- Keep mode accents within the existing green/sage family. They must never replace semantic status colors.
- AMOLED canvas stays true black. Lifted cards and navigation can use the existing near-black surface, but avoid introducing a brown/blue/white dock that looks like another theme.
- System bars, dialogs, sheets, and overlays must use the same theme mapping as the screen underneath.

### Typography and line length

Use the shared Sans Serif Material type scale in `ui/theme/Type.kt` without per-screen font family changes.

| Purpose | Token | Rule |
|---|---|---|
| Page title | `headlineMedium` or `headlineSmall` | One prominent title; avoid manually restating its size/weight |
| Section heading | `titleMedium` | Clear, compact, sentence case |
| Task title | `titleMedium` | Highest emphasis inside a task card; up to two lines |
| Main body / dialog text | `bodyLarge` or `bodyMedium` | Explain the action in short sentences |
| Due time and helper text | `bodySmall` | Secondary only; never the sole place for a critical rule |
| Button/chip label | `labelLarge` | Short, action-oriented wording |

Rules:

- Do not override typography sizes inline unless a genuine component variant needs it and the variant is reusable.
- Avoid all-caps field labels and dense letter-spaced microcopy. Prefer `Task title`, `Due date`, and `Priority`.
- Keep one task title and one due/status summary readable before secondary details. Clamp long descriptions, but allow the title and dialog content to grow with system font size.
- Test at 1.3–2.0 font scale. Never make a control depend on a fixed height that clips translated or enlarged text.

### Spacing, shape, and touch

Use a 4dp spacing rhythm and standardize components rather than tuning each screen independently.

- Screen gutters: 20–24dp; use one value per screen family.
- Related rows: 12–16dp apart.
- Section groups: 20–24dp apart.
- Control interior padding: at least 8dp.
- Compact controls: 12dp corners; cards: 16–20dp; sheets/prominent panels: 24dp top corners.
- Interactive targets: at least 48 x 48dp, including icon buttons and chips. Give the visible icon room without shrinking the hit area.
- Button width follows its label and importance. Primary actions can fill the available width when that is the clearest choice; secondary actions should not compete by matching weight and color.
- Prefer a single surface with dividers to several nested outlined cards. Use borders and tonal surface changes before shadows.
- Do not horizontally clip mode choices, duration chips, or settings controls without a visible scroll cue.

### Icons and labels

- Use one consistent rounded Material icon family and optical size.
- Icons support a text label; they do not replace a setting name or action label.
- Decorative icons have no accessibility description. Action icons describe the action, not the visual (`Edit task`, `Choose apps`).
- Use one icon per concept consistently. Do not use a pause icon for both a master feature and a separate setting if both appear together.
- An icon must not imply that an action exists when its click is disabled by mode.

## 3. Core component and screen rules

### Task list

- Keep a single strong header, one primary `Add task` action, then concise filters and the task list.
- A task card’s order is: task title; optional status/priority; due date and time; optional short description. Do not show multiple competing badges.
- Casual due copy should be human and stable (`Today · 5:00 PM`, `Tomorrow · 9:00 AM`). Strict may show a countdown as secondary information, but it must not tick the whole screen every frame or make every task feel urgent.
- A deadline changing from upcoming to overdue should update the same status treatment everywhere: task card, filters/count, notification, Progress/Balance screen, and Focus Lock decision.
- `High priority` is separate from `Overdue`. Use distinct visual meaning.
- Keep delete safety independent from completion and editing. If deletion is hidden behind a preference, explain how to enable it and provide an accessible alternative to a long hold.
- Empty states use one clear next action and do not duplicate the same Add action in multiple equally strong buttons.

### Task editor

- Group fields in this order: title, due date/time, priority, then optional behavior such as Focus Lock.
- Use sentence-case labels and theme-aware field borders, date/time pickers, and sheet handle.
- Clearly state when a field is locked and why. Do not merely disable a date picker without an explanation.
- Strict-mode restrictions must be validated before saving, not only hidden from the screen.
- Do not show removed or unsupported features. Photo-proof UI/model hooks should be removed or isolated behind a deliberate data migration plan, not left as an apparent available option.

### Settings and progressive disclosure

Organize Settings around the user’s jobs:

1. **Tasks and commitments** — operating mode, completion and deadline behavior.
2. **Mindful Pause** — master switch, chosen apps, duration/prompt, repeat and grace.
3. **Focus Lock** — separate opt-in, eligible tasks/apps, limits and emergency/expiry behavior.
4. **Notifications and sound** — deadline reminders, overdue alarm, pause ping, vibration.
5. **Appearance and motion** — System/Light/Dark/AMOLED, reduced motion.
6. **Sync and help** — Notion, permissions, tutorials, diagnostics.

Rules:

- One preference has one visible control. A status summary may mirror its value, but must not also act as a second toggle.
- Show a one-line explanation with a setting; put advanced options inside a clearly labeled expansion.
- A master switch is distinct from a feature-detail row. Use a switch for on/off and a chevron for navigation; do not use two different rows as switches for the same state.
- Ask for sensitive permission when the person enables the feature that needs it, explain the exact permission, and report actual status. Never call Usage Access plus overlay permission “Accessibility Service.”
- Group the optional/advanced items below the simple path. Do not expose every tuning choice on first view.

### Mindful Pause and Focus Lock

- The pause screen states the selected app and a short, nonjudgmental prompt.
- The opening phrase remains visible for the configured initial hold (current implementation: 2.5 seconds), then transitions into the breathing cycle. Keep Continue unavailable until the configured cycles finish; show the active phase with text and animation.
- Motion communicates inhale/exhale and state change. Keep transitions short (about 180–260ms); the breath itself is intentionally slow. Respect the system reduced-motion setting by replacing scale/particle motion with a static phase indicator.
- Sound and vibration are optional, independently configurable, and never required to understand the phase.
- Focus Lock is opt-in and task-linked. Say what activates it, what event releases it, and what to do if the deadline is wrong. Do not use “penalty,” “hostage,” or “zero-bypass” language in product copy.
- A gentle Mindful Pause must never silently become a Focus Lock screen. Permission loss should disable the feature clearly and safely.

### Motion and feedback

- Use motion only to explain an interaction: selected state, sheet transition, task completion, pause phase, or a permission/state change.
- Standard UI transitions: 180–260ms, one easing family; no idle shimmer or constant decorative movement.
- Completion: one brief scale/bounce and one haptic. Strict hold progress has one continuous visual indicator and a restrained haptic ramp.
- Avoid confetti, heartbeat escalation, or sound by default. Users can disable sound and haptics separately.
- Long presses need visible progress, an announced accessibility action, and a cancellation path. Do not make a long hold the only way to complete or delete a task.

### Product language

- Voice: calm, concise, factual, and agency-preserving.
- State the consequence before the action: `This task is locked in Strict mode. Change the commitment?`
- Avoid identity labels (`for perfectionists`), shame (`you failed`), threats, and punishment framing (`penalty`).
- Use one term for each concept everywhere: `Overdue` or `Late` (choose one), `Focus Lock`, `Mindful Pause`, `Complete`, `Reschedule`, and `Due`.
- Button labels use verbs: `Save task`, `Continue to Instagram`, `Return to tasks`, `Turn on pause`.

## 4. Code consistency findings

Priority describes user impact. File paths are relative to the project root.

### P1 — Strict mode can be bypassed through alternate action routes

- In `app/src/main/java/com/example/dueordie/ui/task/TaskHomeScreen.kt:790–800`, a right swipe calls `onEditClick()` without checking the operating mode. The tap path checks Casual at approximately `:819–822`, so Strict has an alternate edit route.
- `TaskViewModel.openEditTaskDialog()` (`ui/task/TaskViewModel.kt:225`) and `saveTask()` (`:243+`) do not enforce a Strict deadline lock. `TaskDialog` only disables the date/time picker for completed tasks (`ui/task/TaskDialog.kt` around `:230–286`), not for Strict-mode tasks.
- `NotificationHelper` hides the `Reschedule (+1h)` action while Strict (`util/NotificationHelper.kt:204–227`), but `TaskActionReceiver` applies `ACTION_RESCHEDULE` without checking the current mode (`service/TaskActionReceiver.kt:54–62`). A stale notification action can still write the deadline after the mode changes.
- **Correction:** centralize mode policy; validate at the ViewModel/domain and receiver boundaries. Refresh/cancel old notification actions when mode changes. A disabled visual control is not enforcement.

### P1 — Casual mode may open the editor when the completion control is tapped

`TaskHomeScreen.kt` attaches a tap/hold `pointerInput` handler to the whole task card (`:758+`) and calls `onEditClick()` for a short Casual-mode tap (`:819–822`). The completion checkbox is a nested clickable (`:1244–1265`). The parent handler does not first exclude consumed child taps. This creates a strong risk that tapping the checkbox both completes and opens the editor.

- **Correction:** make card navigation and completion separate semantic targets. Use explicit click handlers and a dedicated hold gesture area; do not infer card taps from a parent pointer stream that also contains child controls.
- **Verification required:** exercise Casual checkbox taps, Strict holds, swipes, and TalkBack on a device after correction.

### P1 — Mode state is read inconsistently and can go stale

`HoldToCompleteCheckbox` captures mode with `remember(context)` (`TaskHomeScreen.kt:1224+`), while the card tap reads SharedPreferences again inside the gesture (`:819`). `appInteractionMode` is a plain SharedPreferences property (`util/OnboardingPreferences.kt:59–61`) with no observable flow. A settings change can therefore update one interaction path while another keeps the old value until recomposition/recreation.

- **Correction:** expose mode as observable state and pass one mode/policy value down the task screen. Do not fetch preferences from input callbacks.

### P1 — Strict completion semantics do not match accessibility semantics

The Strict checkbox tap only ticks haptics (`TaskHomeScreen.kt:1261–1265`), while the icon still advertises `Mark task complete` (`:1285`). Completion is instead attached to a long hold on the whole card (`:758–780`). Screen readers cannot perform that gesture through the checkbox’s advertised action, and the label promises an action that a tap does not perform.

- **Correction:** expose an explicit accessible completion action with clear confirmation/hold semantics; include an accessible alternative, and update state descriptions and labels to match the actual action.

### P1 — A new install silently starts in Strict mode

`AppInteractionMode.fromStorage()` falls back to `STRICT` (`util/OnboardingPreferences.kt:22–29`), and the preference getter also defaults to Strict (`:59–60`). The onboarding code found in this review does not ask the user to choose a mode. This means users can encounter hidden editing/completion restrictions without knowingly enabling them.

- **Correction:** make the choice explicit during onboarding or use Casual as the safe initial behavior; explain what Strict changes before activation.

### P1 — Settings has two controls for one Mindful Pause switch

`RulesAndBlocklistScreen.kt:441` presents a clickable `Mindful pause` status row that toggles `isMindfulPauseEnabled`; `:465–471` then presents a `Pause before apps` switch backed by the same value. The user sees two controls for one setting.

- **Correction:** keep one master switch. Make the next row a non-toggle destination (`Choose apps`) or a summary with a chevron. Preserve a single clear source of truth.

### P2 — Dropped photo-proof feature remains wired through the product

Photo-proof state is still in `data/model/Task.kt:17–18`; `TaskDialog` carries `requirePhotoProof` and `photoPath` (`ui/task/TaskDialog.kt:72–88, 158–160`); `TaskViewModel` still saves, deletes, and captures proof (`ui/task/TaskViewModel.kt:248–285, 386+`); and the task card still displays proof-related UI (`ui/task/TaskHomeScreen.kt:1141–1201`). This creates a mismatch between product scope and code behavior, and can surface old proof data to users.

- **Correction:** remove the creation/capture UI and dead ViewModel paths. Before removing persisted fields or files, decide how to preserve or clean existing records safely; do not silently delete user data as part of a UI cleanup.

### P2 — Mode behavior is more limited than the Settings description claims

The mode selector says Strict means locked deadlines, hold-to-complete, notes view only, and a Focus Lock “penalty”; Casual means tap-to-complete, flexible rescheduling, and easy editing (`RulesAndBlocklistScreen.kt:317–325`). The task card shows the same countdown/visual treatment in both modes, both modes share the long-hold path, and Strict’s restrictions have the alternate-route holes above. The main screen does not visibly indicate the active mode.

- **Correction:** make the description exactly match the actual contract; show one small mode cue; implement a deliberately small set of reliable behavioral differences. Remove “perfectionists” / “penalty” framing.

### P2 — Long-press timing varies by deadline state and is not mode-specific

`TaskHomeScreen.kt:687` uses 1.4 seconds for a non-overdue card and 2.4 seconds for an overdue one. The parent card’s hold completion logic is active for both operating modes (`:758–780`), even though Strict is described as hold-to-complete and Casual as tap-to-complete.

- **Correction:** make the gesture policy explicit per mode, use one consistent hold duration, show it before the action completes, and keep an accessible alternative. Overdue status should not make completion harder.

### P2 — Theme tokens are bypassed in several major screens

- The bottom dock defines a second full palette in `ui/navigation/AppNavigation.kt:151–180`, including values that differ from `Color.kt` and the Material scheme.
- `ui/guilttrip/GuiltTripScreen.kt` contains many fixed light-only greens, creams, and browns (representative examples around `:172–185`, `:572–590`, `:731–778`, `:1155–1161`). They can diverge from Light/Dark/AMOLED surfaces and contrast.
- Task card success and warning use fixed constants (`ui/task/TaskHomeScreen.kt` around `:989–1000`), which do not adapt automatically to dark/AMOLED contrast.
- `TaskDialog` uses `Color.LightGray` for the drag handle (`ui/task/TaskDialog.kt:117`), so it does not follow theme surfaces.
- **Correction:** use semantic `MaterialTheme.colorScheme` roles; add a small number of custom semantic tokens only where the scheme cannot express meaning. Add theme previews/screenshots for every surface, including the navigation dock and dialogs.

### P2 — Permission terminology does not match the actual check

`PermissionHelper.isAccessibilityServiceEnabled()` (`util/PermissionHelper.kt:86–88`) returns `isPauseAndFocusAccessGranted()` (`:55–57`), which is Usage Access plus Display Over Other Apps. A separate `isRealAccessibilityServiceEnabled()` exists at `:90+`. Settings/onboarding callers use the misleading “accessibility service enabled” property, while `OngoingNotificationService` polls UsageStats every 750ms (`service/OngoingNotificationService.kt:224–243`).

- **Correction:** name and describe the permission actually used. Decide whether the product uses Usage Access polling, the real Accessibility Service, or a defined fallback; do not treat the concepts as interchangeable. Keep the disclosure, settings status, and runtime implementation in sync.

### P3 — Type, shape, and target-size rules drift between screens

- `TaskDialog` overrides the shared title to 26sp and uses 11sp all-caps field labels (`ui/task/TaskDialog.kt:141–143, 183–187`).
- Settings mode chips request a 46dp minimum height (`ui/permissions/RulesAndBlocklistScreen.kt:338, 353`), below the 48dp target rule.
- Task cards use 20dp corners while the existing `DESIGN_LANGUAGE.md` specifies 16dp cards. Task dialog uses 28dp top corners; both can work, but the system has no shared shape tokens to keep screens aligned.
- **Correction:** define shared spacing, shape, and typography tokens and migrate high-traffic components first. Keep touch targets at 48dp minimum and test large text.

### P3 — Internal naming retains retired or punitive concepts

The active code still has internal packages/classes such as `ui/guilttrip/GuiltTripScreen.kt` and `ui/hostage/HostageBlockActivity.kt`; user-facing nudge text includes “Focus Lock active” (for example `util/MoraNudgeEngine.kt:100–102`). Internal names are not necessarily visible, but they make it easier for old concepts and copy to leak back into the product.

- **Correction:** use neutral feature names in new code and product strings. Rename old packages only as a separate, behavior-preserving cleanup.

## 5. Recommended implementation order

1. **Close behavior bypasses:** put operating-mode policy in one place; block Strict editing/rescheduling in ViewModel/domain and notification receiver; fix Casual tap target overlap.
2. **Make mode state reliable and understandable:** observable preference, explicit initial choice, truthful copy, one visible mode cue.
3. **Remove stale product scope:** remove photo-proof UI and behavior while preserving existing user data intentionally.
4. **Repair Settings information architecture:** remove duplicate Mindful Pause toggle; group advanced options progressively.
5. **Unify theme implementation:** migrate navigation dock, Balance/Progress, task status colors, sheets, and system overlays to semantic tokens; verify Light, Dark, AMOLED.
6. **Finish accessibility and sizing:** 48dp controls, screen-reader semantics for Strict completion, font scaling, reduced-motion behavior.
7. **Verify interactions and themes:** tests for mode policy and receiver enforcement; device pass for checkbox taps, card swipes, Strict holds, permission loss, app switching, notification actions, large text, and all three themes.

## 6. Done criteria

- Casual and Strict differ in a small, documented set of mechanics; the same UI components and brand palette remain recognizable.
- There is no edit or reschedule route that bypasses Strict policy, including stale notifications.
- A mode switch updates every visible interaction immediately.
- Casual completion does not also open the editor; Strict completion is discoverable and operable with assistive technology.
- A fresh install is not silently placed into a restrictive mode.
- One setting has one control; dropped features are not still presented as active.
- Light, Dark, and AMOLED use the same semantic hierarchy with readable status colors.
- Buttons and touch targets meet the 48dp minimum; text scales without clipping.
- Permission prompts accurately name the permission and mechanism in use.
