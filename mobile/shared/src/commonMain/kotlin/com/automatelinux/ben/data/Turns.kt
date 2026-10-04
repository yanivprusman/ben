package com.automatelinux.ben.data

import com.automatelinux.ben.data.model.Event
import com.automatelinux.ben.data.model.Kind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One exchange: a sentence that was heard, and how it ended — if it has. */
data class Turn(
    val id: String,
    /** Null only at the very top of the window, when the page boundary fell between the two halves. */
    val heard: Event?,
    val outcome: Event?,
) {
    val startedAt: Long get() = (heard ?: outcome)!!.ts
    val endedAt: Long get() = (outcome ?: heard)!!.ts
    val unanswered: Boolean get() = outcome == null
    val failed: Boolean get() = outcome?.kind == Kind.FAILED

    /** How long Ben took, when both ends are known. */
    val tookMs: Long? get() = if (heard != null && outcome != null) outcome.ts - heard.ts else null
}

/** Events paired into turns, in the order each turn was first heard of. */
fun turnsOf(events: List<Event>): List<Turn> {
    val turns = LinkedHashMap<String, Turn>()
    for (e in events) {
        val soFar = turns[e.turn] ?: Turn(e.turn, null, null)
        turns[e.turn] = when (e.kind) {
            Kind.HEARD -> soFar.copy(heard = e)
            Kind.REPLY, Kind.FAILED -> soFar.copy(outcome = e)
            // A kind this build does not know: a newer server. Leave the turn as it is.
            else -> continue
        }
    }
    return turns.values.toList()
}

/** What the screen lists, top to bottom. */
sealed interface Row {
    val key: String

    data class Day(val date: LocalDate) : Row {
        override val key get() = "day-$date"
    }

    /** A silence long enough that what follows is another conversation. */
    data class Gap(val before: String, val minutes: Long) : Row {
        override val key get() = "gap-$before"
    }

    data class Exchange(val turn: Turn) : Row {
        override val key get() = "turn-${turn.id}"
    }
}

/** Under this, two turns belong to the same conversation and nothing is drawn between them. */
const val GAP_MINUTES = 30L

fun localDate(ts: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(ts).toLocalDateTime(zone).date

/** Turns laid out with the day each began on and the silences between them. */
fun timeline(turns: List<Turn>, zone: TimeZone): List<Row> {
    val rows = ArrayList<Row>(turns.size + 8)
    var previous: Turn? = null
    for (turn in turns) {
        val day = localDate(turn.startedAt, zone)
        val last = previous
        if (last == null || localDate(last.startedAt, zone) != day) {
            rows += Row.Day(day)
        } else {
            val quiet = (turn.startedAt - last.endedAt) / 60_000
            if (quiet >= GAP_MINUTES) rows += Row.Gap(turn.id, quiet)
        }
        rows += Row.Exchange(turn)
        previous = turn
    }
    return rows
}
