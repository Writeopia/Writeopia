plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.sqldelight)
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

    implementation(libs.ktor.client.logging)

    implementation(libs.sqldelight.jvm)
    implementation(libs.sqldelight.jdbc.driver)
    implementation(libs.database.hikaricp)
    implementation(libs.database.postgresql)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotlin.test)
}

sqldelight {
    databases {
        create("WriteopiaDbBackend") {
            packageName.set("io.writeopia.sql")
            dialect(libs.sqldelight.dialect.postgres)
            generateAsync.set(true)
        }
    }
}

