package com.example.dueordie.util

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Tactile haptic engine for Mora:
 * 1. Organic Lung-Air Simulation:
 *    - Inhale ("Breathe in"): Starts whisper-soft as lungs begin drawing air, progressively swelling
 *      in physical intensity up to capacity at top of breath, followed by a brief stillness.
 *    - Exhale ("Breathe out"): The exact reverse; starts firm and smoothly softens/fades away
 *      as air leaves the lungs, dissolving into stillness at the bottom of the breath.
 *    - Implemented with discrete, non-flooding pulses (18ms duration, ~190ms interval) to ensure
 *      smooth, consistent playback on all Android vibration hardware without motor stutter.
 * 2. Press-and-Hold Completion Haptics:
 *    - Accelerating cadence and amplitude ramp while holding, culminating in a crisp double-thud
 *      on task completion or a gentle cancel tick on early release.
 */
class MoraHaptics(context: Context) {

    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastBreathPulseElapsedMs: Long = 0L
    private var lastHoldPulseElapsedMs: Long = 0L

    fun resetBreathCycle() {
        lastBreathPulseElapsedMs = 0L
    }

    fun resetHoldCycle() {
        lastHoldPulseElapsedMs = 0L
    }

    /**
     * Called during breathing cycles to generate organic lung-swell tactile feedback.
     * @param phaseProgress 0.0f..1.0f within current breath phase
     * @param isInhale true during "Breathe in", false during "Breathe out"
     */
    fun onBreathingFrame(
        phaseProgress: Float,
        isInhale: Boolean,
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val p = phaseProgress.coerceIn(0f, 1f)

        // Minimum 180ms spacing to prevent vibrator queue stutter/overlap
        val intervalMs = 185L

        if (isInhale) {
            // Lungs filling with air:
            // Top-of-breath stillness between 0.90 and 1.0 (lungs held full)
            if (p > 0.90f) return

            if (elapsedRealtimeMs - lastBreathPulseElapsedMs < intervalMs) return
            lastBreathPulseElapsedMs = elapsedRealtimeMs

            val fillRatio = (p / 0.90f).coerceIn(0f, 1f)
            // Progressive swell curve: whisper soft (25) -> powerful chest expansion (240)
            val curvedVolume = fillRatio.pow(1.5f)
            val amplitude = (25f + 215f * curvedVolume).roundToInt().coerceIn(1, 255)
            val durationMs = (16L + (8f * curvedVolume).toLong()).coerceIn(14L, 26L)
            vibrateOneShot(vib, durationMs, amplitude)
        } else {
            // Air releasing from lungs (exact reverse):
            // Bottom-of-breath stillness between 0.88 and 1.0 (empty lungs at rest)
            if (p > 0.88f) return

            if (elapsedRealtimeMs - lastBreathPulseElapsedMs < intervalMs) return
            lastBreathPulseElapsedMs = elapsedRealtimeMs

            val emptyRatio = (p / 0.88f).coerceIn(0f, 1f)
            val fadeRatio = (1f - emptyRatio).coerceIn(0f, 1f)
            // Progressive fade curve: firm release (240) -> gentle whisper (25)
            val curvedVolume = fadeRatio.pow(1.5f)
            val amplitude = (25f + 215f * curvedVolume).roundToInt().coerceIn(1, 255)
            val durationMs = (16L + (8f * curvedVolume).toLong()).coerceIn(14L, 26L)
            vibrateOneShot(vib, durationMs, amplitude)
        }
    }

    fun onBreathingCycleComplete() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 25L, 60L, 40L),
                        intArrayOf(0, 90, 0, 180),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(50L)
            }
        }
    }

    /**
     * Called continuously while the user presses and holds to complete a task.
     * @param holdProgress 0.0f..1.0f
     */
    fun onHoldToCompleteProgress(
        holdProgress: Float,
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val p = holdProgress.coerceIn(0f, 1f)
        if (p <= 0.02f || p >= 1.0f) return

        // Smoothly accelerate pulse cadence as completion approaches
        val intervalMs = (180f - 105f * p).toLong().coerceAtLeast(75L)
        if (elapsedRealtimeMs - lastHoldPulseElapsedMs < intervalMs) return
        lastHoldPulseElapsedMs = elapsedRealtimeMs

        val curved = p.pow(1.4f)
        val amplitude = (35f + 210f * curved).roundToInt().coerceIn(1, 255)
        val durationMs = (14L + (10f * curved).toLong()).coerceIn(14L, 26L)
        vibrateOneShot(vib, durationMs, amplitude)
    }

    fun onHoldTaskCompleted() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 28L, 50L, 70L),
                        intArrayOf(0, 150, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(80L)
            }
        }
    }

    fun onHoldCancelled() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        vibrateOneShot(vib, 15L, 45)
    }

    fun onTick() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        vibrateOneShot(vib, 12L, 40)
    }

    companion object {
        fun performHapticTick(context: Context) {
            MoraHaptics(context).onTick()
        }
    }

    fun cancel() {
        runCatching { vibrator?.cancel() }
    }

    private fun vibrateOneShot(vib: Vibrator, durationMs: Long, amplitude: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
                } else {
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }
}
