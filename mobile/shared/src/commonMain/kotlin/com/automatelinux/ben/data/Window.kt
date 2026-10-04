package com.automatelinux.ben.data

import com.automatelinux.ben.data.model.Event
import com.automatelinux.ben.data.model.Page
import kotlinx.serialization.Serializable

/**
 * The part of the conversation this phone holds: an unbroken run of events ending at the newest
 * one it has seen. Everything here is pure — the decisions about what to keep and what to throw
 * away are the part worth testing, and none of them need a network.
 */
@Serializable
data class Window(
    /** The file these events came from; null until the server has answered once. */
    val epoch: String? = null,
    val events: List<Event> = emptyList(),
    /** The server holds events older than the first one here. */
    val olderRemain: Boolean = false,
) {
    val firstSeq: Int? get() = events.firstOrNull()?.seq
    val lastSeq: Int? get() = events.lastOrNull()?.seq
}

/** What to ask the server next. */
sealed interface Ask {
    /** Nothing is known yet: the latest page. */
    data object Latest : Ask

    /** Whatever is newer than [after]. */
    data class Newer(val after: Int) : Ask
}

/**
 * Away for longer than this many events, the phone stops catching up line by line and starts
 * again from the latest page — nobody opens the app to watch three days scroll past, and the
 * older lines are one scroll away.
 */
const val MAX_CATCH_UP = 600

fun Window.ask(): Ask = if (epoch == null) Ask.Latest else Ask.Newer(lastSeq ?: 0)

/**
 * The window after the server answered [ask].
 *
 * Returning an empty [Window] means "what I hold describes nothing the server has" — the next
 * ask is then [Ask.Latest], and the screen is rebuilt from that.
 */
fun Window.absorb(page: Page): Window {
    if (epoch == null) return Window(page.epoch, page.events, page.more)

    val last = lastSeq ?: 0
    return when {
        // A different file. Waiting for seq 9000 of a record that now has 12 lines would wait forever.
        page.epoch != epoch -> Window()
        // The same name with fewer events is not the file these came from either.
        page.last < last -> Window()
        page.last - last > MAX_CATCH_UP -> Window()
        page.events.isEmpty() -> this
        // A hole would read as a seamless transcript with lines silently missing.
        page.events.first().seq != last + 1 -> Window()
        else -> copy(events = events + page.events)
    }
}

/**
 * The window with one page of older events put in front — or null when the page does not belong
 * in front of this window (another record, or not the lines just before the first one held).
 */
fun Window.withOlder(page: Page): Window? {
    val first = firstSeq ?: return null
    return when {
        page.epoch != epoch -> null
        page.events.isEmpty() -> copy(olderRemain = false)
        page.events.last().seq != first - 1 -> null
        else -> copy(events = page.events + events, olderRemain = page.more)
    }
}

/** At most [max] of the newest events — what is worth saving for the next launch. */
fun Window.newest(max: Int): Window =
    if (events.size <= max) this else copy(events = events.takeLast(max), olderRemain = true)
