@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    jvm {}

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
            baseName = "WriteopiaCoreModels"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia_models"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
            }
        }

        val jvmTest by getting {
            dependencies {
            }
        }

        val jsMain by getting {
            dependencies {
            }
        }

        nativeMain.dependencies {
        }
    }
}
