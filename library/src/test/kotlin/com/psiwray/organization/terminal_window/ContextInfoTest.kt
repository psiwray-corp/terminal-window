package com.psiwray.organization.terminal_window

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ContextInfoTest {
    @Test
    fun `rejects a texture size below the OpenGL 3 3 core minimum`() {
        assertFailsWith<IllegalArgumentException> {
            ContextInfo(
                requestedMajor = ContextInfo.MIN_OPENGL_MAJOR,
                requestedMinor = ContextInfo.MIN_OPENGL_MINOR,
                actualMajor = ContextInfo.MIN_OPENGL_MAJOR,
                actualMinor = ContextInfo.MIN_OPENGL_MINOR,
                versionString = "3.3",
                renderer = "test",
                vendor = "test",
                shadingLanguageVersion = "3.30",
                maxTextureSize = ContextInfo.MIN_MAX_TEXTURE_SIZE - 1,
            )
        }
    }
}
