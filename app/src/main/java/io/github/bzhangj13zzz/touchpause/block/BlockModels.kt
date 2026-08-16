package io.github.bzhangj13zzz.touchpause.block

/** Hardware volume key used to release touch. */
enum class ReleaseKey(val persistedValue: String) {
    VOLUME_DOWN("1"),
    VOLUME_UP("2");

    companion object {
        val DEFAULT = VOLUME_UP

        /** Invalid, missing, and legacy Power-key values safely fall back to Volume Up. */
        fun fromPreference(value: String?): ReleaseKey =
            values().firstOrNull { it.persistedValue == value } ?: DEFAULT
    }
}

/** Feedback choices captured at the start of one blocking session. */
data class FeedbackOptions(
    val showStartMessage: Boolean = true,
    val vibrateOnStart: Boolean = true,
    val showStopMessage: Boolean = true,
    val vibrateOnStop: Boolean = true
)
