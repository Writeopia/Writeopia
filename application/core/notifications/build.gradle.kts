import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

/**
 * System notifications of the app. Android only for now: the AI tasks running in the background
 * are followed with a notification each, with a Cancel action.
 */
kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "io.writeopia.core.notifications"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        // The strings and the icon of the notifications
        androidResources {
            enable = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":application:core:local_ai"))

            implementation(libs.androidx.ktx)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.android)
            implementation(compose.runtime)
            implementation(compose.ui)
        }
    }
}
