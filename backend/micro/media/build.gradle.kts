plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.ktor.framework)
    alias(libs.plugins.kotlinSerialization)
}

application {
    mainClass.set("io.writeopia.api.media.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktor {
    fatJar {
        archiveFileName.set("media-all.jar")
    }
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    implementation(project(":plugins:writeopia_serialization"))

    // Core modules
    implementation(project(":backend:core:auth"))
    implementation(project(":backend:core:buckets"))
    implementation(project(":backend:core:connection"))
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:models"))
    implementation(project(":backend:core:pubsub"))
    implementation(project(":backend:core:storage"))
    implementation(project(":common:endpoints"))

    // Ktor
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.database.embedded.postgres)
    testImplementation(libs.database.hikaricp)
    testImplementation(libs.database.postgresql)
}
