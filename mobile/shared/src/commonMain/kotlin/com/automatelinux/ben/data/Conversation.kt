package com.automatelinux.ben.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** Whether the phone is hearing from the server right now. */
sealed interface Link {
    data object Connecting : Link
    data object Live : Link
    data class Down(val why: Why) : Link

    enum class Why { Unreachable, Refused, Broken }
}

/**
 * The conversation as this phone knows it, kept current.
 *
 * [follow] is the whole of the syncing: ask for what is new, wait on the server when there is
 * nothing, and say plainly when the server cannot be heard. It runs while the app is on screen
 * and is cancelled when it is not — a transcript nobody is looking at needs no connection.
 */
class Conversation(
    private val source: ConversationSource,
    private val store: KeyValueStore,
    private val now: () -> Long,
) {
    var window by mutableStateOf(Window())
        private set

    var link: Link by mutableStateOf(Link.Connecting)
        private set

    /**
     * False until the saved copy or the server has said something. Before that there is nothing
     * to show — which is not the same as "nothing was said".
     */
    var answered by mutableStateOf(false)
        private set

    var loadingOlder by mutableStateOf(false)
        private set

    var olderFailed by mutableStateOf(false)
        private set

    /**
     * The server's clock minus this phone's. "Working for 12 s" is measured against when the
     * server heard the sentence, and a phone a minute out would otherwise open at 72 s.
     */
    var skewMs by mutableStateOf(0L)
        private set

    private val json = Json { ignoreUnknownKeys = true }
    private val retry = Channel<Unit>(Channel.CONFLATED)

    init {
        // A copy that will not decode is a copy from another version of the app. It is only a
        // cache: the server still has every line.
        val saved = store.get(SAVED)?.let { runCatching { json.decodeFromString(Window.serializer(), it) }.getOrNull() }
        if (saved != null && saved.epoch != null) {
            window = saved
            answered = true
        }
    }

    /** Keep [window] current until cancelled. */
    suspend fun follow() {
        link = Link.Connecting
        var caughtUp = false
        var pause = FIRST_PAUSE_MS

        while (true) {
            try {
                val page = when (val ask = window.ask()) {
                    Ask.Latest -> source.page(limit = PAGE)
                    // Only wait on the server once there is nothing left to fetch; the first
                    // request after opening must come straight back, or "Connecting" would sit
                    // there for the whole 25 seconds.
                    is Ask.Newer -> source.page(after = ask.after, limit = PAGE, waitSeconds = if (caughtUp) WAIT_S else null)
                }

                skewMs = page.now - now()
                val before = window
                // `loadOlder` may have grown the front while this waited; it never touches the
                // newest end, which is the only part `absorb` reads.
                val after = before.absorb(page)
                if (after != before) {
                    window = after
                    save()
                }
                // An emptied window asks for the latest page next, at once. Otherwise there is
                // more to fetch exactly when the server said so.
                caughtUp = after.epoch != null && !(before.epoch != null && page.more)

                answered = true
                link = Link.Live
                pause = FIRST_PAUSE_MS
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiFailure) {
                caughtUp = false
                answered = true
                link = Link.Down(
                    when (e) {
                        is ApiFailure.Unreachable -> Link.Why.Unreachable
                        is ApiFailure.Refused -> Link.Why.Refused
                        is ApiFailure.Broken -> Link.Why.Broken
                    },
                )
                // Try again after a pause that grows, or the moment he taps Retry.
                val tapped = withTimeoutOrNull(pause) { retry.receive() } != null
                if (tapped) link = Link.Connecting
                pause = (pause * 2).coerceAtMost(MAX_PAUSE_MS)
            }
        }
    }

    /** Skip whatever pause [follow] is in. */
    fun retryNow() {
        retry.trySend(Unit)
    }

    /** Fetch the page before the oldest line held. Safe to call repeatedly. */
    suspend fun loadOlder() {
        val asked = window
        val first = asked.firstSeq ?: return
        if (loadingOlder || !asked.olderRemain) return

        loadingOlder = true
        olderFailed = false
        try {
            val page = source.page(before = first, limit = PAGE)
            val current = window
            // `follow` may have started the window over while this was in flight.
            if (current.epoch == asked.epoch && current.firstSeq == first) {
                val grown = current.withOlder(page)
                if (grown == null) {
                    // Not the lines before these. Saying so beats a "loading" that never ends.
                    olderFailed = true
                } else {
                    window = grown
                    save()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            olderFailed = true
        } finally {
            loadingOlder = false
        }
    }

    private fun save() {
        store.put(SAVED, json.encodeToString(Window.serializer(), window.newest(SAVED_EVENTS)))
    }

    companion object {
        const val PAGE = 200
        const val WAIT_S = 25
        const val SAVED_EVENTS = 400
        const val FIRST_PAUSE_MS = 2_000L
        const val MAX_PAUSE_MS = 15_000L
        private const val SAVED = "window"
    }
}
