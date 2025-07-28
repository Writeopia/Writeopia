@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    kotlin("multiplatform")
//    alias(libs.plugins.nativeCocoapods)
}

kotlin {
    jvmToolchain(21)

    jvm {}

    js(IR) {
        browser()
        binaries.library()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {}
        }

        val jsMain by getting {
            dependencies {
            }
        }

        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "WriteopiaCommonEndpoints"
                isStatic = true
            }
        }
    }
}
