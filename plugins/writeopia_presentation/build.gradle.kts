@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val sdkVersion: String by rootProject.extra

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sonatype.publish)
}

mavenPublishing {
    val artifactId = "writeopia-presentations"

    coordinates(
        groupId = "io.writeopia",
        artifactId = artifactId,
        version = sdkVersion
    )

    pom {
        name = artifactId
        description = "Writeopia plugin - presentations"
        url = "https://writeopia.io"

        developers {
            developer {
                id = "leandroBorgesFerreira"
                name = "Leandro Borges Ferreira"
                url = "https://github.com/leandroBorgesFerreira"
            }
        }

        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        scm {
            connection = "scm:git@github.com:leandroBorgesFerreira/Writeopia.git"
            developerConnection = "scm:git:ssh://github.com/leandroBorgesFerreira/Writeopia.git"
            url = "https://github.com/leandroBorgesFerreira/Writeopia"
        }
    }

    signAllPublications()
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
}

kotlin {
    jvmToolchain(21)

    jvm {}

    js {
        browser()
        binaries.library()
    }

    wasmJs {
        browser()
        binaries.library()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaPluginPresentation"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia_models"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.coil.compose)
                implementation(libs.coil.ktor3)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.dokkaHtmlPartial {
    moduleName = "plugin:writeopia_presentation"
}
