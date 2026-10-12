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

dependencies {
    implementation(project(":writeopia_models"))
    implementation(project(":backend:core:connection"))
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:genai_service"))

    implementation(libs.kotlinx.coroutines.core)

    // SLF4J for logging (required by connection module's logger)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.database.hikaricp)
    testImplementation(libs.database.postgresql)
}
