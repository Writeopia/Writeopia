plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.ktor.framework)
    alias(libs.plugins.kotlinSerialization)
}
application {
    mainClass.set("io.writeopia.api.geteway.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(project(":writeopia"))
    implementation(project(":writeopia_models"))

    implementation(project(":backend:core:auth"))
    implementation(project(":backend:documents:documents"))

    implementation(project(":plugins:writeopia_persistence_core"))
    implementation(project(":plugins:writeopia_persistence_sqldelight"))
    implementation(project(":plugins:writeopia_serialization"))

    implementation(project(":common:endpoints"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websocket)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.client.logging)

    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.swagger)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.coroutines.reactor)
    implementation(libs.kotlinx.datetime)

    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.firebase.auth.provider)

    implementation(libs.sqldelight.jvm)

    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
}
