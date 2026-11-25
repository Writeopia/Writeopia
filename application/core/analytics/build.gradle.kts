import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.ktlint)
}

// The PostHog project token isn't a build secret (PostHog's client tokens are write-only and
// meant to ship inside the app), but it's still generated instead of hardcoded so it can be
// rotated without touching source, and isn't duplicated across the JVM and Android targets.
val generatePostHogConfig by tasks.registering {
    val tokenProvider = providers.gradleProperty("writeopia.posthog.projectToken")
        .orElse(providers.environmentVariable("WR_POSTHOG_PROJECT_TOKEN"))
        .orElse("")
    // Declared as an input (not just read inside doLast) so Gradle reruns this task -
    // and regenerates the constant for both targets - when the token changes, instead
    // of treating it as up-to-date because only the output directory is tracked.
    inputs.property("posthogProjectToken", tokenProvider)

    val outputDir = layout.buildDirectory.dir("generated/posthog")
    outputs.dir(outputDir)

    doLast {
        val token = tokenProvider.get()
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

        // The Compose web app is no longer shipped (the web client is the React app), and iOS
        // has the native app with its own tracker: these targets only need to compile.
        val noopMain by creating {
            dependsOn(commonMain)
        }

        listOf("jsMain", "wasmJsMain", "iosArm64Main", "iosSimulatorArm64Main").forEach { name ->
            getByName(name).dependsOn(noopMain)
        }
    }
}
