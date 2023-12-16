// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.application) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.nativeCocoapods) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dag.command) apply true
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

apply(from = "${rootDir}/scripts/publish-root.gradle")

dagCommand {
    filter = "all"
    defaultBranch = "origin/main"
    outputType = "json"
    printModulesInfo = true
}

