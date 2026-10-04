package com.automatelinux.ben

import com.automatelinux.ben.data.ApiFailure
import com.automatelinux.ben.data.Conversation
import com.automatelinux.ben.data.ConversationSource
import com.automatelinux.ben.data.KeyValueStore
import com.automatelinux.ben.data.Link
import com.automatelinux.ben.data.model.Event
import com.automatelinux.ben.data.model.Page
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun event(seq: Int) = Event(seq, seq * 1000L, "t${(seq + 1) / 2}", if (seq % 2 == 1) "heard" else "reply", "line $seq")

/** A server holding a record that the test can add to, replace, or take offline. */
private class FakeServer(var epoch: String = "A") : ConversationSource {
    val record = ArrayList<Event>()
    val asked = ArrayList<String>()
    var failure: ApiFailure? = null
    private var arrival = CompletableDeferred<Unit>()

    fun say(count: Int) {
        repeat(count) { record += event(record.size + 1) }
        arrival.complete(Unit)
        arrival = CompletableDeferred()
    }

    override suspend fun page(after: Int?, before: Int?, limit: Int?, waitSeconds: Int?): Page {
        asked += "after=$after before=$before wait=$waitSeconds"
        failure?.let { throw it }
        val n = limit ?: 200
        if (after != null && waitSeconds != null && record.size <= after) {
            // The long-poll: hold until something is said or the wait runs out.
            kotlinx.coroutines.withTimeoutOrNull(waitSeconds * 1000L) { arrival.await() }
        }
        return when {
            after != null -> Page(epoch, record.drop(after).take(n), more = after + n < record.size, last = record.size, now = 0)
            else -> {
                val end = (before?.minus(1) ?: record.size).coerceIn(0, record.size)
                val start = (end - n).coerceAtLeast(0)
                Page(epoch, record.subList(start, end).toList(), more = start > 0, last = record.size, now = 0)
            }
        }
    }
}

private class MemoryStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationTest {
    private fun TestScope.conversation(server: FakeServer, store: KeyValueStore = MemoryStore()) =
        Conversation(server, store) { testScheduler.currentTime }

    @Test
    fun itLoadsTheLatestPageThenWaitsOnTheServerForMore() = runTest {
        val server = FakeServer().apply { say(300) }
        val c = conversation(server)
        val following = launch { c.follow() }
        runCurrent()

        assertEquals(Link.Live, c.link)
        assertEquals(101, c.window.firstSeq)
        assertEquals(300, c.window.lastSeq)
        assertTrue(c.window.olderRemain)
        // First the latest page; then — already caught up — a request the server may hold.
        assertEquals(listOf("after=null before=null wait=null", "after=300 before=null wait=25"), server.asked)

        server.say(2)
        runCurrent()
        assertEquals(302, c.window.lastSeq, "a new turn arrives without anyone asking again")

        following.cancelAndJoin()
    }

    @Test
    fun theFirstRequestAfterOpeningWithASavedCopyDoesNotWait() = runTest {
        // Otherwise "Connecting" sits there for the full 25 seconds whenever nothing is new.
        val server = FakeServer().apply { say(10) }
        val store = MemoryStore()
        val first = conversation(server, store)
        val a = launch { first.follow() }
        runCurrent()
        a.cancelAndJoin()

        server.asked.clear()
        val reopened = conversation(server, store)
        assertEquals(10, reopened.window.lastSeq, "the saved copy is there before any request")
        assertTrue(reopened.answered)

        val b = launch { reopened.follow() }
        runCurrent()
        assertEquals("after=10 before=null wait=null", server.asked.first())
        assertEquals(Link.Live, reopened.link)
        b.cancelAndJoin()
    }

    @Test
    fun aReplacedRecordIsReloadedFromScratch() = runTest {
        val server = FakeServer().apply { say(128) }
        val c = conversation(server)
        val following = launch { c.follow() }
        runCurrent()
        assertEquals(128, c.window.lastSeq)

        server.record.clear()
        server.epoch = "B"
        server.say(4)
        runCurrent()

        assertEquals("B", c.window.epoch)
        assertEquals(listOf(1, 2, 3, 4), c.window.events.map { it.seq })
        following.cancelAndJoin()
    }

    @Test
    fun anUnreachableServerIsSaidPlainlyAndRetriedWithAGrowingPause() = runTest {
        val server = FakeServer().apply { say(4); failure = ApiFailure.Unreachable() }
        val c = conversation(server)
        val following = launch { c.follow() }
        runCurrent()

        assertEquals(Link.Down(Link.Why.Unreachable), c.link)
        assertTrue(c.answered, "a failure is an answer too: the screen can say what is wrong")
        assertEquals(1, server.asked.size)

        advanceTimeBy(2_001); runCurrent()
        assertEquals(2, server.asked.size)
        advanceTimeBy(2_001); runCurrent()
        assertEquals(2, server.asked.size, "the second pause is longer than the first")
        advanceTimeBy(2_001); runCurrent()
        assertEquals(3, server.asked.size)

        server.failure = null
        c.retryNow()
        runCurrent()
        assertEquals(Link.Live, c.link)
        assertEquals(4, c.window.lastSeq)
        following.cancelAndJoin()
    }

    @Test
    fun aRefusedTokenIsNotMistakenForBeingOffline() = runTest {
        val server = FakeServer().apply { failure = ApiFailure.Refused() }
        val c = conversation(server)
        val following = launch { c.follow() }
        runCurrent()
        assertEquals(Link.Down(Link.Why.Refused), c.link)
        following.cancelAndJoin()
    }

    @Test
    fun olderLinesAreFetchedAPageAtATimeUntilTheStart() = runTest {
        val server = FakeServer().apply { say(450) }
        val c = conversation(server)
        val following = launch { c.follow() }
        runCurrent()
        assertEquals(251, c.window.firstSeq)

        c.loadOlder()
        assertEquals(51, c.window.firstSeq)
        assertTrue(c.window.olderRemain)

        c.loadOlder()
        assertEquals(1, c.window.firstSeq)
        assertEquals(false, c.window.olderRemain)

        val asks = server.asked.size
        c.loadOlder()
        assertEquals(asks, server.asked.size, "at the start there is nothing more to ask for")
        following.cancelAndJoin()
    }

    @Test
    fun anOlderPageFromAnotherRecordIsAFailureNotAnEndlessWait() = runTest {
        val server = FakeServer().apply { say(450) }
        val c = conversation(server)
        val following = launch { c.follow() }
        runCurrent()
        following.cancelAndJoin()

        // The record is replaced while nobody is following; the older page comes from the new one.
        server.epoch = "B"
        c.loadOlder()
        assertTrue(c.olderFailed)
        assertEquals(251, c.window.firstSeq)
    }

    @Test
    fun aFailedOlderPageIsReportedAndCanBeAskedForAgain() = runTest {
        val server = FakeServer().apply { say(450) }
        val c = conversation(server)
        val following = launch { c.follow() }
        runCurrent()
        following.cancelAndJoin()

        server.failure = ApiFailure.Unreachable()
        c.loadOlder()
        assertTrue(c.olderFailed)
        assertEquals(251, c.window.firstSeq)

        server.failure = null
        c.loadOlder()
        assertEquals(false, c.olderFailed)
        assertEquals(51, c.window.firstSeq)
    }
}
