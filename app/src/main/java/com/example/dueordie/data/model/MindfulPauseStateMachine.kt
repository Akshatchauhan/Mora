package com.example.dueordie.data.model

enum class PauseFeatureStatus(val displayLabel: String) {
    OFF("Off"),
    NEEDS_PERMISSION("Needs permission"),
    ON("On")
}

data class MindfulPauseConfig(
    val isPauseEnabled: Boolean = false,
    val isAccessibilityServiceEnabled: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val pauseDurationSeconds: Int = 8,
    val promptText: String = "Take a mindful pause before continuing.",
    val isReInterventionEnabled: Boolean = true,
    val reInterventionIntervalMinutes: Int = 10,
    val disabledReInterventionPackages: Set<String> = emptySet(),
    val returnGraceSeconds: Int = 30,
    val selfPackage: String = "com.example.dueordie",
    val ignoredPackages: Set<String> = DEFAULT_IGNORED_PACKAGES,
    val packageDurationSeconds: Map<String, Int> = emptyMap(),
    val packageReInterventionMinutes: Map<String, Int> = emptyMap()
) {
    fun getDurationForPackage(packageName: String): Int {
        return packageDurationSeconds[packageName] ?: pauseDurationSeconds
    }

    fun getReInterventionMinutesFor(packageName: String): Int {
        return packageReInterventionMinutes[packageName] ?: reInterventionIntervalMinutes
    }

    val featureStatus: PauseFeatureStatus
        get() = when {
            !isPauseEnabled -> PauseFeatureStatus.OFF
            !isAccessibilityServiceEnabled -> PauseFeatureStatus.NEEDS_PERMISSION
            else -> PauseFeatureStatus.ON
        }

    fun isReInterventionEnabledFor(packageName: String): Boolean {
        return isReInterventionEnabled && !disabledReInterventionPackages.contains(packageName)
    }

    companion object {
        val DEFAULT_IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "android"
        )
    }
}

sealed interface MindfulPauseState {
    data object Disabled : MindfulPauseState
    data object Ready : MindfulPauseState

    data class PauseVisible(
        val targetPackage: String,
        val startedAtElapsedMs: Long,
        val durationSeconds: Int,
        val promptText: String,
        val isFocusLock: Boolean = false,
        val focusLockTaskTitle: String? = null,
        val focusLockOverdueMinutes: Long = 0L,
        val isCooldownActive: Boolean = false,
        val cooldownRemainingMinutes: Int = 0
    ) : MindfulPauseState

    data class ActiveInTarget(
        val targetPackage: String,
        val allowedAtElapsedMs: Long,
        val nextReInterventionElapsedMs: Long?,
        val isFocusLockSession: Boolean = false
    ) : MindfulPauseState

    data class GraceWindow(
        val lastTargetPackage: String,
        val graceExpiresAtElapsedMs: Long,
        val isFocusLockSession: Boolean = false
    ) : MindfulPauseState
}

/**
 * Deterministic, monotonic-clock state machine for **Pause before apps** (Mindful Pause)
 * and overdue-task **Focus Lock** interventions.
 *
 * State flow:
 * `Disabled → Ready → PauseVisible → ActiveInTarget → GraceWindow → Ready`
 */
