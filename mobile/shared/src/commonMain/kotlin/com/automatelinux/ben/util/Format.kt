package com.automatelinux.ben.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun String.titled(): String = lowercase().replaceFirstChar { it.uppercase() }

/** "Today", "Yesterday", "Saturday 3 October" — with the year once it is not this one. */
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
    else -> {
        val base = "${date.dayOfWeek.name.titled()} ${date.dayOfMonth} ${date.month.name.titled()}"
        if (date.year == today.year) base else "$base ${date.year}"
    }
}

/** "17:05" — 24-hour, the way the phone's own clock shows it. */
fun clock(ts: Long, zone: TimeZone): String {
    val t = Instant.fromEpochMilliseconds(ts).toLocalDateTime(zone)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/** How long something took or has been running: "9 s", "1 min 12 s". */
fun spanLabel(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 500) / 1000
    return when {
        s < 1 -> "under 1 s"
        s < 60 -> "$s s"
        s % 60 == 0L -> "${s / 60} min"
        else -> "${s / 60} min ${s % 60} s"
    }
}

/** The silence between two conversations: "45 min later", "3 h later", "2 h 20 min later". */
fun gapLabel(minutes: Long): String {
    if (minutes < 60) return "$minutes min later"
    val h = minutes / 60
    val m = minutes % 60 / 5 * 5     // to the five minutes: nobody cares that it was 2 h 23
    return if (m == 0L) "$h h later" else "$h h $m min later"
}

/** Why a turn has no answer, in a few words. */
fun failureLabel(reason: String?): String = when (reason) {
    "timeout" -> "Ran out of time"
    "interrupted" -> "Cut off by a restart"
    else -> "The turn failed"
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

/**
 * What Ben had the phone do, in a few words: `{"type":"open_app","package_hint":"waze"}` reads
 * "Opened Waze". The types are the server's `ACTION_TYPES`; one this build does not know is shown
 * by its own name rather than dropped.
 */
fun actionLabel(action: JsonObject): String {
    val type = action.text("type") ?: return "Phone action"
    return when (type) {
        // A hint may be a package id — "com.whatsapp" — and the last part is the name.
        "open_app" -> action.text("package_hint")?.let { "Opened ${it.substringAfterLast('.').titled()}" } ?: "Opened an app"
        "navigate" -> action.text("destination")?.let { "Navigating to $it" } ?: "Navigating"
        "call" -> action.text("number")?.let { "Calling $it" } ?: "Calling"
        "web_search" -> action.text("query")?.let { "Searched “$it”" } ?: "Web search"
        "type_text" -> action.text("text")?.let { "Typed “$it”" } ?: "Typed text"
        "go_home" -> "Home screen"
        "go_back" -> "Back"
        "open_settings" -> "Opened Settings"
        "volume" -> action.text("level")?.let { "Volume $it" } ?: "Volume"
        "flashlight" -> action.text("state")?.let { "Flashlight $it" } ?: "Flashlight"
        "set_alarm" -> action.text("time")?.let { "Alarm at $it" } ?: "Alarm"
        "set_timer" -> action.text("minutes")?.let { "Timer, $it min" } ?: "Timer"
        else -> type.replace('_', ' ').titled()
    }
}
