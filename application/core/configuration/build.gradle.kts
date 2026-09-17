@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val desktopAppVersion = rootProject.extra["desktopAppVersion"] as String
val generatedDesktopVersionDir =
    layout.buildDirectory.dir("generated/desktopVersion/kotlin")

val generateDesktopAppVersion by tasks.registering {
    inputs.property("desktopAppVersion", desktopAppVersion)
    outputs.dir(generatedDesktopVersionDir)

    doLast {
        val outputFile = generatedDesktopVersionDir
            .get()
            .file("io/writeopia/core/configuration/DesktopAppVersion.kt")
            .asFile

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package io.writeopia.core.configuration

            object DesktopAppVersion {
                const val CURRENT = "$desktopAppVersion"
            }
            """.trimIndent()
        )
    }
}

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)

    jvm {}

    androidLibrary {
        namespace = "io.writeopia.core.configuration"
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
            baseName = "WriteopiaCoreConfiguration"
            isStatic = true
        }
    }

    sourceSets {
        val jvmMain by getting {
            kotlin.srcDir(generatedDesktopVersionDir)
        }

        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia_models"))

                implementation(project(":application:core:models"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:theme"))

                implementation(project(":plugins:writeopia_persistence_core"))

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateDesktopAppVersion)
}
