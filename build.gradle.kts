plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless) apply false
}

// One host classification for :library and :examples. Native artifacts are
// runtime-only and never published.
val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val isX64 = hostArch == "x86_64" || hostArch == "amd64"
val isArm64 = hostArch == "aarch64" || hostArch == "arm64"
val isMac = hostOs.contains("mac") || hostOs.contains("darwin")
val lwjglNativesClassifier = when {
    hostOs.contains("linux") && isX64 -> "natives-linux"
    hostOs.contains("linux") && isArm64 -> "natives-linux-arm64"
    hostOs.contains("windows") && isX64 -> "natives-windows"
    hostOs.contains("windows") && isArm64 -> "natives-windows-arm64"
    isMac && isX64 -> "natives-macos"
    isMac && isArm64 -> "natives-macos-arm64"
    else -> error("Unsupported host for LWJGL natives: $hostOs/$hostArch")
}
extra["lwjglNativesClassifier"] = lwjglNativesClassifier

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}
