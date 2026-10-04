package com.automatelinux.ben

import com.automatelinux.ben.util.actionLabel
import com.automatelinux.ben.util.clock
import com.automatelinux.ben.util.dayLabel
import com.automatelinux.ben.util.failureLabel
import com.automatelinux.ben.util.gapLabel
import com.automatelinux.ben.util.spanLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    private val today = LocalDate(2026, 10, 4)
    private fun action(json: String) = actionLabel(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun days() {
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(LocalDate(2026, 10, 3), today))
        assertEquals("Friday 2 October", dayLabel(LocalDate(2026, 10, 2), today))
        assertEquals("Wednesday 31 December 2025", dayLabel(LocalDate(2025, 12, 31), today))
    }

    @Test
    fun theClockIsTwentyFourHourAndPadded() {
        // 2026-10-04 14:05:09 UTC is 17:05 in Israel.
        val ts = 1_791_122_709_000L
        assertEquals("17:05", clock(ts, TimeZone.of("Asia/Jerusalem")))
        assertEquals("14:05", clock(ts, TimeZone.UTC))
    }

    @Test
    fun spans() {
        assertEquals("under 1 s", spanLabel(300))
        assertEquals("9 s", spanLabel(9_400))
        assertEquals("10 s", spanLabel(9_600))
        assertEquals("1 min", spanLabel(60_000))
        assertEquals("1 min 12 s", spanLabel(72_000))
        assertEquals("under 1 s", spanLabel(-5_000), "a phone clock running ahead must not show a negative time")
    }

    @Test
    fun gaps() {
        assertEquals("45 min later", gapLabel(45))
        assertEquals("3 h later", gapLabel(182))
        assertEquals("2 h 20 min later", gapLabel(143))
    }

    @Test
    fun failures() {
        assertEquals("Ran out of time", failureLabel("timeout"))
        assertEquals("Cut off by a restart", failureLabel("interrupted"))
        assertEquals("The turn failed", failureLabel("error"))
        assertEquals("The turn failed", failureLabel(null))
    }

    @Test
    fun actionsReadAsWhatThePhoneDid() {
        assertEquals("Opened Waze", action("""{"type":"open_app","package_hint":"waze"}"""))
        assertEquals("Opened Whatsapp", action("""{"type":"open_app","package_hint":"com.whatsapp"}"""))
        assertEquals("Opened Settings", action("""{"type":"open_settings"}"""))
        assertEquals("Navigating to תחנת דלק", action("""{"type":"navigate","destination":"תחנת דלק"}"""))
        assertEquals("Timer, 10 min", action("""{"type":"set_timer","minutes":"10"}"""))
    }

    @Test
    fun anActionThisBuildDoesNotKnowIsShownByName() {
        assertEquals("Take photo", action("""{"type":"take_photo"}"""))
    }
}
