# MORA: MASTER PRODUCTION DESIGN SYSTEM & COMPLETE MULTI-THEME APP BLUEPRINT

> **MISSION-CRITICAL DIRECTIVE FOR GPT:**
> This document is the **final, authoritative production release specification** for the complete Mora digital wellness application.
> You are commanded to design the **entire app ecosystem from scratch** directly inside the Figma Desktop file using the active Talk-to-Figma relay.
> This is not a set of partial mockups. This covers **every screen, state, modal, and all 3 official themes (Dark, AMOLED, and Light)**. Every screen must be production-ready, structured with native Figma AutoLayout, perfectly proportioned, and faithful to the ColorOS 17 / iOS Liquid Glass aesthetic.

---

## 1. Execution Setup: How to Talk Directly to Figma Desktop

Do **not** use Chrome, browser automation, or web scraping tools. Figma is running as a native Windows desktop app.

The **Talk to Figma Pro** plugin is already open and running inside Figma Desktop, connected to a local WebSocket relay server on your machine.

### Active Session Parameters
- **Relay Port:** `3055`
- **WebSocket URL:** `ws://127.0.0.1:3055`
- **Active Channel:** `sti1qu8d`
- **Target Page in Figma:** Page 4 (or create a dedicated "Mora Production v1.0" page)
- **Local Relay Script Path:** `C:\Users\aksha\.gemini\antigravity\scratch\figma-relay\relay.mjs`

### Verification Command
Run this in PowerShell to confirm the relay and channel are active:
```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:3055"
```
You should see `{ status: "ok", port: 3055, channels: [..., "sti1qu8d"] }`.

### Programmatic Execution Template (Node.js)
```javascript
import crypto from 'node:crypto';

const CHANNEL = 'sti1qu8d';
const ws = new WebSocket('ws://127.0.0.1:3055');

function runFigma(code) {
  return new Promise((resolve, reject) => {
    const id = crypto.randomUUID();
    const timer = setTimeout(() => reject(new Error('Figma timed out')), 40000);

    const handler = (event) => {
      try {
        const data = JSON.parse(String(event.data));
        if (data.type === 'broadcast' && data.message && data.message.id === id) {
          clearTimeout(timer);
          ws.removeEventListener('message', handler);
          resolve(data.message.result || data.message.error);
        }
      } catch (e) {}
    };

    ws.addEventListener('message', handler);
    ws.send(JSON.stringify({
      id,
      type: 'message',
      channel: CHANNEL,
      message: {
        id,
        command: 'eval_plugin_script',
        params: { code }
      }
    }));
  });
}
```

---

## 2. Codebase Architecture: Files You Must Analyze First

Before generating the Figma design, analyze the existing Android codebase at `C:\Users\aksha\AndroidStudioProjects\DueOrDie`:

1. **Overlay Service & Window Blurring:**
   - `app/src/main/java/com/example/dueordie/service/OngoingNotificationService.kt`
   - Handles `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
   - Uses `FLAG_BLUR_BEHIND` and `setBlurBehindRadius(80)` on Android 12+ / ColorOS 15/17.
2. **Zero-Delay Foreground App Detection:**
   - `app/src/main/java/com/example/dueordie/service/MoraAccessibilityService.kt`
   - Listens to `TYPE_WINDOW_STATE_CHANGED` events to trigger overlays with zero latency.
3. **Mindful Pause State Machine:**
   - `app/src/main/java/com/example/dueordie/data/model/MindfulPauseStateMachine.kt`
   - Manages pause durations, grace periods, re-interventions, and package configs.
4. **Daily Usage Stats Helper:**
   - `app/src/main/java/com/example/dueordie/util/UsageStatsHelper.kt`
   - Queries `UsageStatsManager` for daily foreground milliseconds per package.
5. **Preferences & Settings Storage:**
   - `app/src/main/java/com/example/dueordie/util/OnboardingPreferences.kt`
   - Stores per-package preferences, custom prompts, and pause feature state.
6. **Existing Design Tokens & Glass Surfaces:**
   - `app/src/main/java/com/example/dueordie/ui/theme/Glassmorphism.kt`
   - `app/src/main/java/com/example/dueordie/ui/theme/Color.kt`

---

## 3. Product Context: 1-Week Real-World Field Test Insights

Mora was field-tested for one full week on a Realme GT 6 (Android 16 / ColorOS). The results revealed fundamental behavioral friction:

1. **Tasks Were Sidelined:** The user completely ignored task management features and used Mora exclusively for the Instagram mindful pause screen.
2. **Toll-Gate Motor Habituation:** The initial 2-breath timer became an automated muscle reflex. The user's thumb learned to wait passively and tap through on autopilot, completely bypassing mindfulness.
3. **Adversarial Reactance:** The recurring 5-minute nag loops created resentment. The user felt like they were "competing with the app" rather than benefiting from it.
4. **Legitimate Texting Was Penalized:** Repeatedly interrupting valid communication with the user's girlfriend (who the app is built for) conditioned rapid bypasses.

### Core Solution Architecture
- **Daily App Usage Budgets per App** (Honest, segmented tracking via `UsageStatsManager`).
- **Adaptive Tiered Friction** (Gentle reminders under budget; cognitive prefrontal check-ins over budget).
- **Intentional Micro-Sessions** (`Just Texting / Replying (5m)` 1-tap pass vs `Mindful Browsing (5m)`).
- **Prefrontal Cortex Typing Friction** (Typing a genuine reflection halts automatic motor autopilot).
- **Conscious Check-Out Screen** (Smooth frosted bottom sheet at 5 minutes; replaces the hostile nag loop).
- **Life Reclaimed & Mindful Wins Tracker** (Positive reinforcement metrics).
- **Personalized Partner Anchor Note** (Grounding message from their partner to anchor real-life commitments).

---

## 4. The 3 Official Mora Themes

Every screen in the core app must be designed in all 3 official themes:

### Theme 1: Dark Theme (Signature Obsidian Forest)
- Canvas / Window Background: `#0D1511`
- Recessed Background: `#090F0C`
- Glass Surface: `#14201A` (75% to 85% opacity)
- Card Background: `#18261F`
- Primary Typography: `#ECF2EE`
- Muted Typography: `#8FA698`
- Subtle Typography: `#62786B`
- Accent Emerald: `#7ED4A1`
- Accent Soft (Chips/Pills): `#1E3529`
- Accent Mid: `#4E9A6F`
- Caution Amber: `#E59348` (Soft: `#2C2014`)
- Danger / Late Rose: `#E06C64` (Soft: `#2C1615`)
- Specular Border: `#24382D` (1px)
- Bottom Dock Background: `#16241D`
- Bottom Dock Active Pill: `#7ED4A1` (Active Text: `#0B1710`)

### Theme 2: AMOLED Theme (Pitch Black)
- Canvas / Window Background: `#000000`
- Recessed Background: `#060A08`
- Glass Surface: `#09100C` (85% opacity)
- Card Background: `#0E1813`
- Primary Typography: `#F2F7F4`
- Muted Typography: `#88A091`
- Subtle Typography: `#566C5E`
- Accent Emerald: `#8EE2B0`
- Accent Soft (Chips/Pills): `#14261C`
- Accent Mid: `#54A878`
- Caution Amber: `#EDA058` (Soft: `#24180C`)
- Danger / Late Rose: `#E8766E` (Soft: `#24100F`)
- Specular Border: `#1B2B22` (1px)
- Bottom Dock Background: `#0C1410`
- Bottom Dock Active Pill: `#8EE2B0` (Active Text: `#050B08`)

