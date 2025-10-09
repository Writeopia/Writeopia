import org.jetbrains.compose.compose

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    jvm()
    js(IR) {
        browser()
        binaries.library()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia_models"))
                implementation(project(":writeopia"))
                implementation(project(":writeopia_ui"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(project(":plugins:writeopia_network"))
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":plugins:writeopia_persistence_sqldelight"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:navigation"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:documents"))
                implementation(project(":application:core:common_ui"))
                implementation(project(":application:core:ollama"))
                implementation(project(":application:core:connection"))
                implementation(project(":application:core:configuration"))
                implementation(project(":application:features:editor"))
                implementation(project(":application:features:note_menu"))
                implementation(project(":application:features:global_shell"))
                implementation(project(":application:features:account"))
                implementation(project(":application:features:search"))
                implementation(project(":application:features:documents_graph"))

                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.serialization.json)

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)

                implementation(libs.compose.navigation)
                implementation(libs.compose.icons.lucide)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(compose("org.jetbrains.compose.ui:ui-test-junit4"))
                implementation(project(":application:core:common_ui_tests"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
