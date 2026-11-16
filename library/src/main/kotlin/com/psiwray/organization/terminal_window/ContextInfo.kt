package com.psiwray.organization.terminal_window

/**
 * Immutable OpenGL context capability data captured at window creation.
 *
 * The type deliberately contains no GLFW window handle or other native
 * pointer. The strings are copied from the driver while the context is
 * current; they remain safe to inspect after the window is closed.
 *
 * @property requestedMajor requested OpenGL major version.
 * @property requestedMinor requested OpenGL minor version.
 * @property actualMajor major version parsed from [versionString].
 * @property actualMinor minor version parsed from [versionString].
 * @property versionString the driver's `GL_VERSION` string.
 * @property renderer the driver's `GL_RENDERER` string.
 * @property vendor the driver's `GL_VENDOR` string.
 * @property shadingLanguageVersion the driver's `GL_SHADING_LANGUAGE_VERSION`.
 * @property maxTextureSize the driver's `GL_MAX_TEXTURE_SIZE` value.
 * @throws IllegalArgumentException if a version is negative or
 *   `maxTextureSize` is below the OpenGL 3.3 core minimum.
 * @since 2.0.0
 */
public data class ContextInfo(
    public val requestedMajor: Int,
    public val requestedMinor: Int,
    public val actualMajor: Int,
    public val actualMinor: Int,
    public val versionString: String,
    public val renderer: String,
    public val vendor: String,
    public val shadingLanguageVersion: String,
    public val maxTextureSize: Int,
) {
    init {
        require(requestedMajor >= 1) { "requestedMajor must be positive: $requestedMajor" }
        require(requestedMinor >= 0) { "requestedMinor must be non-negative: $requestedMinor" }
        require(actualMajor >= 1) { "actualMajor must be positive: $actualMajor" }
        require(actualMinor >= 0) { "actualMinor must be non-negative: $actualMinor" }
        require(maxTextureSize >= MIN_MAX_TEXTURE_SIZE) {
            "maxTextureSize must be at least the OpenGL 3.3 core minimum " +
                "($MIN_MAX_TEXTURE_SIZE): $maxTextureSize"
        }
    }

    /** OpenGL specification minima and the library's supported core floor. */
    public companion object {
        /** Minimum supported OpenGL major version. @since 2.0.0 */
        public const val MIN_OPENGL_MAJOR: Int = 3

        /** Minimum supported OpenGL minor version. @since 2.0.0 */
        public const val MIN_OPENGL_MINOR: Int = 3

        /**
         * OpenGL 3.3 core minimum for `GL_MAX_TEXTURE_SIZE`.
         *
         * OpenGL 3.3 core specification, Table 6.38 (Implementation Dependent
         * Values), lists a minimum value of 1024. Drivers that report less do
         * not satisfy the core profile.
         * @since 2.0.0
         */
        public const val MIN_MAX_TEXTURE_SIZE: Int = 1024
    }
}
