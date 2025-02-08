import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":writeopia"))
    implementation(project(":writeopia_models"))

    implementation(project(":plugins:writeopia_persistence_core"))
    implementation(project(":plugins:writeopia_persistence_sqldelight"))
}
