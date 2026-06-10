@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidMultiplatformLibrary)
    kotlin("multiplatform")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)

    jvm {}

    androidLibrary {
        namespace = "io.writeopia.auth.core"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

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
            baseName = "WriteopiaCoreAuth"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia"))
                implementation(project(":writeopia_models"))
                implementation(project(":application:core:models"))

                implementation(project(":application:core:utils"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":plugins:writeopia_persistence_sqldelight"))

                implementation(project(":plugins:writeopia_network"))
                implementation(project(":plugins:writeopia_serialization"))

                implementation(project(":application:core:connection"))
                implementation(project(":common:endpoints"))

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                //

                implementation(libs.ktor.client.core)
            }
        }

        val jvmMain by getting {
            dependencies {
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.content.negotiation)
            }
        }

        val jsMain by getting {
            dependencies {
            }
        }

        val androidMain by getting {
            dependencies {
            }
        }

        nativeMain.dependencies {
        }
    }
}
