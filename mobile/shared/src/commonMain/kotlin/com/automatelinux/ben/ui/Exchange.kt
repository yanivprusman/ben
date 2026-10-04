package com.automatelinux.ben.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.ben.data.Turn
import com.automatelinux.ben.data.model.Event
import com.automatelinux.ben.ui.theme.LocalPalette
import com.automatelinux.ben.ui.theme.LocalType
import com.automatelinux.ben.util.actionLabel
import com.automatelinux.ben.util.clock
import com.automatelinux.ben.util.failureLabel
import com.automatelinux.ben.util.spanLabel
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone

/** The column Ben's mark stands in; his text starts where it ends. */
private val MARK_COLUMN = 30.dp

/**
 * The longest a turn can run on the server: its own limit, twice (the retry on the fallback
 * model), plus slack. A sentence with no answer after this long is not being worked on —
 * whatever the saved copy says.
 */
private const val TURN_LIMIT_MS = 7 * 60_000L

/** Hold a finger on a line to copy it. No ripple on a tap: a tap does nothing, and should look it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.copyOnHold(text: String, tag: String, onCopy: (String) -> Unit): Modifier =
    this.testTag(tag).combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onLongClickLabel = "Copy",
        onLongClick = { onCopy(text) },
        onClick = {},
    )

/**
 * One exchange: the sentence as the phone heard it, set to the right in a bubble; and under it,
 * to the left and in his own typeface, what Ben said.
 */
@Composable
fun ExchangeRow(
    turn: Turn,
    zone: TimeZone,
    /** Whether the server can be heard. A turn cannot be "working" on a line that is down. */
    connected: Boolean,
    serverNow: () -> Long,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 11.dp)) {
        turn.heard?.let { Heard(it, zone, onCopy) }
        if (turn.heard != null) Spacer(Modifier.height(12.dp))

        val outcome = turn.outcome
        when {
            outcome == null -> Waiting(turn.heard!!, connected, serverNow)
            turn.failed -> Failed(outcome, turn.tookMs)
            else -> Said(outcome, turn.tookMs, zone, onCopy)
        }
    }
}

@Composable
private fun Heard(heard: Event, zone: TimeZone, onCopy: (String) -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Column(Modifier.fillMaxWidth().padding(start = 52.dp), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                // Three round corners and one tight: the bubble leans toward the side that spoke.
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp))
                .background(p.bubble)
                .copyOnHold(heard.text, "heard-text", onCopy)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(heard.text, style = t.heard, color = p.ink)
        }
        Text(clock(heard.ts, zone), style = t.meta, color = p.faint, modifier = Modifier.padding(top = 5.dp, end = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Said(reply: Event, tookMs: Long?, zone: TimeZone, onCopy: (String) -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Box(Modifier.width(MARK_COLUMN).padding(top = 7.dp)) { VoiceBars(p.voice) }
        Column(Modifier.weight(1f)) {
            Text(reply.text, style = t.said, color = p.ink, modifier = Modifier.copyOnHold(reply.text, "reply-text", onCopy))
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // With both ends of the turn: how long he took. With only his half (the page
                // boundary fell between them): when he said it.
                Text(
                    text = tookMs?.let { "Answered in ${spanLabel(it)}" } ?: clock(reply.ts, zone),
                    style = t.meta,
                    color = p.faint,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                for (action in reply.actions) ActionChip(actionLabel(action))
            }
        }
    }
}

/** Something Ben had the phone do alongside his answer. */
@Composable
private fun ActionChip(label: String) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(
        Modifier.clip(CircleShape).border(1.dp, p.hairline, CircleShape).padding(start = 7.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = p.faint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = t.meta, color = p.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Failed(outcome: Event, tookMs: Long?) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Box(Modifier.width(MARK_COLUMN).padding(top = 6.dp)) { VoiceBars(p.faint) }
        Column(Modifier.weight(1f)) {
            Text(outcome.text, style = t.saidFailed, color = p.soft)
            Text(
                text = failureLabel(outcome.reason) + (tookMs?.let { " after ${spanLabel(it)}" } ?: ""),
                style = t.meta,
                color = p.faint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A sentence with no answer yet: either Ben is on it, or nobody can say. */
@Composable
private fun Waiting(heard: Event, connected: Boolean, serverNow: () -> Long) {
    val p = LocalPalette.current
    val t = LocalType.current
    val elapsed by produceState(initialValue = serverNow() - heard.ts, heard.ts) {
        while (true) {
            value = serverNow() - heard.ts
            delay(1_000)
        }
    }
    val working = connected && elapsed < TURN_LIMIT_MS

    Row(Modifier.fillMaxWidth().testTag(if (working) "turn-working" else "turn-unanswered"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(MARK_COLUMN)) { VoiceBars(if (working) p.voice else p.faint, moving = working) }
        if (working) {
            Text("Working on it", style = t.body, color = p.soft)
            // Nothing for the first second: "under 1 s" is a strange thing to say about a wait.
            if (elapsed >= 1_000) {
                Spacer(Modifier.width(10.dp))
                Text(spanLabel(elapsed), style = t.meta, color = p.faint)
            }
        } else {
            Text(
                // Offline, the answer may exist and simply not have reached the phone.
                text = if (connected) "No answer was recorded" else "No answer yet — waiting for the connection",
                style = t.body,
                color = p.soft,
            )
        }
    }
}
