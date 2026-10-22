package com.example.dueordie

import com.example.dueordie.data.model.FocusLockDecision
import com.example.dueordie.data.model.MindfulPauseConfig
import com.example.dueordie.data.model.MindfulPauseState
import com.example.dueordie.data.model.MindfulPauseStateMachine
import com.example.dueordie.data.model.PauseFeatureStatus
import com.example.dueordie.data.model.Task
import com.example.dueordie.data.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MindfulPauseStateMachineTest {

    private fun defaultConfig(
        isPauseEnabled: Boolean = true,
        isAccessibilityServiceEnabled: Boolean = true
    ) = MindfulPauseConfig(
        isPauseEnabled = isPauseEnabled,
        isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
        selectedPackages = setOf("com.instagram.android", "com.google.android.youtube"),
        pauseDurationSeconds = 8,
        promptText = "Take one slow breath before continuing.",
        isReInterventionEnabled = true,
        reInterventionIntervalMinutes = 10,
        disabledReInterventionPackages = setOf("com.google.android.youtube"),
        returnGraceSeconds = 30
    )

    @Test
    fun featureStatus_reportsHonestPermissionAndToggleState() {
        assertEquals(
            PauseFeatureStatus.OFF,
            defaultConfig(isPauseEnabled = false, isAccessibilityServiceEnabled = true).featureStatus
        )
        assertEquals(
            PauseFeatureStatus.NEEDS_PERMISSION,
            defaultConfig(isPauseEnabled = true, isAccessibilityServiceEnabled = false).featureStatus
        )
        assertEquals(
            PauseFeatureStatus.ON,
            defaultConfig(isPauseEnabled = true, isAccessibilityServiceEnabled = true).featureStatus
        )
    }

    @Test
    fun fullLifecycle_readyToPauseToActiveToGraceToReady() {
        val config = defaultConfig()
        val sm = MindfulPauseStateMachine(config)
        assertTrue(sm.state is MindfulPauseState.Ready)

        // 1. Entering selected app triggers PauseVisible
        val enterState = sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 2_000L
        )
        assertTrue(enterState is MindfulPauseState.PauseVisible)
        val pauseVisible = enterState as MindfulPauseState.PauseVisible
        assertEquals("com.instagram.android", pauseVisible.targetPackage)
        assertEquals(2_000L, pauseVisible.startedAtElapsedMs)
        assertEquals(8, pauseVisible.durationSeconds)

        // 2. Duplicate window event for same package does not reset timer
        val duplicateState = sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 5_000L
        )
        assertEquals(pauseVisible, duplicateState)

        // 3. Tapping Continue transitions to ActiveInTarget with 10m re-intervention timer
        val activeState = sm.onContinueClicked(elapsedRealtimeMs = 10_000L)
        assertTrue(activeState is MindfulPauseState.ActiveInTarget)
        val active = activeState as MindfulPauseState.ActiveInTarget
        assertEquals("com.instagram.android", active.targetPackage)
        assertEquals(10_000L + 600_000L, active.nextReInterventionElapsedMs)

        // 4. Switching to launcher starts 30s GraceWindow
        val graceState = sm.onForegroundPackageChanged(
            packageName = "com.google.android.apps.nexuslauncher",
            elapsedRealtimeMs = 20_000L
        )
        assertTrue(graceState is MindfulPauseState.GraceWindow)
        val grace = graceState as MindfulPauseState.GraceWindow
        assertEquals(50_000L, grace.graceExpiresAtElapsedMs)

        // 5. Returning within 30s grace skips pause and resumes ActiveInTarget
        val resumedState = sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 35_000L
        )
        assertTrue(resumedState is MindfulPauseState.ActiveInTarget)

        // 6. Leaving again and returning after 30s grace expires shows a new PauseVisible
        sm.onForegroundPackageChanged(
            packageName = "com.google.android.apps.nexuslauncher",
            elapsedRealtimeMs = 40_000L
        )
        val afterGraceState = sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 75_000L // > 40_000 + 30_000
        )
        assertTrue(afterGraceState is MindfulPauseState.PauseVisible)
    }

    @Test
    fun userLeave_returnsToReadyWithoutGraceWindow() {
        val config = defaultConfig()
        val sm = MindfulPauseStateMachine(config)

        sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 1_000L
        )
        val afterLeave = sm.onLeaveClicked()
        assertTrue(afterLeave is MindfulPauseState.Ready)

        // Re-opening immediately must trigger PauseVisible (no grace window granted on Leave)
        val reopenState = sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 2_000L
        )
        assertTrue(reopenState is MindfulPauseState.PauseVisible)
    }

    @Test
    fun reIntervention_triggersWhenDueAndRespectsPerAppOverride() {
        val config = defaultConfig()
        val sm = MindfulPauseStateMachine(config)

        // Instagram has Show again enabled
        sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 1_000L
        )
        sm.onContinueClicked(elapsedRealtimeMs = 9_000L)

        // Before 10 minutes: stays ActiveInTarget
        val beforeDue = sm.onReInterventionTimerTick(
            currentForegroundPackage = "com.instagram.android",
            elapsedRealtimeMs = 9_000L + 599_000L
        )
        assertTrue(beforeDue is MindfulPauseState.ActiveInTarget)

        // At 10 minutes: transitions back to PauseVisible
        val atDue = sm.onReInterventionTimerTick(
            currentForegroundPackage = "com.instagram.android",
            elapsedRealtimeMs = 9_000L + 600_000L
        )
        assertTrue(atDue is MindfulPauseState.PauseVisible)

        // YouTube has per-app Show again disabled in config
        sm.onLeaveClicked()
        sm.onForegroundPackageChanged(
            packageName = "com.google.android.youtube",
            elapsedRealtimeMs = 700_000L
        )
        val youtubeActive = sm.onContinueClicked(elapsedRealtimeMs = 708_000L) as MindfulPauseState.ActiveInTarget
        assertNull(youtubeActive.nextReInterventionElapsedMs)
    }

    @Test
    fun serviceInterrupt_resetsStateToReadyAndRequiresFreshPause() {
        val config = defaultConfig()
        val sm = MindfulPauseStateMachine(config)

        // Interrupt during countdown
        sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 1_000L
        )
        assertTrue(sm.state is MindfulPauseState.PauseVisible)
        val afterInterruptCountdown = sm.onInterrupted()
        assertTrue(afterInterruptCountdown is MindfulPauseState.Ready)

        // Re-entering after interrupt triggers a fresh pause
        val reEnter = sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 2_000L
        )
        assertTrue(reEnter is MindfulPauseState.PauseVisible)

        // Interrupt after Continue (while ActiveInTarget) also resets to Ready
        sm.onContinueClicked(elapsedRealtimeMs = 10_000L)
        assertTrue(sm.state is MindfulPauseState.ActiveInTarget)
        val afterInterruptActive = sm.onInterrupted()
        assertTrue(afterInterruptActive is MindfulPauseState.Ready)
    }

    @Test
    fun focusLockIntervention_whenMindfulPauseDisabled_doesNotScheduleReIntervention() {
        // Mindful Pause is OFF, but repeat setting is still saved as true in preferences
        val config = defaultConfig(isPauseEnabled = false, isAccessibilityServiceEnabled = true)
        val sm = MindfulPauseStateMachine(config)

        val overdueTask = Task(
            id = 1,
            title = "Finish report",
            deadlineTimestamp = 1_000L,
            status = TaskStatus.OVERDUE,
            isHostageModeEnabled = true
        )
        val focusLockState = sm.onForegroundPackageChanged(
            packageName = "com.instagram.android",
            elapsedRealtimeMs = 5_000L,
            focusLockDecision = FocusLockDecision(
                shouldIntervene = true,
                blockingTask = overdueTask,
                overdueMinutes = 20L
            )
        )
        assertTrue(focusLockState is MindfulPauseState.PauseVisible)
        val pauseVisible = focusLockState as MindfulPauseState.PauseVisible
        assertTrue(pauseVisible.isFocusLock)

        // Tapping Continue on Focus Lock when Mindful Pause is disabled must NOT schedule re-intervention
        val activeState = sm.onContinueClicked(elapsedRealtimeMs = 17_000L) as MindfulPauseState.ActiveInTarget
        assertNull(activeState.nextReInterventionElapsedMs)
    }
}
