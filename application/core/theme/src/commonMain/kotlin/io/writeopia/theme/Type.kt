package io.writeopia.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.writeopia.resources.Fonts
import org.jetbrains.compose.resources.Font

val Typography = Typography(
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
)

@Composable
fun robotoTypography(): Typography {
    val robotoFamily = FontFamily(
        Font(Fonts.robotoRegular, weight = FontWeight.Normal)
    )

    // Get default typography and copy each style with Roboto font family
    val defaults = Typography()

    return Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = robotoFamily),
        displayMedium = defaults.displayMedium.copy(fontFamily = robotoFamily),
        displaySmall = defaults.displaySmall.copy(fontFamily = robotoFamily),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = robotoFamily),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = robotoFamily),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = robotoFamily),
        titleLarge = defaults.titleLarge.copy(fontFamily = robotoFamily),
        titleMedium = defaults.titleMedium.copy(fontFamily = robotoFamily),
        titleSmall = defaults.titleSmall.copy(fontFamily = robotoFamily),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = robotoFamily),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = robotoFamily),
        bodySmall = defaults.bodySmall.copy(fontFamily = robotoFamily),
        labelLarge = defaults.labelLarge.copy(fontFamily = robotoFamily),
        labelMedium = defaults.labelMedium.copy(fontFamily = robotoFamily),
        labelSmall = defaults.labelSmall.copy(fontFamily = robotoFamily),
    )
}
