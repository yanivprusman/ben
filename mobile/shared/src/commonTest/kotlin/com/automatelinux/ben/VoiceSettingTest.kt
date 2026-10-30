package com.automatelinux.ben

import com.automatelinux.ben.data.ApiFailure
import com.automatelinux.ben.data.SettingsSource
import com.automatelinux.ben.data.VoiceSetting
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class FakeSettings(var stored: String = VoiceSetting.PHONE, var down: Boolean = false) : SettingsSource {
    var writes = 0
    override suspend fun voice(): String = if (down) throw ApiFailure.Unreachable() else stored
    override suspend fun setVoice(voice: String): String {
        if (down) throw ApiFailure.Unreachable()
        writes++
        stored = voice
        return stored
    }
}

class VoiceSettingTest {
    @Test
    fun unknownUntilTheServerSays() = runTest {
        val s = VoiceSetting(FakeSettings(stored = VoiceSetting.DESKTOP))
        assertNull(s.current)
        s.load()
        assertEquals(VoiceSetting.DESKTOP, s.current)
    }

    @Test
    fun aChoiceIsWhatTheServerConfirms() = runTest {
        val src = FakeSettings()
        val s = VoiceSetting(src)
        s.load()
        s.choose(VoiceSetting.DESKTOP)
        assertEquals(VoiceSetting.DESKTOP, s.current)
        assertEquals(VoiceSetting.DESKTOP, src.stored)
        assertNull(s.saving)
    }

    @Test
    fun choosingTheCurrentVoiceSendsNothing() = runTest {
        val src = FakeSettings()
        val s = VoiceSetting(src)
        s.load()
        s.choose(VoiceSetting.PHONE)
        assertEquals(0, src.writes)
    }

    @Test
    fun aFailedChangeKeepsTheOldValueAndSaysWhy() = runTest {
        val src = FakeSettings()
        val s = VoiceSetting(src)
        s.load()
        src.down = true
        s.choose(VoiceSetting.DESKTOP)
        assertEquals(VoiceSetting.PHONE, s.current)
        assertIs<ApiFailure.Unreachable>(s.failure)
        assertNull(s.saving)
    }
}
