# Mora Digital Wellness Evolution: Context, Codebase Architecture & Figma Redesign Guide

This document is the master briefing for GPT (or any AI agent) connected to Mora's active Figma canvas via Talk-to-Figma relay.

---

## 1. Directive for GPT: What Needs to Be Done

1. **Analyze the Android Codebase First:**
   Before modifying Figma or proposing code changes, inspect the core service, state machine, and usage tracking files in `C:\Users\aksha\AndroidStudioProjects\DueOrDie`.
2. **Improve and Fix the Mockups on Page 4:**
   The current mockups on Page 4 suffer from broken layout constraints (hardcoded dimensions, text clipping, bad label collisions, and cards floating awkwardly). You must redesign and update Page 4 with proper Figma AutoLayout, responsive text wrapping, elegant vertical rhythm, and authentic ColorOS 17 / iOS Liquid Glass styling.
3. **Respect the User's Product Direction & Full Context:**
   The features outlined in Section 3 are intentionally chosen based on one week of real-world field use on the user's daily device. Maintain these features without stripping them away.

---

## 2. Background Context: Why These Features Exist

The user tested Mora in the wild for one week on a real device (Realme GT 6). Three behavioral insights emerged:

1. **Tasks were ignored:** The user did not use task management; Mora was used exclusively for the Mindful Pause overlay on Instagram.
2. **Toll-gate habituation:** The existing 2-breath timer turned into an automated motor reflex. The thumb learned to wait mindlessly and tap through on autopilot, destroying mindfulness.
3. **Adversarial reactance:** The recurring 5-minute nag loops created feelings of "competing against the app," breeding frustration and rapid dismissal.
4. **Legitimate texting was penalized:** Repeatedly interrupting valid communication with the user's girlfriend (who the app is built for) conditioned fast bypasses.

---

## 3. Product Features Specification

### Feature 1: Per-App Daily Usage Budgets
- User specifies a daily allowance per distracting app (e.g. Instagram: 30 minutes, X: 15 minutes, YouTube: 45 minutes).
- Backed by Android `UsageStatsManager` for real-time daily foreground tracking.
- The pause screen dynamically adapts based on daily budget consumption (Under budget vs Over budget).

### Feature 2: Intentional Micro-Sessions (Protected Texting vs Browsing)
- **1-Tap Texting Pass:** `Just Texting / Replying (5m)`. Zero breath cycles, zero delay pass. Protects relationship communication and prevents conditioning motor bypass reflexes.
- **Mindful Browsing (5m):** For scrolling feeds. Requires intentional grounding (2 calm breath cycles).

### Feature 3: Prefrontal Cortex Typing Friction (Over-Budget State)
- Triggered when daily budget is exceeded (e.g. `32m / 30m Used`) or high open frequency.
- Replaces passive timers (which succumb to motor habituation) with active cognitive reflection.
- Asks: `"Why are you opening Instagram right now?"`
- Active typing input field with cursor: `"I am bored and avoiding finishing tasks"`.
- Typing engages conscious prefrontal cortex deliberation, immediately halting automatic motor reflexes.

### Feature 4: Non-Adversarial Impulse Decoupling
- Avoids punitive lockouts that trigger reactance.
- Primary alternative button: `Drink Water / Take a 5m Walk`.
- Secondary button: `Unlock 3m Conscious Session`.

### Feature 5: Conscious Check-Out Screen
- Replaces the abrasive 5-minute nag loop.
- When 5 minutes elapse inside Instagram, a calm frosted bottom sheet rises over the dimmed feed.
- Non-hostile check-out query: `"Did you finish what you came for? Consciously closing this session prevents falling into endless doomscrolling."`
- Choice 1: `Yes, I'm Done - Close App` (navigates to Home and logs a Mindful Win).
- Choice 2: `Need 2 More Mins for a Chat` (one-time clean extension for active conversations).

### Feature 6: Life Reclaimed & Mindful Wins Tracker
- Mora in-app hub metrics:
  - `Time Reclaimed This Week` (e.g. 1h 42m saved compared to baseline).
  - `Mindful Wins` (count of sessions consciously closed on time).
  - Streak tracking (e.g. 6-day streak).

### Feature 7: Partner Anchor Note
- Dedicated card on pause screens displaying an anchor note from the user's girlfriend:
  `"Remember our weekend plan, baby! Don't get lost in reels."`
- Configurable in Mora settings under `Partner Anchor Note` with attribution `Partner: Sarah`.
- Grounds the user in real-life relationships at the exact moment of dopamine impulse.

---

## 4. Codebase Architecture: Files to Analyze

GPT should review these specific files before designing or coding:

1. **Overlay Service & Window Management:**
   - Path: `app/src/main/java/com/example/dueordie/service/OngoingNotificationService.kt`
   - Handles `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
   - On Android 12+ / ColorOS, uses `FLAG_BLUR_BEHIND` and `setBlurBehindRadius(80)` to produce real system-level Gaussian frosted background blur.
2. **Zero-Delay App Detection:**
   - Path: `app/src/main/java/com/example/dueordie/service/MoraAccessibilityService.kt`
   - Detects target app foreground transitions instantly with zero battery polling.
3. **Mindful Pause State Machine:**
   - Path: `app/src/main/java/com/example/dueordie/data/model/MindfulPauseStateMachine.kt`
   - Manages pause durations, grace periods, re-interventions, and package configs.
4. **Daily Usage Stats Helper:**
   - Path: `app/src/main/java/com/example/dueordie/util/UsageStatsHelper.kt`
   - Queries `UsageStatsManager.queryUsageStats()` for daily foreground milliseconds per package.
5. **Preferences & Settings Storage:**
   - Path: `app/src/main/java/com/example/dueordie/util/OnboardingPreferences.kt`
   - Stores per-package settings, custom prompts, and pause feature state.
6. **Theme & Glassmorphism Definitions:**
   - Paths: `app/src/main/java/com/example/dueordie/ui/theme/Glassmorphism.kt` and `Color.kt`
   - Surface cards, obsidian backgrounds, and border tokens.

---

## 5. Design System: ColorOS 17 / iOS Liquid Glass

### Visual Principles
1. **Liquid Glass Material:**
   - Translucent obsidian surfaces with frosted background blur.
   - Glass surface fills: `#15241C` (75% to 85% opacity) and `#1A2E24` (80% opacity).
   - 1px specular refraction stroke: `#FFFFFF` (15% to 20% opacity) or subtle emerald (`#81C784` 25% opacity).
2. **Concentric Squircle Geometry:**
   - Corner smoothing: `0.6` (Apple squircle / ColorOS continuous curve).
   - Parent modal containers: 32dp corner radius.
   - Inner content cards: 20dp corner radius.
   - Action buttons and chips: 16dp to 24dp corner radius.
3. **Palette Constants:**
   - Obsidian Background: `#0C1410` / `#0D120F`
   - Glass Surface: `#15241C` / `#1A2E24`
   - Forest Green Accent: `#21362C` / `#1B5E20`
   - Vibrant Emerald Green: `#81C784` / `#A5D6A7`
   - Caution Amber: `#FFB74D` (used on overtime warnings)
   - Overtime Rose: `#E57373`
   - Primary Text: `#FFFFFF`
   - Secondary Text: `#9EAF9F`
   - Dim / Hint Text: `#6B8073`
4. **Zero AI Bloat:**
   - No sparkles, no neon cyan/purple gradients, no bot icons.
   - Organic, disciplined, tranquil, premium Nordic/Zen aesthetic.
5. **Strict Typography Constraint:**
   - Zero em dashes, zero en dashes, zero bullet points.
   - Use clean spacing, hierarchy, and simple hyphens or slashes only.

---

## 6. How to Connect to Figma & Update Page 4

### Session Parameters
- **Relay Port:** `3055`
- **WebSocket URL:** `ws://127.0.0.1:3055`
- **Active Channel:** `sti1qu8d`
- **Target Page:** Page 4 in the active Mora Figma file.

### Relay Health Check
To verify active connection:
```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:3055"
```

If the relay server needs to be restarted:
```powershell
node C:\Users\aksha\.gemini\antigravity\scratch\figma-relay\relay.mjs
```

### Executing Commands in Figma
You can execute commands via `eval_plugin_script` over WebSocket:

```javascript
import crypto from 'node:crypto';

const CHANNEL = 'sti1qu8d';
const ws = new WebSocket('ws://127.0.0.1:3055');

function sendCmd(command, params) {
  return new Promise((resolve, reject) => {
    const id = crypto.randomUUID();
    const timer = setTimeout(() => reject(new Error('Timeout')), 20000);
    ws.addEventListener('message', function handler(ev) {
      try {
        const data = JSON.parse(String(ev.data));
        if (data.type === 'broadcast' && data.message && data.message.id === id) {
          clearTimeout(timer);
          ws.removeEventListener('message', handler);
          resolve(data.message.result || data.message.error);
        }
      } catch (e) {}
    });
    ws.send(JSON.stringify({
      id,
      type: 'message',
      channel: CHANNEL,
      message: { id, command, params },
    }));
  });
}

ws.addEventListener('open', async () => {
  ws.send(JSON.stringify({ type: 'join', channel: CHANNEL, id: crypto.randomUUID() }));
  
  // Example: Run script inside Figma
  const result = await sendCmd('eval_plugin_script', {
    code: `
      // Figma plugin API code here
      return figma.currentPage.name;
    `
  });
  console.log(result);
});
```

---

## 7. Figma Layout Requirements to Fix on Page 4

When updating Page 4, apply these layout rules:
1. **Proper AutoLayout Everywhere:**
   - Every card and text block must have `layoutMode = "VERTICAL"` or `"HORIZONTAL"`.
   - Set text elements to `layoutAlign = "STRETCH"` with explicit fixed widths or auto-height wrapping so text never runs off the screen.
   - Use line heights matching font sizes (e.g. fontSize 14 -> lineHeight 20).
2. **Proper Bottom Sheet Anchoring:**
   - The Conscious Check-Out card must sit anchored at the bottom of the device viewport (`y = 852 - sheetHeight - 20`), rather than floating ambiguously in the center.
3. **No Overlapping Badges:**
   - Badges and chips must sit in horizontal rows with `itemSpacing` (e.g. 8dp), rather than colliding with title text.
4. **Generous Spacing & Padding:**
   - Use 16dp to 20dp internal card padding.
   - Leave adequate breathing room so the liquid glass surfaces feel spacious and premium.