### Theme 3: Light Theme (Editorial Warm Linen)
- Canvas / Window Background: `#F2F0E8`
- Recessed Background: `#E4E2D8`
- Glass Surface: `#FAF9F5` (85% opacity)
- Card Background: `#FFFFFF`
- Primary Typography (Ink): `#12241B`
- Muted Typography: `#52665B`
- Subtle Typography: `#788A80`
- Accent Forest Green: `#1B3B2B`
- Accent Soft (Chips/Pills): `#DCE8E0`
- Accent Mid: `#2E5E47`
- Caution Amber: `#B85D19` (Soft: `#F7E8DC`)
- Danger / Late Rose: `#A63A32` (Soft: `#F9E6E4`)
- Specular Border: `#D4D2C6` (1px)
- Bottom Dock Background: `#12241B`
- Bottom Dock Active Pill: `#FAF9F5` (Active Text: `#12241B`)

---

## 5. Visual Language: ColorOS 17 / iOS Liquid Glass Design System

1. **Liquid Glass Material:**
   - Multi-layered frosted glass with high-radius Gaussian background blur.
   - Translucent obsidian/linen glass fills with subtle 1px specular refraction borders.
   - Drop shadows: Soft, diffuse ambient elevation. Every `DROP_SHADOW` in Figma **must** include `blendMode: "NORMAL"`.
2. **Concentric Squircle Geometry:**
   - Continuous corner smoothing `0.6` (Apple squircle / ColorOS continuous curve).
   - Viewport containers: 44dp corner radius.
   - Floating modal sheets: 32dp corner radius.
   - Inner content cards: 18dp to 20dp corner radius.
   - Buttons and action chips: 14dp to 24dp corner radius.
3. **Zero AI Bloat Policy:**
   - Absolutely no sparkles, no neon cyan/purple gradients, no bot icons, no robotic chat widgets.
   - Clean, serene, disciplined, organic Nordic/Zen digital wellness aesthetic.
4. **Strict Typography Constraint:**
   - Zero em dashes (`—`), zero en dashes (`–`), zero bullets (`•`, `·`, `●`, `◦`).
   - Use clean whitespace, clear visual hierarchy, and simple hyphens (`-`) or slashes (`/`).

---

## 6. Full Inventory of Screens to Build

You are commanded to design the complete screen matrix laid out in clear categorized rows on Page 4 in Figma. Device viewport: 393 x 852 px per screen, 60px horizontal spacing.

---

### ROW 1: Habit Intervention & System Overlay System (The Daily Touchpoints)

1. **Screen 1A: Adaptive Liquid Glass Launch Gate (Under Budget)**
   - Displayed immediately upon opening Instagram when usage is within daily budget (e.g. 18m / 30m).
   - Blurred Instagram feed underneath (`FLAG_BLUR_BEHIND` preview with 40px blur).
   - Floating frosted glass sheet (361 x 720 dp), drag handle, app icon, `ADAPTIVE PAUSE` status pill.
   - Segmented daily budget gauge: `18m / 30m Used` (60% emerald track, 40% empty track).
   - Partner Anchor card: Heart icon + `"Remember our weekend plan, baby! Don't get lost in reels."`
   - Intention selection:
     - `Just Texting / Replying (5m)` (1-Tap Pass: zero delay pass for girlfriend communication).
     - `Mindful Browsing (5m)` (Requires 2 calm breath cycles).
   - Primary button: `Put Phone Down / Step Away`.

2. **Screen 1B: Prefrontal Cortex Friction Screen (Over-Budget / High Frequency)**
   - Displayed when daily budget is exceeded (e.g. 32m / 30m) or high open frequency.
   - Warm amber ambient refraction glow behind frosted obsidian glass.
   - Alert chip: `BUDGET OVERTIME: 32m / 30m`.
   - Concentric breathing squircle widget: pulsing breath rings with cadence `Inhale 4s ... Hold 4s ... Exhale 4s`.
   - Prefrontal typing card: `"Why are you opening Instagram right now?"` with interactive input: `"I am bored and avoiding finishing tasks|"`.
   - Non-adversarial impulse decoupling button: `Drink Water / Take a 5m Walk`.
   - Ghost button: `Unlock 3m Conscious Session`.

