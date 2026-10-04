package com.automatelinux.ben

import com.automatelinux.ben.data.Row
import com.automatelinux.ben.data.timeline
import com.automatelinux.ben.data.turnsOf
import com.automatelinux.ben.data.model.Event
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val MIN = 60_000L
private const val HOUR = 60 * MIN

class TurnsTest {
    private var seq = 0
    private fun heard(turn: String, ts: Long) = Event(++seq, ts, turn, "heard", "said $turn")
    private fun reply(turn: String, ts: Long) = Event(++seq, ts, turn, "reply", "answer $turn")
    private fun failed(turn: String, ts: Long) = Event(++seq, ts, turn, "failed", "That did not work", reason = "error")

    @Test
    fun aTurnIsWhatWasHeardAndHowItEnded() {
        val turns = turnsOf(listOf(heard("a", 0), reply("a", 9_000), heard("b", 20_000), failed("b", 80_000), heard("c", 90_000)))

        assertEquals(listOf("a", "b", "c"), turns.map { it.id })
        assertEquals(9_000, turns[0].tookMs)
        assertEquals(false, turns[0].failed)
        assertEquals(true, turns[1].failed)
        assertEquals(true, turns[2].unanswered)
        assertNull(turns[2].tookMs)
    }

    @Test
    fun overlappingTurnsArePairedByIdNotByOrder() {
        // 2026-10-04 03:09: two sentences in one second, answered in the other order.
        val turns = turnsOf(listOf(heard("a", 0), heard("b", 500), failed("b", 60_000), failed("a", 60_200)))
        assertEquals(listOf("a", "b"), turns.map { it.id })
        assertEquals(60_200, turns[0].outcome?.ts)
        assertEquals(60_000, turns[1].outcome?.ts)
    }

    @Test
    fun anAnswerWhoseQuestionIsOnTheOlderPageStillShows() {
        val turns = turnsOf(listOf(reply("a", 9_000), heard("b", 20_000)))
        assertNull(turns[0].heard)
        assertEquals(9_000, turns[0].startedAt)
    }

    @Test
    fun anEventKindFromANewerServerIsLeftOut() {
        val turns = turnsOf(listOf(heard("a", 0), Event(99, 1, "a", "something-new", "?"), reply("a", 5_000)))
        assertEquals(1, turns.size)
        assertEquals("reply", turns[0].outcome?.kind)
    }

    @Test
    fun theTimelineMarksDaysAndLongSilences() {
        val day1 = 1_791_000_000_000L - (1_791_000_000_000L % (24 * HOUR)) + 10 * HOUR   // 10:00 UTC
        val rows = timeline(
            turnsOf(
                listOf(
                    heard("a", day1), reply("a", day1 + 9_000),
                    heard("b", day1 + 2 * MIN), reply("b", day1 + 3 * MIN),            // same conversation
                    heard("c", day1 + 3 * HOUR), reply("c", day1 + 3 * HOUR + 5_000),   // hours later
                    heard("d", day1 + 24 * HOUR), reply("d", day1 + 24 * HOUR + 5_000), // next day
                ),
            ),
            TimeZone.UTC,
        )

        val shape = rows.map {
            when (it) {
                is Row.Day -> "day"
                is Row.Gap -> "gap ${it.minutes}"
                is Row.Exchange -> it.turn.id
            }
        }
        // The silence is measured from when the last answer ended, not from when its question began.
        assertEquals(listOf("day", "a", "b", "gap 177", "c", "day", "d"), shape)
        assertEquals(rows.map { it.key }.distinct().size, rows.size, "keys are unique")
    }

    @Test
    fun theDayIsTheOneOnThePhoneNotInUtc() {
        // 22:30 UTC on the 3rd is already the 4th in Israel.
        val ts = LocalDate(2026, 10, 3).toEpochDays() * 24 * HOUR + 22 * HOUR + 30 * MIN
        val rows = timeline(turnsOf(listOf(heard("a", ts))), TimeZone.of("Asia/Jerusalem"))
        assertEquals(Row.Day(LocalDate(2026, 10, 4)), rows.first())
    }
}
