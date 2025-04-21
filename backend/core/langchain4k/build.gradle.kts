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
    implementation(project(":writeopia"))
    implementation(project(":writeopia_models"))
    implementation(project(":plugins:writeopia_serialization"))
}
