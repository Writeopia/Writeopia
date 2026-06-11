plugins {
    alias(libs.plugins.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.writeopia"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        val baseUrl = "https://writeopia.dev"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")

        applicationId = "io.writeopia"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 63
        versionName = "0.50.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../../upload-keystore.jks")
            storePassword = System.getenv("WR_ANDROID_SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("WR_ANDROID_SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("WR_ANDROID_SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Todo: Re enable the minification and fix R8 bugs
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules-android.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel7api33") {
                    device = "Pixel 7"
                    apiLevel = 33
                    systemImageSource = "aosp"
                }
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":application:composeApp"))
    implementation(project(":writeopia_ui"))
    implementation(project(":writeopia"))
    implementation(project(":writeopia_models"))

    implementation(project(":application:core:persistence_room"))
    implementation(project(":application:core:persistence_bridge"))
    implementation(project(":application:core:utils"))
    implementation(project(":application:core:theme"))
    implementation(project(":application:core:models"))

    implementation(project(":application:features:editor"))
    implementation(project(":application:features:note_menu"))
    implementation(project(":application:features:search"))

    implementation(project(":plugins:writeopia_persistence_room"))
    implementation(project(":plugins:writeopia_persistence_core"))
    implementation(project(":plugins:writeopia_network"))

    implementation(libs.room.runtime)

    implementation(compose.material3)
    implementation(compose.runtime)
    implementation(compose.foundation)

    implementation(libs.compose.navigation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.ktor.client.logging)
    implementation(libs.activity.compose)
    implementation(libs.material)
}
