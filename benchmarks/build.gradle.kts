import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spotless)
    application
}

val lwjglVersion = libs.versions.lwjgl.get()
val lwjglNativesClassifier = rootProject.extra["lwjglNativesClassifier"] as String

val natives =
    configurations.create("natives") {
        description = "LWJGL natives for the build host"
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    implementation(project(":library"))
    implementation(kotlin("stdlib"))

    natives("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNativesClassifier")
    natives("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNativesClassifier")
    natives("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNativesClassifier")
    natives("org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNativesClassifier")
}

configurations.named("runtimeClasspath") {
    extendsFrom(natives)
}

application {
    mainClass.set("com.psiwray.organization.terminal_window.benchmarks.FrameBenchKt")
}

kotlin {
    jvmToolchain(25)
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        allWarningsAsErrors.set(true)
    }
}

val benchmarkJvmArgs =
    buildList {
        add("-Djava.awt.headless=true")
        add("--enable-native-access=ALL-UNNAMED")
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            add("-XstartOnFirstThread")
        }
    }

tasks.named<JavaExec>("run") {
    group = "verification"
    description = "Runs the offscreen frame benchmark."
    mainClass.set("com.psiwray.organization.terminal_window.benchmarks.FrameBenchKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    jvmArgs(benchmarkJvmArgs)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf("ktlint_standard_package-name" to "disabled"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf("ktlint_standard_package-name" to "disabled"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}
