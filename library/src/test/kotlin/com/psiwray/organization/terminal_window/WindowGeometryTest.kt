package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.internal.WindowGeometry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WindowGeometryTest {
    @Test
    fun `cell pixels round integer and content scale`() {
        assertEquals(16, WindowGeometry.cellPixels(8, 1, 2.0f))
        assertEquals(24, WindowGeometry.cellPixels(8, 3, 1.0f))
        assertEquals(20, WindowGeometry.cellPixels(8, 1, 2.5f))
        assertEquals(1, WindowGeometry.cellPixels(8, 1, 0.01f))
    }

    @Test
    fun `grid dimensions clamp empty and partial axes`() {
        assertEquals(80, WindowGeometry.dimension(1280, 16))
        assertEquals(24, WindowGeometry.dimension(384, 16))
        assertEquals(1, WindowGeometry.dimension(0, 16))
        assertEquals(1, WindowGeometry.dimension(15, 16))
        assertEquals(2, WindowGeometry.dimension(32, 16))
    }
}
