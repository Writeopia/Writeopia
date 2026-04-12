package io.writeopia.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.painterResource
import writeopia.application.core.resources.generated.resources.Res
import writeopia.application.core.resources.generated.resources.logo
import writeopia.application.core.resources.generated.resources.onboarding_robot

object CommonImages {

    @Composable
    fun onboardingRobot() = painterResource(Res.drawable.onboarding_robot)

    @Composable
    fun logo() = painterResource(Res.drawable.logo)
}
