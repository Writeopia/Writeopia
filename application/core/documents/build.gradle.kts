import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/*
 * The OAuth client of the desktop app for the Google Drive sync, from the git-ignored
 * local.properties of the repository root (googleDriveDesktopClientId, googleDriveDesktopClientSecret)
 * or the environment (GOOGLE_DRIVE_DESKTOP_CLIENT_ID, GOOGLE_DRIVE_DESKTOP_CLIENT_SECRET, for CI).
 * Without them the build works and the app offers no Google Drive sync.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun desktopConfig(property: String, environment: String): String =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(property).orEmpty()

val googleDriveDesktopClientId = desktopConfig("googleDriveDesktopClientId", "GOOGLE_DRIVE_DESKTOP_CLIENT_ID")
val googleDriveDesktopClientSecret =
    desktopConfig("googleDriveDesktopClientSecret", "GOOGLE_DRIVE_DESKTOP_CLIENT_SECRET")
val googleDriveConfigDir = layout.buildDirectory.dir("generated/googleDriveConfig/jvmMain/kotlin")

val generateGoogleDriveDesktopConfig by tasks.registering {
    inputs.property("clientId", googleDriveDesktopClientId)
    inputs.property("clientSecret", googleDriveDesktopClientSecret)
    outputs.dir(googleDriveConfigDir)

    doLast {
        val file = googleDriveConfigDir.get()
            .file("io/writeopia/core/folders/sync/cloud/GoogleDriveDesktopConfig.kt")
            .asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package io.writeopia.core.folders.sync.cloud
            |
            |// Generated from local.properties or the environment; see build.gradle.kts.
            |internal object GoogleDriveDesktopConfig {
            |    const val CLIENT_ID = "$googleDriveDesktopClientId"
            |    const val CLIENT_SECRET = "$googleDriveDesktopClientSecret"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    jvmToolchain(21)

    jvm {}

    androidLibrary {
        namespace = "io.writeopia.core.documents"
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
            baseName = "WriteopiaCoreDocuments"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia"))
                implementation(project(":writeopia_models"))
                implementation(project(":tutorials"))
                implementation(project(":writeopia_ui"))
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(project(":plugins:writeopia_network"))

                implementation(project(":application:core:utils"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:common_ui"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":application:core:connection"))
                implementation(project(":application:core:configuration"))
                implementation(project(":application:core:connection"))
                implementation(project(":common:endpoints"))

                implementation(libs.kotlinx.coroutines.core)
                //
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val jvmMain by getting {
            kotlin.srcDir(generateGoogleDriveDesktopConfig)
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}
