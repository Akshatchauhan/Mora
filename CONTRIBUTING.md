# Contributing to Mora

Thank you for your interest in contributing to **Mora**! We welcome bug reports, design ideas, feature proposals, and pull requests that align with our core design philosophy of mindful, non-adversarial digital wellness.

---

## 🧭 Core Design Philosophy

Before submitting a PR or feature proposal, please keep our core tenets in mind:

1. **Non-Adversarial UX:** Mora does not treat the user like an unruly child. We avoid shame triggers, abrasive recurring nags, and rigid lockouts that trigger psychological reactance.
2. **Protected Intimacy:** Legitimate communication with partners and family must never be artificially blocked or frustrated.
3. **Adaptive Cognitive Friction:** Interventions must adapt dynamically based on daily limits and user intent.
4. **Liquid Glass Aesthetic:** Maintain the ColorOS 17 / iOS Liquid Glass design language (concentric squircles, 1px specular gradient strokes, Gaussian background blur).

---

## 🛠 Development Workflow

1. **Fork and Clone:**
   ```bash
   git clone https://github.com/your-username/mora.git
   cd mora
   ```

2. **Branching Strategy:**
   - Create a feature branch off `main`:
     ```bash
     git checkout -b feat/your-feature-name
     ```

3. **Code Style & Guidelines:**
   - Follow official Kotlin coding conventions and Jetpack Compose best practices.
   - Use meaningful Compose state naming (`state`, `onEvent`, `uiEffect`).
   - Keep design tokens centralized in `core/designsystem/`.

4. **Testing on Physical Devices:**
   - Test UI overlays on physical Android devices running Android 12+ to verify `FLAG_BLUR_BEHIND` and touch-event transparency.

5. **Commit Conventions:**
   We follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` New features (e.g. `feat: add partner anchor quote configuration`)
   - `fix:` Bug fixes (e.g. `fix: prevent overlay flicker on configuration change`)
   - `docs:` Documentation updates
   - `design:` UI/UX tokens and theme enhancements

---

## 📄 Licensing

By contributing to Mora, you agree that your contributions will be licensed under the project's [MIT License](LICENSE).
