plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
}

kotlin {
    jvm {}

    js(IR) {
        browser()
        binaries.library()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaCoreOllama"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common:endpoints"))
                implementation(project(":writeopia_models"))

                implementation(project(":application:core:connection"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:persistence_sqldelight"))

                implementation(project(":plugins:writeopia_persistence_sqldelight"))

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(compose.foundation)
            }
        }
    }
}
