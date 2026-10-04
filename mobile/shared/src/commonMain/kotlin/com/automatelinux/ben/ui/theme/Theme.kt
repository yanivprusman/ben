package com.automatelinux.ben.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A spoken conversation, written down.
//
// Ink and paper, and one colour. The coral is Ben's voice and nothing else: his mark beside each
// answer, the trace that moves while he is working, the lamp that says the line is open. What was
// heard, the times, the days — all of that is neutral, so the eye lands on what he said.

/** The typefaces, handed in by the platform (Android reads them from res/font). */
@Immutable
data class Fonts(
    /** What was heard, and every label: Instrument Sans. */
    val sans: FontFamily,
    /** What Ben said: Newsreader, cut for text sizes. */
    val serif: FontFamily,
    /** The wordmark: Newsreader's display cut. */
    val display: FontFamily,
)

@Immutable
data class Palette(
    val dark: Boolean,
    /** The page. */
    val ground: Color,
    /** The bubble a heard sentence sits in. */
    val bubble: Color,
    /** Things that float above the page: the jump button, the copied note. */
    val raised: Color,
    val ink: Color,
    /** Times, labels, what was heard-but-secondary. */
    val soft: Color,
    /** The quietest text, and marks that are switched off. */
    val faint: Color,
    val hairline: Color,
    /** Ben's voice. */
    val voice: Color,
    /** The voice colour as a wash, behind a chip. */
    val voiceWash: Color,
)

private val Night = Palette(
    dark = true,
    ground = Color(0xFF0F1113),
    bubble = Color(0xFF1E2226),
    raised = Color(0xFF272C31),
    ink = Color(0xFFECE9E4),
    soft = Color(0xFF969DA5),
    faint = Color(0xFF5F666E),
    hairline = Color(0xFF24282D),
    voice = Color(0xFFFF7A59),
    voiceWash = Color(0x24FF7A59),
)

private val Day = Palette(
    dark = false,
    ground = Color(0xFFF6F4F0),
    bubble = Color(0xFFE9E5DE),
    raised = Color(0xFFFFFFFF),
    ink = Color(0xFF17191B),
    soft = Color(0xFF666C73),
    faint = Color(0xFF9DA2A8),
    hairline = Color(0xFFE1DDD5),
    voice = Color(0xFFD9482B),
    voiceWash = Color(0x1FD9482B),
)

@Immutable
data class Type(
    val wordmark: TextStyle,
    /** Ben's answers. */
    val said: TextStyle,
    /** A failed answer: the sentence the phone spoke instead. */
    val saidFailed: TextStyle,
    /** What was heard. */
    val heard: TextStyle,
    /** Titles of the empty states. */
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    /** Times and durations — figures of equal width, so a ticking count does not jitter. */
    val meta: TextStyle,
)

private fun typeOf(f: Fonts) = Type(
    wordmark = TextStyle(fontFamily = f.display, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    said = TextStyle(fontFamily = f.serif, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 28.sp),
    saidFailed = TextStyle(fontFamily = f.serif, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 26.sp),
    heard = TextStyle(fontFamily = f.sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    title = TextStyle(fontFamily = f.serif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    body = TextStyle(fontFamily = f.sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    label = TextStyle(fontFamily = f.sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    meta = TextStyle(fontFamily = f.sans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
)

val LocalPalette = staticCompositionLocalOf<Palette> { error("no palette") }
val LocalType = staticCompositionLocalOf<Type> { error("no type") }

@Composable
fun AppTheme(fonts: Fonts, content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Night else Day
    // Material is only here for ripples and the text-selection handles; give it the same colours.
    val scheme = if (p.dark) {
        darkColorScheme(primary = p.voice, background = p.ground, surface = p.ground, onBackground = p.ink, onSurface = p.ink)
    } else {
        lightColorScheme(primary = p.voice, background = p.ground, surface = p.ground, onBackground = p.ink, onSurface = p.ink)
    }
    CompositionLocalProvider(LocalPalette provides p, LocalType provides typeOf(fonts)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
