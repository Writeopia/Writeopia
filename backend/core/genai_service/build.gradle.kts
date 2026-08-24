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

// Ktor version that google-genai-kotlin:0.5.0 was compiled against
val ktorVersion = "2.3.8"

dependencies {
    implementation(project(":backend:core:connection"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.google.genai.kotlin)
    implementation(libs.kotlinx.serialization.json)

    // Use Ktor 2.x to match google-genai-kotlin's compiled dependencies
    // google-genai-kotlin:0.5.0 was compiled against Ktor 2.3.8
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

configurations.all {
    resolutionStrategy {
        // Force Ktor 2.x for compatibility with google-genai-kotlin:0.5.0
        force("io.ktor:ktor-client-core:$ktorVersion")
        force("io.ktor:ktor-client-okhttp:$ktorVersion")
        force("io.ktor:ktor-client-websockets:$ktorVersion")
        force("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        force("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    }
}

