package io.writeopia.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.painterResource
import writeopia.application.core.resources.generated.resources.Res
import writeopia.application.core.resources.generated.resources.logo

object CommonImages {

    @Composable
    fun logo() = painterResource(Res.drawable.logo)
}
