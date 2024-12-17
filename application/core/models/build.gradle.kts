plugins {
    kotlin("multiplatform")
    alias(libs.plugins.ktlint)
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
            baseName = "WriteopiaCoreModels"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia_models"))
                implementation(libs.kotlinx.datetime)
            }
        }

        val commonTest by getting {
            dependencies {
            }
        }

        val jvmTest by getting {
            dependencies {
            }
        }
    }
}
