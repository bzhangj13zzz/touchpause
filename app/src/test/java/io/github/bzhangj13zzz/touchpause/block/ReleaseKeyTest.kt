package io.github.bzhangj13zzz.touchpause.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseKeyTest {
    @Test
    fun nativeProtocolValuesMapToReleaseKeys() {
        assertSame(ReleaseKey.VOLUME_DOWN, ReleaseKey.fromPreference("1"))
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference("2"))
        assertSame(ReleaseKey.POWER, ReleaseKey.fromPreference("3"))

        assertEquals("1", ReleaseKey.VOLUME_DOWN.nativeValue)
        assertEquals("2", ReleaseKey.VOLUME_UP.nativeValue)
        assertEquals("3", ReleaseKey.POWER.nativeValue)
    }

    @Test
    fun missingOrInvalidValueFallsBackToVolumeUp() {
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference(null))
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference(""))
        assertSame(ReleaseKey.VOLUME_UP, ReleaseKey.fromPreference("2; reboot"))
    }

    @Test
    fun onlyVolumeKeysSupportAccessibilityRelease() {
        assertTrue(ReleaseKey.VOLUME_DOWN.supportsAccessibility)
        assertTrue(ReleaseKey.VOLUME_UP.supportsAccessibility)
        assertFalse(ReleaseKey.POWER.supportsAccessibility)
    }
}
