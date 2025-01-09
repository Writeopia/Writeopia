plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.sqldelight)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}


dependencies {
    implementation(project(":writeopia"))
    implementation(project(":writeopia_models"))
    implementation(project(":plugins:writeopia_serialization"))
    implementation(project(":plugins:writeopia_persistence_core"))
    implementation(project(":plugins:writeopia_persistence_sqldelight"))

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
        create("WriteopiaDb") {
            packageName.set("io.writeopia.api")
            dialect("app.cash.sqldelight:postgresql-dialect:2.0.2")
            dependency(project(":plugins:writeopia_persistence_sqldelight"))
            generateAsync.set(true)
        }
    }
}
