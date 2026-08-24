import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.ktor.framework)
    alias(libs.plugins.kotlinSerialization)
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

// This module is a library, not an application.
// Disable shadowJar since there's no main class.
tasks.named("shadowJar") {
    enabled = false
}

dependencies {
    implementation(project(":writeopia"))
    implementation(project(":writeopia_models"))
    implementation(project(":plugins:writeopia_serialization"))

    implementation(project(":common:endpoints"))
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:connection"))
    implementation(project(":backend:core:auth"))
    implementation(project(":backend:core:models"))
    implementation(project(":backend:core:buckets"))
    implementation(project(":backend:core:genai_service"))

    //

    implementation(libs.ktor.client.core)

    implementation(libs.ktor.server.auth)
}
