@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val sdkVersion: String by rootProject.extra

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sonatype.publish)
}

mavenPublishing {
    val artifactId = "writeopia-network"

    coordinates(
        groupId = "io.writeopia",
        artifactId = artifactId,
        version = sdkVersion
    )

    pom {
        name = artifactId
        description = "Writeopia plugin - import document"
        url = "https://writeopia.io"

        developers {
            developer {
                id = "leandroBorgesFerreira"
                name = "Leandro Borges Ferreira"
                url = "https://github.com/leandroBorgesFerreira"
            }
        }

        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        scm {
            connection = "scm:git@github.com:leandroBorgesFerreira/Writeopia.git"
            developerConnection = "scm:git:ssh://github.com/leandroBorgesFerreira/Writeopia.git"
            url = "https://github.com/leandroBorgesFerreira/Writeopia"
        }
    }

    signAllPublications()
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
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

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaPluginNetwork"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia_models"))
                implementation(project(":writeopia"))
                implementation(project(":common:endpoints"))
                implementation(project(":plugins:writeopia_serialization"))

                implementation(libs.kotlinx.serialization.json)

                implementation(libs.ktor.client.auth)
                implementation(libs.kotlinx.coroutines.core)
//                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)

                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

tasks.dokkaHtmlPartial {
    moduleName = "plugin:writeopia_network"
}

android {
    namespace = "io.writeopia.sdk.plugins.network"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
