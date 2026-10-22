# Mora design language

## Product character

Mora is a quiet, capable companion for planning and intentional app use. It should feel grounded, clear, and respectful of the user's attention. Keep the existing forest green, sage, warm cream, dark forest, and true AMOLED palette. Avoid gradients, glass effects, neon accents, decorative AI motifs, and playful urgency.

## Visual foundations

- **Color:** use `MaterialTheme.colorScheme` semantic roles in screens. `primary` is the main action and selected state; `primaryContainer` is a soft selected surface; `surface` is for cards and controls; `background` is the page canvas; `onSurfaceVariant` is supporting text; `outline` and `outlineVariant` are borders; `error` is destructive/error; `tertiary` is warning. Do not paste palette hex values into screen code.
- **Themes:** light, dark, and AMOLED must preserve the same hierarchy and contrast. AMOLED uses pure black canvas with a subtly lifted surface. Never make a navigation or dialog surface permanently light or dark.
- **Typography:** use the shared Sans Serif Material typography in `ui/theme/Type.kt`. Screen titles use headlineMedium/Small; section headings use titleMedium; controls use labelLarge; body copy uses bodyLarge/Medium; metadata uses bodySmall. Avoid one-off font families, arbitrary sizes, and dense all-caps labels. Keep line length comfortable and let accessibility font scaling work.
- **Spacing:** use a 4dp rhythm: 8dp within a control, 12–16dp between related rows, 20–24dp between sections, and 24dp screen gutters. Group settings by task and progressively reveal optional details.
- **Shapes:** use 12dp for compact controls, 16dp for cards, and 20–24dp for prominent panels/docks. Use borders and subtle tonal differences instead of stacked shadows.
- **Touch:** interactive controls should provide at least a 48dp target. Give each navigation destination equal width, keep labels on one line, and keep button widths proportional to their labels. Do not horizontally clip choices without a visible scroll cue.

## Components and hierarchy

- Each screen has one clear page title and one primary action. Secondary actions are outlined or text buttons.
- Cards group related settings; avoid nesting multiple bordered cards inside one another.
- Toggles sit directly beside the setting they control, with a short label and one supporting line at most.
- Use chips for mutually exclusive, compact choices. Use a menu or expandable section for long or advanced choices.
- Empty states explain the next useful action and show one primary CTA. Avoid redundant counters or duplicate CTAs that repeat the same action.
- Dialogs state the decision plainly, explain consequences briefly, and use specific action labels such as “Turn on pause” or “Leave pause.”
- Navigation stays visually quiet and theme-aware. Selected state uses the primary container; unselected destinations use supporting content color.

## Motion and sound

Use motion to explain a state change, not decorate idle screens. Standard transitions should be short and calm (about 180–260ms), with gentle easing. Respect Android's reduced-motion setting. Breathing animation is the intentional exception: keep it slow, predictable, and user-controlled; reveal Continue only after the configured breathing cycles. Keep sound optional, quiet, and paired with an accessible visual state.

## Product language

Mora's voice is calm, direct, non-judgmental, and agency-focused. Use short sentences and familiar words. Describe what the feature does, then offer a choice. Prefer “Take a breath before opening Instagram” and “You can continue when you're ready” over guilt, threats, moral judgments, or claims about addiction. Avoid shaming labels such as “guilt trip,” “hostage,” “lazy,” or “failed.” Never blame the user for dismissing a prompt. Keep implementation details and tutorial prose out of ordinary screens.

## Code conventions

- App UI is Kotlin with Jetpack Compose and Material 3. `DueOrDieTheme` is the source of truth for theme colors and shared typography.
- Screen composables should consume semantic `MaterialTheme.colorScheme` and `MaterialTheme.typography` values, not recreate global colors or styles locally.
- Shared controls belong in reusable Compose components when the same behavior or visual pattern appears on more than one screen.
- Keep domain and scheduling logic outside composables; UI should render state and emit user actions.
- Before adding a new token, check whether a Material 3 semantic role already expresses it. Add a custom token only for a real product meaning that cannot be represented by the scheme.

## Review checklist

1. Does the screen look correct in light, dark, and AMOLED modes?
2. Is the primary action obvious without competing duplicate actions?
3. Are titles, section headings, body text, and helper text using the shared type scale?
4. Are controls readable at larger system font sizes and large enough to tap?
5. Are animation and sound optional, brief, and respectful of reduced-motion/accessibility settings?
6. Does the copy preserve the user's dignity and choice?
