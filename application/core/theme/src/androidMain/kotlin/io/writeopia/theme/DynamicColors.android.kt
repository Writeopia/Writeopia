package io.writeopia.theme

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun getDynamicAccentColors(): DynamicAccentColors? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        val lightScheme = dynamicLightColorScheme(context)
        val darkScheme = dynamicDarkColorScheme(context)
        DynamicAccentColors(
            lightPrimary = lightScheme.primary,
            lightSecondary = lightScheme.secondary,
            darkPrimary = darkScheme.primary,
            darkSecondary = darkScheme.secondary
        )
    } else {
        null
    }
}
