plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
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
    implementation(project(":backend:core:connection"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.google.genai.kotlin)
    implementation(libs.kotlinx.serialization.json)

    // Required by google-genai-kotlin for HttpTimeout plugin and OkHttp engine
    // Force Ktor 3.x versions to resolve conflicts with google-genai-kotlin's transitive dependencies
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

configurations.all {
    resolutionStrategy {
        // Force Ktor 3.x to resolve version conflicts
        force("io.ktor:ktor-client-core:3.5.1")
        force("io.ktor:ktor-client-okhttp:3.5.1")
        force("io.ktor:ktor-client-websockets:3.5.1")
    }
}
