package com.example.dueordie.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A short, low-level, descending sine chime for the opt-in breathing pause. */
object PausePing {
    fun playIfEnabled(context: android.content.Context) {
        if (!OnboardingPreferences(context).isPausePingEnabled) return
        play(context)
    }

    fun play(context: android.content.Context) {
        Thread({
            var track: AudioTrack? = null
            try {
                val sampleRate = 22_050
                val durationSeconds = 0.28
                val count = (sampleRate * durationSeconds).toInt()
                val samples = ShortArray(count)
                var phase = 0.0
                for (index in samples.indices) {
                    val time = index.toDouble() / sampleRate
                    val progress = time / durationSeconds
                    val frequency = 880.0 - (220.0 * progress)
                    phase += 2.0 * PI * frequency / sampleRate
                    val fadeIn = (progress / 0.06).coerceIn(0.0, 1.0)
                    val fadeOut = exp(-4.0 * progress)
                    val envelope = fadeIn * fadeOut
                    samples[index] = (sin(phase) * envelope * 5_200.0).toInt().toShort()
                }

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                track = AudioTrack(
                    attributes,
                    format,
                    samples.size * Short.SIZE_BYTES,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.write(samples, 0, samples.size)
                    track.setVolume(0.22f)
                    track.play()
                    Thread.sleep((durationSeconds * 1_000).toLong())
                }
            } catch (_: Exception) {
                // Audio is optional; a device audio failure must never interrupt the pause.
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }, "DueOrDie-PausePing").apply { isDaemon = true }.start()
    }
}
