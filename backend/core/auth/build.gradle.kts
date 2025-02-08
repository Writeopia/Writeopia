plugins {
    id("java-library")
    alias(libs.plugins.ktor.framework)
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}


dependencies {
    implementation(libs.ktor.server.core)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    implementation(libs.firebase.admin)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)

}
