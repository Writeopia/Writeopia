plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.ktor.framework)
    alias(libs.plugins.kotlinSerialization)
}

application {
    mainClass.set("io.writeopia.api.billing.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("billing-all.jar")
    }
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    // Core modules
    implementation(project(":backend:core:auth"))
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:connection"))
    implementation(project(":writeopia_models"))
    implementation(project(":plugins:writeopia_serialization"))

    // Ktor
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    // Stripe
    implementation(libs.stripe.java)

    // Coroutines & DB
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.jvm)

    // Testing
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
}
