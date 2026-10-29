import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.ktlint)
}

// The PostHog project token isn't a build secret (PostHog's client tokens are write-only and
// meant to ship inside the app), but it's still generated instead of hardcoded so it can be
// rotated without touching source, and isn't duplicated across every platform target.
val generatePostHogConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/posthog")
    outputs.dir(outputDir)

    doLast {
        val token = providers.gradleProperty("writeopia.posthog.projectToken")
            .orElse(providers.environmentVariable("WR_POSTHOG_PROJECT_TOKEN"))
            .getOrElse("")
        val file = outputDir.get().file("io/writeopia/analytics/PostHogConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.writeopia.analytics

            internal const val POSTHOG_PROJECT_TOKEN: String = "$token"

            """.trimIndent()
        )
    }
}

kotlin {
    jvmToolchain(21)

    jvm {}

    androidLibrary {
        namespace = "io.writeopia.analytics"
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
            baseName = "WriteopiaCoreAnalytics"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting

        val jvmMain by getting {
            kotlin.srcDir(generatePostHogConfig)
            dependencies {
                implementation(libs.posthog.java)
            }
        }

        val androidMain by getting {
            kotlin.srcDir(generatePostHogConfig)
            dependencies {
                implementation(libs.posthog.android)
            }
        }

        val jsMain by getting {
            kotlin.srcDir(generatePostHogConfig)
        }

        val wasmJsMain by getting {
            kotlin.srcDir(generatePostHogConfig)
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
    }
}