class MindfulPauseStateMachine(
    initialConfig: MindfulPauseConfig = MindfulPauseConfig()
) {
    var config: MindfulPauseConfig = initialConfig
        private set

    var state: MindfulPauseState = computeBaseState(initialConfig)
        private set

    fun updateConfig(newConfig: MindfulPauseConfig, elapsedRealtimeMs: Long) {
        val wasEnabled = config.featureStatus == PauseFeatureStatus.ON
        config = newConfig
        val isNowEnabled = newConfig.featureStatus == PauseFeatureStatus.ON

        if (!isNowEnabled) {
            val current = state
            if (current is MindfulPauseState.PauseVisible && current.isFocusLock) {
                return
            }
            if (current is MindfulPauseState.ActiveInTarget && current.isFocusLockSession) {
                state = current.copy(nextReInterventionElapsedMs = null)
                return
            }
            state = MindfulPauseState.Disabled
            return
        }
        if (!wasEnabled && state is MindfulPauseState.Disabled) {
            state = MindfulPauseState.Ready
            return
        }

        val current = state
        if (current is MindfulPauseState.ActiveInTarget) {
            if (!newConfig.selectedPackages.contains(current.targetPackage) && !current.isFocusLockSession) {
                state = MindfulPauseState.Ready
            } else {
                val shouldScheduleReIntervention =
                    newConfig.featureStatus == PauseFeatureStatus.ON &&
                        newConfig.selectedPackages.contains(current.targetPackage) &&
                        newConfig.isReInterventionEnabledFor(current.targetPackage)
                val nextReIntervention = if (shouldScheduleReIntervention) {
                    val intervalMins = newConfig.getReInterventionMinutesFor(current.targetPackage)
                    current.allowedAtElapsedMs + (intervalMins * 60_000L)
                } else {
                    null
                }
                state = current.copy(nextReInterventionElapsedMs = nextReIntervention)
                if (nextReIntervention != null && elapsedRealtimeMs >= nextReIntervention) {
                    state = buildPauseState(current.targetPackage, elapsedRealtimeMs)
                }
            }
        } else if (current is MindfulPauseState.PauseVisible && !current.isFocusLock) {
            if (!newConfig.selectedPackages.contains(current.targetPackage)) {
                state = MindfulPauseState.Ready
            }
        }
    }

    /**
     * Handles a window-state transition to [packageName] at monotonic [elapsedRealtimeMs].
     * Also accepts an optional [focusLockDecision] if overdue-task Focus Lock applies.
     */
    fun onForegroundPackageChanged(
        packageName: String?,
        elapsedRealtimeMs: Long,
        focusLockDecision: FocusLockDecision? = null
    ): MindfulPauseState {
        if (packageName.isNullOrBlank()) return state
        if (packageName == config.selfPackage) return state

        // Transient system UI (notification shade, volume/permission dialogs) should not
        // falsely end an active target session or self-trigger an overlay.
        if (isTransientSystemWindow(packageName)) {
            return state
        }

        // Check overdue-task Focus Lock first if a qualifying decision is present
        if (focusLockDecision?.shouldIntervene == true && focusLockDecision.blockingTask != null) {
            val current = state
            if (current is MindfulPauseState.PauseVisible &&
                current.isFocusLock &&
                current.targetPackage == packageName
            ) {
                return state
            }
            if (current is MindfulPauseState.ActiveInTarget &&
                current.targetPackage == packageName
            ) {
                return state
            }
            if (current is MindfulPauseState.GraceWindow &&
                current.lastTargetPackage == packageName &&
                elapsedRealtimeMs < current.graceExpiresAtElapsedMs
            ) {
                state = buildActiveState(
                    targetPackage = packageName,
                    elapsedRealtimeMs = elapsedRealtimeMs,
                    isFocusLockSession = current.isFocusLockSession
                )
                return state
            }
            state = MindfulPauseState.PauseVisible(
                targetPackage = packageName,
                startedAtElapsedMs = elapsedRealtimeMs,
                durationSeconds = config.pauseDurationSeconds,
                promptText = config.promptText,
                isFocusLock = true,
                focusLockTaskTitle = focusLockDecision.blockingTask.title,
                focusLockOverdueMinutes = focusLockDecision.overdueMinutes,
                isCooldownActive = focusLockDecision.isCooldownActive,
                cooldownRemainingMinutes = focusLockDecision.cooldownRemainingMinutes
            )
            return state
        }

        val isPauseFeatureOn = config.featureStatus == PauseFeatureStatus.ON
        if (!isPauseFeatureOn) {
            val current = state
            if (current is MindfulPauseState.ActiveInTarget && current.isFocusLockSession) {
                if (packageName == current.targetPackage) {
                    return state
                }
                val graceMs = config.returnGraceSeconds * 1000L
                state = if (graceMs > 0L) {
                    MindfulPauseState.GraceWindow(
                        lastTargetPackage = current.targetPackage,
                        graceExpiresAtElapsedMs = elapsedRealtimeMs + graceMs,
                        isFocusLockSession = true
                    )
                } else {
                    MindfulPauseState.Disabled
                }
                return state
            }
            if (current is MindfulPauseState.GraceWindow && current.isFocusLockSession) {
                if (elapsedRealtimeMs >= current.graceExpiresAtElapsedMs) {
                    state = MindfulPauseState.Disabled
                }
                return state
            }
            state = MindfulPauseState.Disabled
            return state
        }

        val isTargetApp = config.selectedPackages.contains(packageName) &&
            !config.ignoredPackages.contains(packageName)

        when (val current = state) {
            is MindfulPauseState.Disabled -> {
                state = if (isTargetApp) {
                    buildPauseState(packageName, elapsedRealtimeMs)
                } else {
                    MindfulPauseState.Ready
                }
            }

            is MindfulPauseState.Ready -> {
                if (isTargetApp) {
                    state = buildPauseState(packageName, elapsedRealtimeMs)
                }
            }

            is MindfulPauseState.PauseVisible -> {
                // Suppress duplicate window events while the same intervention is active
                if (packageName == current.targetPackage) {
                    return state
                }
                // User switched away to launcher or another app while pause was visible
                state = if (isTargetApp) {
                    buildPauseState(packageName, elapsedRealtimeMs)
                } else {
                    MindfulPauseState.Ready
                }
            }

            is MindfulPauseState.ActiveInTarget -> {
                if (packageName == current.targetPackage) {
                    // Check if re-intervention is due on this event
                    val dueAt = current.nextReInterventionElapsedMs
                    if (dueAt != null && elapsedRealtimeMs >= dueAt) {
                        state = buildPauseState(packageName, elapsedRealtimeMs)
                    }
                    return state
                }

                // Target app left for launcher or another app: cancel re-intervention and enter grace window
                val graceMs = config.returnGraceSeconds * 1000L
                if (isTargetApp) {
                    // Switched directly from one selected target app to a different selected target app
                    state = buildPauseState(packageName, elapsedRealtimeMs)
                } else if (graceMs > 0L) {
                    state = MindfulPauseState.GraceWindow(
                        lastTargetPackage = current.targetPackage,
                        graceExpiresAtElapsedMs = elapsedRealtimeMs + graceMs,
                        isFocusLockSession = current.isFocusLockSession
                    )
                } else {
                    state = MindfulPauseState.Ready
                }
            }

            is MindfulPauseState.GraceWindow -> {
                if (isTargetApp) {
                    if (packageName == current.lastTargetPackage &&
                        elapsedRealtimeMs < current.graceExpiresAtElapsedMs
                    ) {
                        // Returned within intentional app-switch grace period: skip pause
                        state = buildActiveState(
                            targetPackage = packageName,
                            elapsedRealtimeMs = elapsedRealtimeMs,
                            isFocusLockSession = current.isFocusLockSession
                        )
                    } else {
                        // Grace expired or opened a different selected target app
                        state = buildPauseState(packageName, elapsedRealtimeMs)
                    }
                } else if (elapsedRealtimeMs >= current.graceExpiresAtElapsedMs) {
                    state = MindfulPauseState.Ready
                }
            }
        }

        return state
    }

    /**
     * User tapped "Continue" after the mindful breath or Focus Lock reminder to reveal the target app.
     */
    fun onContinueClicked(elapsedRealtimeMs: Long): MindfulPauseState {
        val current = state as? MindfulPauseState.PauseVisible ?: return state
        state = buildActiveState(
            targetPackage = current.targetPackage,
            elapsedRealtimeMs = elapsedRealtimeMs,
            isFocusLockSession = current.isFocusLock
        )
        return state
    }

    /**
     * User tapped "Leave / Not now" to exit without opening the target app.
     * Does not start a grace window so the next open will pause again.
     */
    fun onLeaveClicked(): MindfulPauseState {
        state = computeBaseState(config)
        return state
    }

    /**
     * Called when the scheduled re-intervention timer ticks while [currentForegroundPackage] is active.
     */
    fun onReInterventionTimerTick(
        currentForegroundPackage: String?,
        elapsedRealtimeMs: Long
    ): MindfulPauseState {
        val current = state as? MindfulPauseState.ActiveInTarget ?: return state
        if (config.featureStatus != PauseFeatureStatus.ON ||
            !config.selectedPackages.contains(current.targetPackage)
        ) {
            state = if (current.isFocusLockSession) {
                current.copy(nextReInterventionElapsedMs = null)
            } else {
                computeBaseState(config)
            }
            return state
        }
        val dueAt = current.nextReInterventionElapsedMs ?: return state

        if (currentForegroundPackage != current.targetPackage) {
            // Target is no longer foreground; cancel re-intervention and transition to grace/ready
            val graceMs = config.returnGraceSeconds * 1000L
            state = if (graceMs > 0L) {
                MindfulPauseState.GraceWindow(
                    lastTargetPackage = current.targetPackage,
                    graceExpiresAtElapsedMs = elapsedRealtimeMs + graceMs,
                    isFocusLockSession = current.isFocusLockSession
                )
            } else {
                MindfulPauseState.Ready
            }
            return state
        }

        if (elapsedRealtimeMs >= dueAt) {
            state = buildPauseState(current.targetPackage, elapsedRealtimeMs)
        }
        return state
    }

    /**
     * Resets any active overlay or target timer when the accessibility service is interrupted.
     */
    fun onInterrupted(): MindfulPauseState {
        return onScreenOffOrServiceStopped()
    }

    /**
     * Resets any active overlay or target timer when the screen turns off, service is disabled,
     * or process is torn down.
     */
    fun onScreenOffOrServiceStopped(): MindfulPauseState {
        state = computeBaseState(config)
        return state
    }

    private fun buildPauseState(targetPackage: String, elapsedRealtimeMs: Long): MindfulPauseState.PauseVisible {
        return MindfulPauseState.PauseVisible(
            targetPackage = targetPackage,
            startedAtElapsedMs = elapsedRealtimeMs,
            durationSeconds = config.getDurationForPackage(targetPackage),
            promptText = config.promptText,
            isFocusLock = false
        )
    }

    private fun buildActiveState(
        targetPackage: String,
        elapsedRealtimeMs: Long,
        isFocusLockSession: Boolean = false
    ): MindfulPauseState.ActiveInTarget {
        val shouldScheduleMindfulReIntervention =
            config.featureStatus == PauseFeatureStatus.ON &&
                config.selectedPackages.contains(targetPackage) &&
                config.isReInterventionEnabledFor(targetPackage)
        val nextReIntervention = if (shouldScheduleMindfulReIntervention) {
            val intervalMins = config.getReInterventionMinutesFor(targetPackage)
            elapsedRealtimeMs + (intervalMins * 60_000L)
        } else {
            null
        }
        return MindfulPauseState.ActiveInTarget(
            targetPackage = targetPackage,
            allowedAtElapsedMs = elapsedRealtimeMs,
            nextReInterventionElapsedMs = nextReIntervention,
            isFocusLockSession = isFocusLockSession
        )
    }

    private fun isTransientSystemWindow(packageName: String): Boolean {
        return packageName == "com.android.systemui" ||
            packageName == "com.google.android.permissioncontroller" ||
            packageName == "com.android.permissioncontroller" ||
            packageName == "android"
    }

    private fun computeBaseState(cfg: MindfulPauseConfig): MindfulPauseState {
        return if (cfg.featureStatus == PauseFeatureStatus.ON) {
            MindfulPauseState.Ready
        } else {
            MindfulPauseState.Disabled
        }
    }
}
