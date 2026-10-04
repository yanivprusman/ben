package com.automatelinux.ben.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.ben.data.Link
import com.automatelinux.ben.ui.theme.LocalPalette
import com.automatelinux.ben.ui.theme.LocalType

/** The name, and whether the line to the desktop is open. */
@Composable
fun TopBar(link: Link, onRetry: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(
        Modifier.fillMaxWidth().background(p.ground).statusBarsPadding().height(62.dp).padding(start = 22.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Ben", style = t.wordmark, color = p.ink)
        Spacer(Modifier.weight(1f))
        LinkPill(link, onRetry)
    }
}

/** A lamp and one word. When the line is down, the pill is also the way to try again. */
@Composable
private fun LinkPill(link: Link, onRetry: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val down = link is Link.Down
    Row(
        Modifier
            .testTag("link-status")
            .clip(CircleShape)
            .border(1.dp, p.hairline, CircleShape)
            .then(if (down) Modifier.clickable(role = Role.Button, onClickLabel = "Try again", onClick = onRetry) else Modifier)
            .padding(start = 11.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(link)
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (link) {
                Link.Live -> "Live"
                Link.Connecting -> "Connecting"
                is Link.Down -> "Offline"
            },
            style = t.label,
            color = if (link == Link.Live) p.ink else p.soft,
        )
    }
}

/** Lit and breathing when live, dim and breathing while connecting, an empty ring when down. */
@Composable
private fun Lamp(link: Link) {
    val p = LocalPalette.current
    val breath = if (link is Link.Down) {
        1f
    } else {
        val b by rememberInfiniteTransition().animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (link == Link.Live) 1600 else 700), RepeatMode.Reverse),
        )
        b
    }
    Canvas(Modifier.size(14.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = 4.dp.toPx()
        when (link) {
            Link.Live -> {
                drawCircle(p.voice.copy(alpha = 0.28f * breath), radius = size.width / 2, center = c)
                drawCircle(p.voice, radius = r, center = c)
            }
            Link.Connecting -> drawCircle(p.soft.copy(alpha = breath), radius = r, center = c)
            is Link.Down -> drawCircle(p.faint, radius = r - 0.75.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** Shown above a saved copy: why it is not live, and a way to try again. */
@Composable
fun DownBanner(why: Link.Why, onRetry: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(
        Modifier.fillMaxWidth().background(p.bubble).padding(start = 22.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (why) {
                Link.Why.Unreachable -> "Can't reach the desktop. This is the saved copy."
                Link.Why.Refused -> "The desktop refused this app's key. This is the saved copy."
                Link.Why.Broken -> "The desktop answered with an error. This is the saved copy."
            },
            style = t.label,
            color = p.soft,
            modifier = Modifier.weight(1f),
        )
        TextAction("Retry", "retry-connection", onRetry)
    }
}

@Composable
fun TextAction(label: String, tag: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Box(
        Modifier.testTag(tag).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = t.label, color = p.voice)
    }
}

/** A filled button, for the one thing an empty screen offers. */
@Composable
fun PrimaryAction(label: String, tag: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Box(
        Modifier.testTag(tag).clip(CircleShape).background(p.ink).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(label, style = t.label, color = p.ground)
    }
}

/** Back to the newest line. The dot means something was said while he was reading further up. */
@Composable
fun JumpToLatest(unseen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier.size(46.dp)) {
        Box(
            Modifier
                .testTag("jump-to-latest")
                .fillMaxSize()
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(p.raised)
                .border(1.dp, p.hairline, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Jump to the latest", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Jump to the latest", tint = p.ink, modifier = Modifier.size(26.dp))
        }
        if (unseen) {
            Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(p.ground).padding(2.dp).clip(CircleShape).background(p.voice))
        }
    }
}

/** A small floating label: the day while scrolling, "Copied" after a hold. */
@Composable
fun FloatingNote(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val t = LocalType.current
    Box(
        modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(p.raised).border(1.dp, p.hairline, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, style = t.label, color = p.soft)
    }
}

/** An empty screen: the mark, what is going on, and at most one thing to do about it. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    moving: Boolean = false,
    lit: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    val p = LocalPalette.current
    val t = LocalType.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(p.bubble), contentAlignment = Alignment.Center) {
            VoiceBars(if (lit) p.voice else p.faint, height = 34.dp, moving = moving)
        }
        Spacer(Modifier.height(26.dp))
        Text(title, style = t.title, color = p.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(body, style = t.body, color = p.soft, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        if (action != null) {
            Spacer(Modifier.height(26.dp))
            action()
        }
        // Sit a little above centre: the top bar already weighs the screen down.
        Spacer(Modifier.height(72.dp))
    }
}
