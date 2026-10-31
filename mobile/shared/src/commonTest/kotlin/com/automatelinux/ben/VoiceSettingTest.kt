package com.automatelinux.ben

import com.automatelinux.ben.data.ApiFailure
import com.automatelinux.ben.data.SettingsSource
import com.automatelinux.ben.data.Sound
import com.automatelinux.ben.data.VoiceSetting
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class FakeSettings(var stored: String = VoiceSetting.PHONE, var down: Boolean = false) : SettingsSource {
    var writes = 0
    var volume = 100
    override suspend fun sound(): Sound = if (down) throw ApiFailure.Unreachable() else Sound(stored, volume)
    override suspend fun setVoice(voice: String): Sound {
        if (down) throw ApiFailure.Unreachable()
        writes++
        stored = voice
        return Sound(stored, volume)
    }
    override suspend fun setVolume(volume: Int): Sound {
        if (down) throw ApiFailure.Unreachable()
        writes++
        this.volume = volume
        return Sound(stored, volume)
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

    @Test
    fun theVolumeIsWhatTheServerConfirms() = runTest {
        val src = FakeSettings()
        val s = VoiceSetting(src)
        assertNull(s.volume)
        s.load()
        assertEquals(100, s.volume)
        s.chooseVolume(160)
        assertEquals(160, s.volume)
        assertEquals(160, src.volume)
        assertNull(s.savingVolume)
    }

    @Test
    fun onlyTheStopsAreSentAndTheSameLevelSendsNothing() = runTest {
        val src = FakeSettings()
        val s = VoiceSetting(src)
        s.load()
        s.chooseVolume(100)
        s.chooseVolume(137)
        assertEquals(0, src.writes)
    }

    @Test
    fun aFailedVolumeChangeKeepsTheOldLevel() = runTest {
        val src = FakeSettings()
        val s = VoiceSetting(src)
        s.load()
        src.down = true
        s.chooseVolume(250)
        assertEquals(100, s.volume)
        assertIs<ApiFailure.Unreachable>(s.failure)
    }
}
