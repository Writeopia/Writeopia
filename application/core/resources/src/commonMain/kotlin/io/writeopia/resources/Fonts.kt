package io.writeopia.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import writeopia.application.core.resources.generated.resources.Res
import writeopia.application.core.resources.generated.resources.Roboto_Regular

object Fonts {

    val robotoRegular: FontResource
        get() = Res.font.Roboto_Regular

    val robotoFontFamily: FontFamily
        @Composable
        get() = FontFamily(
            Font(robotoRegular, weight = FontWeight.Normal)
        )
}