3. **Screen 1C: Conscious Check-Out Screen (Session Complete)**
   - Rises after 5 minutes of Instagram use (replaces the abrasive 5-minute nag loop).
   - Anchored floating frosted bottom sheet over dimmed Instagram.
   - Reflection query: `"Did you finish what you came for? Consciously closing this session prevents falling into endless doomscrolling."`
   - Mindful Win banner: `+1 Mindful Win Pending | Streak: 6 Days (14 wins logged this week)`.
   - Hero button: `Yes, I'm Done - Close App` (locks app for 15-minute focus window).
   - Grace button: `Need 2 More Mins for a Chat` (clean one-time extension).

4. **Screen 1D: Pause Motion & Tutorial Guide**
   - Tutorial and preview screen explaining how the mindful pause works, how the accessibility service triggers without battery drain, and how to calibrate breathing cycles.

---

### ROW 2: Core In-App Screens (Dark Theme - Signature Obsidian Forest)

1. **Screen 2A: Tasks Main Screen (Active State)**
   - Header: "Mora", subtitle "Stay intentional", pull-down Notion sync gesture indicator.
   - Filter segmented rail: `All (12)`, `Active (3)`, `Late (1)`, `Done (8)`.
   - Task list:
     - Task 1: "Review pull requests for Mora v2.4" (Priority green stripe, Due 5:00 PM, `+1h` squircle pill).
     - Task 2: "Evening gym workout / Leg day" (Due 7:30 PM, `+1h` pill).
   - Bottom FAB: Emerald squircle with `+` icon for new tasks.
   - Bottom dock navigation: Pill `Tasks` (active), `Mindful Hub`, `Progress`, `Settings`.

2. **Screen 2B: Tasks Screen (Late / Overdue State)**
   - Filter `Late` active.
   - Mindful Recovery Banner: Soft amber/rose frosted banner: "1 task needs rescheduling. Take a breath and adjust your deadline."
   - Overdue task card: Soft rose accent stripe, "Finalize client proposal deck", text: "Overdue by 2 hours", quick `+1h` and `+3h` reschedule chips.

3. **Screen 2C: Tasks Screen (Done / Completed State)**
   - Filter `Done` active.
   - Completed tasks ledger: Checked items with strike-through styling, completion timestamps ("Completed today at 2:15 PM"), daily completion score.

4. **Screen 2D: Tasks Screen (Empty State)**
   - Displayed when no tasks exist.
   - Concentric breathing mark illustration in center.
   - Headline: "No tasks yet", description: "Add your first task with a due date or load example tasks."
   - Action buttons: `Add task` (emerald) and `See example tasks`.

5. **Screen 2E: Mindful Hub Screen (Wellness Core)**
   - Dedicated primary tab inside Mora.
   - Hero Analytics Card: `1h 42m Life Reclaimed This Week` (`+38% vs last week`, `14 Mindful Wins`, `82% Texting vs Scroll ratio`).
   - Per-App Daily Budgets:
     - Instagram: Daily slider set to 30 mins, current `18m / 30m` consumption, adaptive typing toggle.
     - X (Twitter): 15m budget row.
     - YouTube: 45m budget row.
   - Partner Anchor Note Editor: Editable text field showing Sarah's message, attribution pill `Partner: Sarah`, explanation note.
   - Bottom dock navigation: Pill `Mindful Hub` (active).

6. **Screen 2F: Progress & Analytics Screen**
   - Time reclaimed weekly bar chart.
   - Mindful wins trend line.
   - Focus ratio breakdown (Intentional sessions vs mindless opens).
   - Bottom dock navigation: Pill `Progress` (active).

