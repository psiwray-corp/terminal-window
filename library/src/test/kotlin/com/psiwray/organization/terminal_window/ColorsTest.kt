package com.psiwray.organization.terminal_window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorsTest {
    @Test
    fun `rgb sets opaque alpha`() {
        assertEquals(0xFF123456.toInt(), Colors.rgb(0x12, 0x34, 0x56))
        assertEquals(Colors.BLACK, Colors.rgb(0, 0, 0))
        assertEquals(Colors.WHITE, Colors.rgb(255, 255, 255))
    }

    @Test
    fun `rgba packs all four channels`() {
        assertEquals(0x04010203, Colors.rgba(1, 2, 3, 4))
        assertEquals(Colors.TRANSPARENT, Colors.rgba(0, 0, 0, 0))
        assertEquals(Colors.WHITE, Colors.rgba(255, 255, 255, 255))
    }

    @Test
    fun `channels outside byte range are rejected`() {
        assertFailsWith<IllegalArgumentException> { Colors.rgb(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Colors.rgb(0, 256, 0) }
        assertFailsWith<IllegalArgumentException> { Colors.rgb(0, 0, 300) }
        assertFailsWith<IllegalArgumentException> { Colors.rgba(0, 0, 0, -1) }
        assertFailsWith<IllegalArgumentException> { Colors.rgba(0, 0, 0, 256) }
    }
}
