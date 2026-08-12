package io.github.bzhangj13zzz.touchquell.block

/** Identifies the implementation that currently owns touch blocking. */
enum class Backend(val persistedValue: String) {
    ROOT("root"),
    ACCESSIBILITY("accessibility");

    companion object {
        /** Returns null for missing or unknown persisted values. */
        fun fromPersistedValue(value: String?): Backend? =
            values().firstOrNull { it.persistedValue == value }
    }
}

/**
 * Hardware key used to release touch.
 *
 * [nativeValue] is the stable value understood by the bundled root helper. Power is not a safe
 * accessibility-service release key, so it always selects the root backend.
 */
enum class ReleaseKey(val nativeValue: String, val supportsAccessibility: Boolean) {
    VOLUME_DOWN("1", true),
    VOLUME_UP("2", true),
    POWER("3", false);

    companion object {
        val DEFAULT = VOLUME_UP

        /** Invalid or missing preference values safely fall back to Volume Up. */
        fun fromPreference(value: String?): ReleaseKey =
            values().firstOrNull { it.nativeValue == value } ?: DEFAULT
    }
}

/** Feedback choices captured at the start of one blocking session. */
data class FeedbackOptions(
    val showStartMessage: Boolean = true,
    val vibrateOnStart: Boolean = true,
    val showStopMessage: Boolean = true,
    val vibrateOnStop: Boolean = true
)
