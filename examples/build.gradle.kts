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
    mainClass.set("com.psiwray.organization.terminal_window.examples.HelloTerminalKt")
}

kotlin {
    jvmToolchain(25)
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        allWarningsAsErrors.set(true)
    }
}

val exampleJvmArgs =
    buildList {
        add("-Djava.awt.headless=true")
        add("--enable-native-access=ALL-UNNAMED")
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            add("-XstartOnFirstThread")
        }
    }

fun JavaExec.configureExample(main: String) {
    group = "application"
    mainClass.set(main)
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    jvmArgs(exampleJvmArgs)
}

tasks.named<JavaExec>("run") {
    configureExample("com.psiwray.organization.terminal_window.examples.HelloTerminalKt")
}

tasks.register<JavaExec>("hello") {
    description = "Runs the interactive HelloTerminal example."
    configureExample("com.psiwray.organization.terminal_window.examples.HelloTerminalKt")
}

tasks.register<JavaExec>("twoThreads") {
    description = "Runs the two-window, two-owner-thread example."
    configureExample("com.psiwray.organization.terminal_window.examples.TwoThreadsKt")
}

tasks.register<JavaExec>("customFont") {
    description = "Runs the font sampler; pass a .twf path with --args."
    configureExample("com.psiwray.organization.terminal_window.examples.CustomFontKt")
}

tasks.register<JavaExec>("randomStress") {
    description = "Runs the randomized 80x24 matrix/renderer stress demo."
    configureExample("com.psiwray.organization.terminal_window.examples.RandomStressKt")
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
