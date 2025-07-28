import org.jetbrains.compose.compose

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    jvm {}
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose("org.jetbrains.compose.ui:ui-test-junit4"))
            }
        }
    }
}
