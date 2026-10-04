package com.automatelinux.ben.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.automatelinux.ben.data.Conversation
import com.automatelinux.ben.data.Link
import com.automatelinux.ben.data.Row as Line
import com.automatelinux.ben.data.localDate
import com.automatelinux.ben.data.timeline
import com.automatelinux.ben.data.turnsOf
import com.automatelinux.ben.ui.theme.LocalPalette
import com.automatelinux.ben.ui.theme.LocalType
import com.automatelinux.ben.util.dayLabel
import com.automatelinux.ben.util.gapLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The one screen: the conversation held through the wake phrase, newest at the bottom. */
@Composable
fun ConversationScreen(c: Conversation, now: () -> Long, platformConfirmsCopy: Boolean) {
    val p = LocalPalette.current
    val zone = remember { TimeZone.currentSystemDefault() }
    val events = c.window.events
    val lines = remember(events, zone) { timeline(turnsOf(events), zone) }
    val link = c.link

    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var copies by remember { mutableStateOf(0) }
    var copiedShown by remember { mutableStateOf(false) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        copiedShown = true
        delay(1_400)
        copiedShown = false
    }
    val copy: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // Android 13 and later show what was copied by themselves, in the same corner; a
        // second note under it would only be hidden by the first.
        if (!platformConfirmsCopy) copies++
    }

    Column(Modifier.fillMaxSize().background(p.ground)) {
        TopBar(link, onRetry = c::retryNow)

        // Only above a saved copy. With nothing to show, the whole screen says it instead.
        AnimatedVisibility(link is Link.Down && lines.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            (link as? Link.Down)?.let { DownBanner(it.why, onRetry = c::retryNow) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // Neither the saved copy nor the server has spoken: there is nothing to say yet.
                !c.answered -> EmptyState("Opening the conversation", "Asking the desktop for what has been said.", moving = true)

                lines.isEmpty() && link is Link.Down -> EmptyState(
                    title = when (link.why) {
                        Link.Why.Unreachable -> "Can't reach the desktop"
                        Link.Why.Refused -> "The desktop refused this app"
                        Link.Why.Broken -> "The desktop answered with an error"
                    },
                    body = when (link.why) {
                        Link.Why.Unreachable -> "The conversation is kept there, and the phone reaches it over the private network. Nothing has been saved on this phone yet."
                        Link.Why.Refused -> "The key built into this app no longer matches the one on the desktop. The app needs to be rebuilt with the current key."
                        Link.Why.Broken -> "The voice server is up but did not hand over the conversation. Its log on the desktop will say why."
                    },
                    lit = false,
                    action = { PrimaryAction("Try again", "retry-connection", c::retryNow) },
                )

                lines.isEmpty() -> EmptyState(
                    "Nothing said yet",
                    "Say the wake phrase and talk to Ben. What you say and what he answers will appear here as it happens.",
                )

                else -> Transcript(c, lines, zone, now, copy)
            }

            // Named in full: inside this Box the enclosing Column's own AnimatedVisibility would win.
            androidx.compose.animation.AnimatedVisibility(
                visible = copiedShown,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(WindowInsets.navigationBars.asPaddingValues()).padding(bottom = 24.dp),
            ) {
                FloatingNote("Copied")
            }
        }
    }
}

@Composable
private fun Transcript(c: Conversation, lines: List<Line>, zone: TimeZone, now: () -> Long, onCopy: (String) -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val today = localDate(now(), zone)

    // Newest first, laid out from the bottom up. That way the list opens on the latest line,
    // an answer arriving grows upward from the bottom edge, and older pages are added at the
    // far end — where they cannot push what he is reading.
    val newestFirst = remember(lines) { lines.asReversed() }
    val dayOf = remember(lines) {
        buildMap<String, LocalDate> {
            var day: LocalDate? = null
            for (line in lines) {
                if (line is Line.Day) day = line.date
                day?.let { put(line.key, it) }
            }
        }
    }

    val state = rememberLazyListState()
    val atLatest by remember { derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 } }

    // "Following" is where he left the list, not where it happens to be this frame: a new line
    // moves the list off the bottom before anything can react to it.
    var following by remember { mutableStateOf(true) }
    var unseen by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling -> if (!scrolling) following = atLatest }
    }
    LaunchedEffect(atLatest) { if (atLatest) unseen = false }

    val newest = c.window.lastSeq
    var shown by remember { mutableStateOf(newest) }
    LaunchedEffect(newest) {
        if (newest == shown) return@LaunchedEffect
        shown = newest
        if (following) state.animateScrollToItem(0) else unseen = true
    }

    // The day, floated at the top while the list is moving — the day rows themselves scroll away.
    val topDay by remember(dayOf) {
        derivedStateOf {
            val top = state.layoutInfo.visibleItemsInfo.lastOrNull()
            (top?.key as? String)?.takeIf { !it.startsWith("day-") }?.let { dayOf[it] }
        }
    }
    var dayShown by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                dayShown = true
            } else {
                delay(900)
                dayShown = false
            }
        }
    }

    val connected = c.link !is Link.Down
    val serverNow = { now() + c.skewMs }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().testTag("transcript"),
            contentPadding = PaddingValues(top = 10.dp, bottom = bottomInset + 22.dp),
        ) {
            items(newestFirst, key = { it.key }, contentType = { it::class }) { line ->
                when (line) {
                    is Line.Day -> DayRow(dayLabel(line.date, today), Modifier.animateItem())
                    is Line.Gap -> GapRow(gapLabel(line.minutes), Modifier.animateItem())
                    is Line.Exchange -> ExchangeRow(line.turn, zone, connected, serverNow, onCopy, Modifier.animateItem())
                }
            }
            item(key = "top", contentType = "top") { Top(c) }
        }

        // A hairline under the top bar once lines have scrolled beneath it.
        if (state.canScrollForward) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))

        AnimatedVisibility(
            visible = dayShown && topDay != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        ) {
            // Keep the last label through the fade-out, when topDay may already be null.
            var label by remember { mutableStateOf("") }
            topDay?.let { label = dayLabel(it, today) }
            FloatingNote(label)
        }

        AnimatedVisibility(
            visible = !atLatest,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = bottomInset + 18.dp),
        ) {
            JumpToLatest(unseen, onClick = { scope.launch { state.animateScrollToItem(0) } })
        }
    }
}

/** The top of what is held: more to fetch, a fetch that failed, or the first line there is. */
@Composable
private fun Top(c: Conversation) {
    val p = LocalPalette.current
    val t = LocalType.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            c.window.olderRemain && c.olderFailed -> {
                Text("Couldn't load earlier lines", style = t.meta, color = p.soft)
                TextAction("Retry", "retry-older") { scope.launch { c.loadOlder() } }
            }

            c.window.olderRemain -> {
                // This row being on screen is the request for the page above it.
                LaunchedEffect(c.window.firstSeq) { c.loadOlder() }
                VoiceBars(p.faint, height = 12.dp, moving = true)
                Spacer(Modifier.width(10.dp))
                Text("Loading earlier lines", style = t.meta, color = p.faint)
            }

            else -> {
                VoiceBars(p.faint, height = 12.dp)
                Spacer(Modifier.width(10.dp))
                Text("The conversation starts here", style = t.meta, color = p.faint)
            }
        }
    }
}

@Composable
private fun DayRow(label: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val t = LocalType.current
    Box(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, style = t.label, color = p.soft)
    }
}

/** A silence between two conversations on the same day. */
@Composable
private fun GapRow(label: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(p.hairline))
        Text(label, style = t.meta, color = p.faint, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(p.hairline))
    }
}
