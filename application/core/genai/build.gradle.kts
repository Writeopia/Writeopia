@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
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
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaCoreGenAi"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common:endpoints"))
                implementation(project(":writeopia_models"))
                implementation(project(":writeopia"))

                implementation(project(":application:core:connection"))
                implementation(project(":application:core:utils"))

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