7. **Screen 2G: Settings & Rules Screen (Grouped Overview)**
   - Group 1: Mindful Pause Engine (Accessibility Service toggle [ON], Pause duration 8s, Re-intervention 10m).
   - Group 2: Habit Security (Prevent Task Deletion toggle [ON], Selected apps: Instagram, X, YouTube).
   - Group 3: Cloud & Sync (Notion two-way sync [Connected], Pull-down sync [ON]).
   - Group 4: About Mora (`v2.4.0` Build 5, "Handcrafted for digital discipline and intentional relationships").
   - Bottom dock navigation: Pill `Settings` (active).

---

### ROW 3: Core In-App Screens (AMOLED Theme - Pitch Black `#000000`)

Replicate the key screens in pure pitch black (`#000000`) with high-contrast emerald borders:
1. **Screen 3A: Tasks Active (AMOLED)**
2. **Screen 3B: Tasks Late (AMOLED)**
3. **Screen 3C: Mindful Hub (AMOLED)**
4. **Screen 3D: Progress (AMOLED)**
5. **Screen 3E: Settings (AMOLED)**

---

### ROW 4: Core In-App Screens (Light Theme - Editorial Warm Linen `#F2F0E8`)

Replicate the key screens in warm linen/paper (`#F2F0E8`) with deep forest ink (`#12241B`):
1. **Screen 4A: Tasks Active (Light)**
2. **Screen 4B: Tasks Late (Light)**
3. **Screen 4C: Mindful Hub (Light)**
4. **Screen 4D: Progress (Light)**
5. **Screen 4E: Settings (Light)**

---

### ROW 5: Bottom Sheets, Dialogs & Pickers

1. **Screen 5A: Task Edit & Reschedule Bottom Sheet**
   - Anchored bottom sheet (361 x 540 dp), drag handle, title "Edit Task".
   - Task title input field, quick reschedule chips (`+1 Hour`, `+3 Hours`, `Tomorrow Morning`, `Next Week`), priority selector (`High`, `Medium`, `Low`), action buttons (`Save Changes` and `Mark Done`).
2. **Screen 5B: Select Blocked Apps Picker Dialog**
   - Full list of installed apps with checkboxes, search bar, and usage badges.
3. **Screen 5C: Accessibility Permission Onboarding Guide**
   - Step-by-step interactive sheet demonstrating how to enable Mora Accessibility Service in Android Settings for zero-delay pause triggering.

---

## 7. Strict Figma AutoLayout Execution Rules

To ensure every screen looks impeccable:

1. **AutoLayout on Every Container:**
   - Every frame, card, row, and column must have `layoutMode = "VERTICAL"` or `"HORIZONTAL"`.
   - Never place raw text nodes with hardcoded coordinates inside AutoLayout cards.
2. **Text Wrapping & Explicit Line Heights:**
   - Set every text element that can wrap to `layoutAlign = "STRETCH"` or set an explicit width with auto-height sizing.
   - Always specify explicit `lineHeight` matching the `fontSize` (e.g. fontSize 14 -> lineHeight 20, fontSize 20 -> lineHeight 26).
3. **Pills & Chips (No Collisions):**
   - Badges and status pills must sit in horizontal rows with `itemSpacing: 8` and `counterAxisAlignItems: "CENTER"`.
   - Never overlap chips onto card title text.
4. **Bottom Sheet Positioning:**
   - When placing a bottom sheet, set its y-coordinate to `852 - sheetHeight - 20` so it sits anchored against the bottom edge of the device screen with natural margins.
5. **Shadows:**
   - Every `DROP_SHADOW` in `effects` array **must** contain `blendMode: "NORMAL"`.
6. **Corner Smoothing:**
   - Apply `node.cornerSmoothing = 0.6` to all frames with corner radius > 12 to produce genuine iOS / ColorOS squircle curves.

---

## 8. Next Action for GPT

Connect to `ws://127.0.0.1:3055` on channel `sti1qu8d`, execute `eval_plugin_script`, and build the complete multi-theme production artboards directly on Page 4 in Figma.
