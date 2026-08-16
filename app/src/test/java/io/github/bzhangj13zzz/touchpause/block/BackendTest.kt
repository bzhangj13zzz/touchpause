package io.github.bzhangj13zzz.touchpause.block

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BackendTest {
    @Test
    fun persistedValuesRoundTripToRuntimeBackends() {
        Backend.values().forEach { backend ->
            assertSame(backend, Backend.fromPersistedValue(backend.persistedValue))
        }
    }

    @Test
    fun missingOrUnknownPersistedValueHasNoRuntimeOwner() {
        assertNull(Backend.fromPersistedValue(null))
        assertNull(Backend.fromPersistedValue(""))
        assertNull(Backend.fromPersistedValue("touchpause"))
        assertNull(Backend.fromPersistedValue("ROOT"))
    }
}
