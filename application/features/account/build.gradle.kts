plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    androidTarget()

    jvm {}

    js(IR) {
        browser()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "WriteopiaFeaturesAccount"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:common_ui"))
                implementation(project(":application:core:ollama"))
                implementation(project(":application:core:resources"))

                implementation(libs.kotlinx.datetime)

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(libs.compose.navigation)
            }
        }

        val androidMain by getting {
            dependencies {
            }
        }

        val jvmMain by getting {
            dependencies {
            }
        }
    }
}

android {
    namespace = "io.writeopia.account"
    compileSdk = 35

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
