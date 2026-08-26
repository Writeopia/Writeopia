plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.multiplatform.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)

    jvmToolchain(21)

    js {
        browser {
            webpackTask {
                mainOutputFileName = "writeopia.js"
                sourceMaps = false
            }
            runTask {
                mainOutputFileName = "writeopia.js"
            }
        }
        binaries.executable()
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)

                implementation(project(":writeopia"))
                implementation(project(":writeopia_ui"))

                implementation(project(":plugins:writeopia_persistence_core"))

                implementation(project(":application:core:persistence_sqldelight"))
                implementation(project(":application:core:theme"))
                implementation(project(":application:core:utils"))
                implementation(project(":application:core:navigation"))
                implementation(project(":application:core:documents"))
                implementation(project(":application:core:models"))
                implementation(project(":plugins:writeopia_network"))

                implementation(project(":application:common_flows:wide_screen_common"))
                implementation(project(":application:features:global_shell"))
                implementation(project(":application:features:note_menu"))
                implementation(project(":application:features:editor"))
                implementation(project(":application:features:auth"))
                implementation(project(":application:core:auth_core"))
                implementation(project(":application:core:genai"))

                implementation(libs.compose.navigation)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.ktor.client.core)
            }
        }

        sourceSets.jsMain.dependencies {
            implementation(libs.sqldelight.web.driver)
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.2.1"))
            implementation(npm("sql.js", "1.8.0"))
        }
    }
}
