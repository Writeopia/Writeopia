plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
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
            baseName = "WriteopiaFeaturesEditor"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":writeopia"))
                implementation(project(":writeopia_ui"))
                implementation(project(":writeopia_models"))
                implementation(project(":plugins:writeopia_export"))
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(project(":plugins:writeopia_network"))
                implementation(project(":plugins:writeopia_presentation"))

//                implementation(project(":application:core:resources"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:common_ui"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:documents"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:ollama"))
                implementation(project(":application:core:resources"))
                implementation(project(":application:core:connection"))
                implementation(project(":application:core:configuration"))

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.compose.navigation)

                implementation(libs.kotlin.test)
            }
        }

        val androidMain by getting {
            dependencies {
//                implementation("androidx.activity:activity-compose")
            }
        }

        val jvmMain by getting {
            dependencies {
            }
        }
    }
}

android {
    namespace = "io.writeopia.editor"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 24

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
