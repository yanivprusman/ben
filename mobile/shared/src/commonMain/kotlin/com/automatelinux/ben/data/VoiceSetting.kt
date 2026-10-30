package com.automatelinux.ben.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/**
 * Whose voice the phone speaks Ben's words in (asked for on 2026-10-30):
 * - [DESKTOP] — the caption voice, Kokoro rendered on the desktop
 * - [PHONE]   — the phone's own text-to-speech
 *
 * The setting lives on the voiceControl server, where the listener app reads it with every
 * answer; this only shows it and changes it. Nothing is assumed: until the server has said,
 * [current] is null and the choice is shown as unknown, and a change that fails leaves the
 * old value showing with the reason.
 */
class VoiceSetting(private val source: SettingsSource) {
    var current: String? by mutableStateOf(null)
        private set

    /** The value being sent, while it is in flight. */
    var saving: String? by mutableStateOf(null)
        private set

    var failure: ApiFailure? by mutableStateOf(null)
        private set

    suspend fun load() {
        try {
            current = source.voice()
            failure = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            failure = e
        }
    }

    suspend fun choose(voice: String) {
        if (saving != null || voice == current) return
        saving = voice
        try {
            current = source.setVoice(voice)
            failure = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            failure = e
        } finally {
            saving = null
        }
    }

    companion object {
        const val DESKTOP = "kokoro"
        const val PHONE = "phone"
    }
}
