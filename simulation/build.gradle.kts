// Pure Kotlin/JVM module: a deterministic boat + wind + race-course simulator built on the domain model.
// Used by automated end-to-end tests and by the app's simulation mode.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    // Compile with the installed JDK 21 but emit Java 17 bytecode, matching the Android modules.
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    explicitApi()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":domain"))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        verify {
            rule("simulation must be fully covered") {
                minBound(100)
            }
        }
    }
}
