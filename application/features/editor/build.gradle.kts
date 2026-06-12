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
        namespace = "io.writeopia.editor"
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
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaFeaturesEditor"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia"))
                implementation(project(":writeopia_ui"))
                implementation(project(":writeopia_models"))
                implementation(project(":plugins:writeopia_export"))
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(project(":plugins:writeopia_network"))
                implementation(project(":plugins:writeopia_presentation"))

//                implementation(project(":application:core:resources"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:common_ui"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:documents"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:ollama"))
                implementation(project(":application:core:resources"))
                implementation(project(":application:core:connection"))
                implementation(project(":application:core:configuration"))
                implementation(project(":application:features:drawing"))

                implementation(libs.kotlinx.serialization.json)
                //

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.compose.navigation)

                implementation(libs.kotlin.test)
                implementation(libs.material.icons.core)
                implementation(libs.lifecycle.viewmodel.compose)
            }
        }

        val androidMain by getting {
            dependencies {
//                implementation("androidx.activity:activity-compose")
            }
        }

        val jvmMain by getting {
            dependencies {
            }
        }
    }
}
