package com.dynorixz.buttonscontrol.core

/** Global holds do not require an accessible watch-face view hierarchy. */
object HoldAvailability {
    fun allowed(interactive: Boolean, foregroundPackage: String?, excluded: Set<String>): Boolean =
        interactive && (excluded.isEmpty() || (!foregroundPackage.isNullOrBlank() && foregroundPackage !in excluded))
}
