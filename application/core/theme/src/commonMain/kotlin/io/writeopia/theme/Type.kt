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

    return Typography(
        displayLarge = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        displayMedium = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        displaySmall = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        headlineLarge = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        headlineMedium = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        headlineSmall = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        titleLarge = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        titleMedium = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodySmall = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Medium),
    )
}
