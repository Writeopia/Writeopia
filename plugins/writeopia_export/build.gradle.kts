import com.vanniktech.maven.publish.SonatypeHost

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sonatype.publish)
}

mavenPublishing {
    val artifactId = "writeopia-export"

    coordinates(
        groupId = "io.writeopia",
        artifactId = artifactId,
        version = "0.5.0"
    )

    pom {
        name = artifactId
        description = "Writeopia plugin - export"
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
    jvm {}

    js(IR) {
        browser()
        binaries.library()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaPluginExport"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia_models"))
                implementation(project(":writeopia"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

tasks.dokkaHtmlPartial {
    moduleName = "plugin:writeopia_export"
}
