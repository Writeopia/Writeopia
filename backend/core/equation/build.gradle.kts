plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
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

// Equations: the strokes of a drawing read by Gemini as LaTeX, and LaTeX rendered to pictures
// with JLaTeXMath. The AI routes mount it; the storage of the pictures comes from the buckets.
dependencies {
    implementation(project(":writeopia_models"))
    implementation(project(":backend:core:connection"))
    implementation(project(":backend:core:genai_service"))
    implementation(project(":backend:core:models"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jlatexmath)

    // SLF4J for logging (required by connection module's logger)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
