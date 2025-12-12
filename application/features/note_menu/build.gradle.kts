plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    androidTarget()

    jvm()

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
            baseName = "WriteopiaFeaturesNoteMenu"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":tutorials"))

                implementation(project(":writeopia"))
                implementation(project(":writeopia_ui"))
                implementation(project(":writeopia_models"))
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":plugins:writeopia_export"))
                implementation(project(":plugins:writeopia_import_document"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(project(":plugins:writeopia_network"))

                implementation(project(":application:core:utils"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:common_ui"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:documents"))
                implementation(project(":application:core:resources"))
                implementation(project(":application:core:ollama"))
                implementation(project(":application:core:configuration"))
                implementation(project(":application:core:connection"))
                implementation(project(":application:core:forcegraph"))

                implementation(project(":application:features:account"))
                implementation(project(":application:features:onboarding"))

                //

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.compose.navigation)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.material.icons.core)
                implementation(libs.lifecycle.viewmodel.compose)
            }
        }

        val androidMain by getting {
            dependencies {

                implementation(libs.coil.compose)

                implementation(libs.compose.shimmer)
                implementation(libs.room.runtime)
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(project(":application:core:common_ui_tests"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":plugins:writeopia_persistence_room"))

                implementation(libs.kotlin.test)
                implementation(libs.androidx.espresso.core)

                implementation(libs.androidx.compose.test)
                implementation(project(":libraries:dbtest"))
                implementation(libs.room.runtime)
            }
        }

        val jvmMain by getting {
            dependencies {
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
            }
        }

        iosMain.dependencies {
            implementation(libs.room.runtime)
        }
    }
}

android {
    namespace = "io.writeopia.note_menu"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
