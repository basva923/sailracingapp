// Root build file: declares plugins once (without applying) so subprojects share versions,
// and aggregates code-coverage reports from all modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":domain"))
    kover(project(":simulation"))
    kover(project(":app"))
}
