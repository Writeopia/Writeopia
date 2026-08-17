plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.ktor.framework)
    alias(libs.plugins.kotlinSerialization)
}

application {
    mainClass.set("io.writeopia.api.export.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("export-all.jar")
    }
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    implementation(project(":backend:documents:documents"))
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:connection"))
    implementation(project(":backend:core:buckets"))
    implementation(project(":plugins:writeopia_serialization"))
    implementation(project(":writeopia_models"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(libs.sqldelight.jvm)
    implementation(libs.google.cloud.storage)

    testImplementation(libs.kotlin.test)
}
