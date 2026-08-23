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
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation(project(":plugins:writeopia_serialization"))
    implementation(project(":writeopia_models"))
    implementation(project(":backend:core:models"))
    implementation(project(":common:endpoints"))

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.json)

    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:connection"))

    // Google Gen AI Kotlin SDK (Vertex AI / Gemini)
    implementation(libs.google.genai.kotlin)

    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
