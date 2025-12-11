@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidLibrary)
//    alias(libs.plugins.ktlint)
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(21)

    jvm {}

    androidTarget()

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
    }
}

android {
    namespace = "io.writeopia.persistence"
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
}

dependencies {
    add("kspAndroid", libs.room.compiler)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.test)
    debugImplementation(libs.androidx.compose.test.manifest)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":libraries:dbtest"))

    testImplementation(libs.kotlin.test)
}
