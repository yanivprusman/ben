package com.automatelinux.ben.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/**
 * Whose voice the phone speaks Ben's words in (asked for on 2026-10-30):
 * - [DESKTOP] — the caption voice, Kokoro rendered on the desktop
 * - [PHONE]   — the phone's own text-to-speech
 * and how loud (2026-10-31): [volume], percent of the rendered line on top of the phone's media
 * volume — Android gives the listener no volume of its own. Only the [VOLUMES] stops exist.
 *
 * The setting lives on the voiceControl server, where the listener app reads it with every
 * answer; this only shows it and changes it. Nothing is assumed: until the server has said,
 * [current] and [volume] are null and the choice is shown as unknown, and a change that fails leaves the
 * old value showing with the reason.
 */
class VoiceSetting(private val source: SettingsSource) {
    var current: String? by mutableStateOf(null)
        private set

    var volume: Int? by mutableStateOf(null)
        private set

    /** The value being sent, while it is in flight. */
    var saving: String? by mutableStateOf(null)
        private set

    var savingVolume: Int? by mutableStateOf(null)
        private set

    var failure: ApiFailure? by mutableStateOf(null)
        private set

    suspend fun load() {
        try {
            take(source.sound())
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
            take(source.setVoice(voice))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            failure = e
        } finally {
            saving = null
        }
    }

    suspend fun chooseVolume(percent: Int) {
        if (savingVolume != null || percent == volume || percent !in VOLUMES) return
        savingVolume = percent
        try {
            take(source.setVolume(percent))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            failure = e
        } finally {
            savingVolume = null
        }
    }

    /** "End conversation" while it is on its way; then whether it went. */
    var ending: Boolean by mutableStateOf(false)
        private set

    /** Ask the phone to stop. True when the desktop took it; a failure is in [failure]. */
    suspend fun endConversation(): Boolean {
        if (ending) return false
        ending = true
        return try {
            source.endConversation()
            failure = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            failure = e
            false
        } finally {
            ending = false
        }
    }

    private fun take(s: Sound) {
        current = s.voice
        volume = s.volume
        failure = null
    }

    companion object {
        const val DESKTOP = "kokoro"
        const val PHONE = "phone"

        /** The server's stops (voiceControl `server/lib/settings.mjs`), ~2 dB apart. */
        val VOLUMES = listOf(10, 12, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 320, 400)
    }
}
