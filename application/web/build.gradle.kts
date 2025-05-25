plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)

                implementation(project(":writeopia_ui"))

                implementation(project(":plugins:writeopia_persistence_core"))

                implementation(project(":application:core:persistence_sqldelight"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:navigation"))

                implementation(project(":application:common_flows:wide_screen_common"))
                implementation(project(":application:features:note_menu"))

                implementation(libs.compose.navigation)
            }
        }
    }
}
