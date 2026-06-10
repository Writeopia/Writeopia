import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "io.writeopia.global_shell"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm()

    js {
        browser()
        binaries.library()
    }

    wasmJs {
        browser()
        binaries.library()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaFeaturesGlobalShell"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia"))
                implementation(project(":tutorials"))
                implementation(project(":writeopia_ui"))
                implementation(project(":writeopia_models"))
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(project(":plugins:writeopia_network"))
                implementation(project(":plugins:writeopia_import_document"))

                implementation(project(":application:core:utils"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:common_ui"))
                implementation(project(":application:core:documents"))
                implementation(project(":application:core:ollama"))
                implementation(project(":application:core:resources"))
                implementation(project(":application:core:configuration"))
                implementation(project(":application:core:persistence_sqldelight"))
                implementation(project(":application:core:connection"))

                implementation(project(":application:features:note_menu"))

                implementation(libs.kotlinx.serialization.json)
                //

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.compose.navigation)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.ktor.client.core)
            }
        }

        val androidMain by getting {
            dependencies {
//                implementation("androidx.activity:activity-compose")
//                implementation(libs.accompanist.systemuicontroller)
            }
        }

        val jvmMain by getting {
            dependencies {
                runtimeOnly(libs.kotlinx.coroutines.swing)
            }
        }
    }
}
