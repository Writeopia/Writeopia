plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.ktor.framework)
}

group = "io.writeopia"
version = "0.0.1"

application {
    mainClass.set("io.writeopia.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":writeopia_models"))
    implementation(project(":common:endpoints"))
    implementation(project(":backend:editor:api_editor"))
    implementation(project(":plugins:writeopia_serialization"))
    implementation(project(":plugins:writeopia_network"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websocket)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.coroutines.reactor)
    implementation(libs.kotlinx.datetime)

    implementation(libs.firebase.admin)

    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
}
