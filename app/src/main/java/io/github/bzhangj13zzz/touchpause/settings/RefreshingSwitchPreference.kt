package io.github.bzhangj13zzz.touchpause.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat

/** A normal switch preference that permits its host to request a defensive widget rebind. */
class RefreshingSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes) {
    /** Rebinds the visible switch from its already-persisted checked state. */
    fun refreshWidget() {
        notifyChanged()
    }
}
