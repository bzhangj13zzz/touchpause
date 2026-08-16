package io.github.bzhangj13zzz.touchpause.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReleaseKeyTest {
    @Test
    fun persistedValuesMapToReleaseKeys() {
        assertSame(ReleaseKey.VOLUME_DOWN, ReleaseKey.fromPreference("1"))
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference("2"))

        assertEquals("1", ReleaseKey.VOLUME_DOWN.persistedValue)
        assertEquals("2", ReleaseKey.VOLUME_UP.persistedValue)
    }

    @Test
    fun missingOrInvalidValueFallsBackToVolumeUp() {
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference(null))
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference(""))
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference("3"))
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference("2; reboot"))
    }
}
