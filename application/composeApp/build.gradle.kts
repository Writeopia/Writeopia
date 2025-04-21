import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm {}

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                implementation(project(":writeopia_ui"))
                implementation(project(":writeopia"))
                implementation(project(":writeopia_models"))

                implementation(project(":application:core:persistence_sqldelight"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:navigation"))
                implementation(project(":application:core:documents"))

                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":plugins:writeopia_serialization"))
                implementation(project(":plugins:writeopia_network"))
                implementation(project(":plugins:writeopia_presentation"))

                implementation(project(":application:common_flows:wide_screen_common"))
                implementation(project(":application:core:persistence_bridge"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:models"))
                implementation(project(":application:core:resources"))
                implementation(project(":application:features:note_menu"))
                implementation(project(":application:features:editor"))
                implementation(project(":application:features:account"))
                implementation(project(":application:features:search"))
                implementation(project(":application:features:global_shell"))

                implementation(libs.room.runtime)

                implementation(libs.compose.navigation)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(project(":plugins:writeopia_persistence_core"))
                implementation(project(":application:core:models"))
                implementation(project(":plugins:writeopia_presentation"))
                implementation(compose.desktop.currentOs)
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(project(":application:core:common_ui_tests"))
                implementation(project(":application:core:theme"))

                implementation(libs.kotlin.test)
                implementation(libs.androidx.espresso.core)

                implementation(libs.androidx.compose.test)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.logging)
                implementation(libs.activity.compose)
                implementation(libs.androidx.material3)
                implementation(libs.material)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))

                implementation(project(":application:core:resources"))
                implementation(project(":application:features:auth"))
            }
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(project(":application:features:auth"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.writeopia.desktop.MainDesktopKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Writeopia"
            packageVersion = "1.0.0"
            modules("java.sql")

            val iconsRoot = project.file("./src/jvmMain/resources/images")

            macOS {
                bundleID = "io.writeopia.notesApp"

                iconFile.set(iconsRoot.resolve("icon-mac.icns"))
                jvmArgs("-Dapple.awt.application.appearance=system")

                signing {
                    val providers = project.providers
                    sign.set(true)
                    identity.set(providers.environmentVariable("SIGN_IDENTITY"))
                }

                notarization {
                    val providers = project.providers
                    appleID.set(providers.environmentVariable("APPLE_ID_NOTARIZATION"))
                    password.set(providers.environmentVariable("NOTARIZATION_PASSWORD"))
                    teamID.set(providers.environmentVariable("APPSTORE_TEAM_ID"))
                }
            }

            linux {
                packageVersion = "0.39.0"
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
            }

            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
            }
        }

        buildTypes.release {
            proguard {
                version.set("7.5.0")
                isEnabled = true
                configurationFiles.from("proguard-rules-desktop.pro")
            }
        }
    }
}

android {
    namespace = "io.writeopia"
    compileSdk = 35

    defaultConfig {
        val baseUrl = System.getenv("WRITEOPIA_CLIENT_BASE_URL")
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")

        applicationId = "io.writeopia"
        minSdk = 24
        targetSdk = 35
        versionCode = 33
        versionName = "0.20.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../../../upload-keystore.jks")
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
}
