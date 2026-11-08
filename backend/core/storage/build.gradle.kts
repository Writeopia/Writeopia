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
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:models"))
    implementation(project(":backend:core:connection"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.database.embedded.postgres)
    testImplementation(libs.database.hikaricp)
    testImplementation(libs.database.postgresql)
}
