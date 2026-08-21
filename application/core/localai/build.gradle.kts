@file:OptIn(ExperimentalWasmDsl::class)
@file:Suppress("DEPRECATION")

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    jvm {}

    androidLibrary {
        namespace = "io.writeopia.localai"
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
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaLocalAi"
            isStatic = true
        }
        iosTarget.compilations.getByName("main") {
            cinterops.create("llama") {
                defFile(project.file("nativeInterop/cinterop/llama.def"))
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:persistence_sqldelight"))
                implementation(project(":plugins:writeopia_persistence_sqldelight"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.jna)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val iosArm64Main by getting {
            dependsOn(nativeMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(nativeMain)
        }

        val jsMain by getting

        val wasmJsMain by getting
    }
}
