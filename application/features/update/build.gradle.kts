import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":application:core:configuration"))
                implementation(project(":application:core:resources"))
                implementation(project(":common:endpoints"))
                implementation(project(":plugins:writeopia_network"))
                implementation(project(":plugins:writeopia_serialization"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.lifecycle.viewmodel.compose)

                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
    }
}
