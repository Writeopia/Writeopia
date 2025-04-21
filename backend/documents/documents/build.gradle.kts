import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.ktor.framework)
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

dependencies {
    implementation(project(":writeopia"))
    implementation(project(":writeopia_models"))
    implementation(project(":plugins:writeopia_serialization"))

    implementation(project(":common:endpoints"))
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:connection"))

    implementation(libs.kotlinx.datetime)

    implementation(libs.ktor.server.core)

    implementation(libs.ktor.client.core)
}
