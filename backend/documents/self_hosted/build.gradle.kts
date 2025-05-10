plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version "2.3.3"
    application
}

application {
    mainClass.set("io.writeopia.backend.selfhosted.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("writeopia-selfhosted.jar")
    }
}

dependencies {
    implementation(project(":backend:documents:documents"))
    implementation(project(":backend:core:database"))
    implementation(project(":backend:core:connection"))
    implementation(project(":backend:core:auth"))
    implementation(project(":backend:gateway"))
    implementation(project(":common:endpoints"))

    implementation(project(":writeopia_models"))

    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")

    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
