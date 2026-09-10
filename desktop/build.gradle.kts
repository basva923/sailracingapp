// The strategy workbench: a program for the PC that draws a course by hand and asks the app's own
// strategy what it makes of it. It holds no race logic - all of that is :domain, exactly as on the phone -
// and it is drawn with Swing, which every JDK already has, so it builds and runs with nothing to install.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    application
}

kotlin {
    // Compile with the installed JDK 21 but emit Java 17 bytecode, matching the other modules.
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.sailracing.desktop.MainKt")
}

dependencies {
    implementation(project(":domain"))

    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        filters {
            excludes {
                // The window itself: pixels, listeners and Swing plumbing, judged by eye and not by a test.
                packages("com.sailracing.desktop.ui")
                classes("com.sailracing.desktop.MainKt")
            }
        }
        verify {
            rule("everything the workbench decides must be covered") {
                minBound(100)
            }
        }
    }
}
