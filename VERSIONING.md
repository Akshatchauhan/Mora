# Mora versioning

Mora uses Semantic Versioning for `versionName` and a separate, monotonically increasing Android `versionCode`.

## Current baseline

- `versionName`: `1.1.0`
- `versionCode`: `2`
- App ID: `com.intellectexplorer.mora`

## Bump rules

- **Patch** (`1.1.0` → `1.1.1`): bug fixes, stability, accessibility, or small visual refinements that do not add a notable capability.
- **Minor** (`1.1.0` → `1.2.0`): new backward-compatible features or substantial improvements.
- **Major** (`1.x` → `2.0.0`): breaking behavior, data, or settings changes that require a migration or materially change how existing users use Mora.
- For development builds, use a prerelease suffix such as `1.2.0-alpha.1`, then increment the prerelease number (`alpha.2`, `beta.1`, and so on).

Every APK distributed for installation must have a `versionCode` greater than the last distributed APK, even when it is a prerelease or patch. Keep `versionCode` unchanged for local rebuilds that are not being distributed. Update both values together in `app/build.gradle.kts` before publishing or sharing a new build, and note the change in release notes.

The next patch after the current baseline is `1.1.1` / code `3`; the next feature release is `1.2.0` / code `3`. Use only one of those for the next distributed build, then continue incrementing `versionCode` from there.
