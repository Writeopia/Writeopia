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
            dependencies {
                implementation(libs.posthog.java)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.posthog.android)
            }
        }

        val jsMain by getting

        val wasmJsMain by getting

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
