package io.writeopia.common.utils.configuration

import androidx.compose.runtime.staticCompositionLocalOf

enum class PlatformType {
    MOBILE_PORTRAIT,
    DESKTOP,
    WEB;

    fun isMobile(): Boolean =
        when (this) {
            MOBILE_PORTRAIT -> true
            DESKTOP, WEB -> false
        }

    fun isDesktop(): Boolean = !isMobile()
}

val LocalPlatform = staticCompositionLocalOf<PlatformType> {
    error("No PlatformType provided")
}
