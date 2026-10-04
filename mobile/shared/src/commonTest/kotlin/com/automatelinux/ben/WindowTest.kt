package com.automatelinux.ben

import com.automatelinux.ben.data.Ask
import com.automatelinux.ben.data.MAX_CATCH_UP
import com.automatelinux.ben.data.Window
import com.automatelinux.ben.data.absorb
import com.automatelinux.ben.data.ask
import com.automatelinux.ben.data.newest
import com.automatelinux.ben.data.withOlder
import com.automatelinux.ben.data.model.Event
import com.automatelinux.ben.data.model.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun event(seq: Int) = Event(seq = seq, ts = seq * 1000L, turn = "t${(seq + 1) / 2}", kind = if (seq % 2 == 1) "heard" else "reply", text = "line $seq")
private fun events(range: IntRange) = range.map(::event)
private fun page(range: IntRange, more: Boolean = false, last: Int = range.last, epoch: String = "A") =
    Page(epoch = epoch, events = events(range), more = more, last = last, now = 0)
private fun window(range: IntRange, older: Boolean = false) = Window("A", events(range), older)

class WindowTest {
    @Test
    fun knowingNothingAsksForTheLatestPage() {
        assertEquals(Ask.Latest, Window().ask())
    }

    @Test
    fun theLatestPageBecomesTheWindow() {
        val w = Window().absorb(page(101..300, more = true))
        assertEquals(101, w.firstSeq)
        assertEquals(300, w.lastSeq)
        assertEquals(true, w.olderRemain, "`more` on the latest page means older lines exist")
        assertEquals(Ask.Newer(300), w.ask())
    }

    @Test
    fun anEmptyRecordIsKnownAndWaitedOnFromZero() {
        // Not asking for "latest" again and again: the record exists, it just has nothing in it.
        val w = Window().absorb(Page("A", emptyList(), more = false, last = 0, now = 0))
        assertEquals(Ask.Newer(0), w.ask())
        assertEquals(listOf(1, 2), w.absorb(page(1..2)).events.map { it.seq })
    }

    @Test
    fun newerEventsAreAddedAtTheEnd() {
        val w = window(1..4).absorb(page(5..6))
        assertEquals((1..6).toList(), w.events.map { it.seq })
    }

    @Test
    fun nothingNewChangesNothing() {
        val w = window(1..4)
        assertEquals(w, w.absorb(Page("A", emptyList(), more = false, last = 4, now = 0)))
    }

    @Test
    fun aDifferentEpochEmptiesTheWindow() {
        // The record was replaced. Waiting for seq 129 of a file that now has 12 lines waits forever.
        val w = window(1..128).absorb(Page("B", emptyList(), more = false, last = 12, now = 0))
        assertEquals(Window(), w)
        assertEquals(Ask.Latest, w.ask())
    }

    @Test
    fun aRecordThatShrankIsNotTheSameRecord() {
        assertEquals(Window(), window(1..128).absorb(Page("A", emptyList(), more = false, last = 40, now = 0)))
    }

    @Test
    fun aHoleEmptiesTheWindowRatherThanHidingTheMissingLines() {
        assertEquals(Window(), window(1..4).absorb(page(7..8)))
    }

    @Test
    fun afterALongAbsenceItStartsOverFromTheLatestPage() {
        val far = 4 + MAX_CATCH_UP + 1
        assertEquals(Window(), window(1..4).absorb(page(5..204, more = true, last = far)))
        // One event fewer, and it catches up line by line instead.
        assertEquals(204, window(1..4).absorb(page(5..204, more = true, last = far - 1)).lastSeq)
    }

    @Test
    fun anOlderPageGoesInFront() {
        val w = window(101..300, older = true).withOlder(page(1..100, more = false, last = 300))!!
        assertEquals(1, w.firstSeq)
        assertEquals(300, w.lastSeq)
        assertEquals(false, w.olderRemain)
    }

    @Test
    fun anOlderPageThatDoesNotMeetTheWindowIsRefused() {
        val w = window(101..300, older = true)
        assertNull(w.withOlder(page(1..50, last = 300)), "a hole between the page and the window")
        assertNull(w.withOlder(page(1..100, last = 300, epoch = "B")), "a page of another record")
    }

    @Test
    fun anEmptyOlderPageMeansTheStartWasReached() {
        assertEquals(false, window(1..10, older = true).withOlder(Page("A", emptyList(), false, 10, 0))!!.olderRemain)
    }

    @Test
    fun theSavedCopyKeepsTheNewestAndRemembersThereIsMore() {
        val saved = window(1..500).newest(400)
        assertEquals(101, saved.firstSeq)
        assertEquals(500, saved.lastSeq)
        assertEquals(true, saved.olderRemain)
        assertEquals(window(1..10), window(1..10).newest(400))
    }
}
