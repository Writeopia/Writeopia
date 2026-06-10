import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidMultiplatformLibrary)
//    alias(libs.plugins.ktlint)
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(21)

    jvm {}

    androidLibrary {
        namespace = "io.writeopia.persistence"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaFeaturesRoom"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia"))
                implementation(project(":writeopia_models"))
                implementation(project(":plugins:writeopia_persistence_room"))
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:utils"))

                implementation(libs.room.runtime)
                implementation(libs.room.paging)

                implementation(libs.androidx.ktx)

                //
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jvmMain by getting {
            dependencies {

            }
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }

        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
                implementation(libs.androidx.compose.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":libraries:dbtest"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
