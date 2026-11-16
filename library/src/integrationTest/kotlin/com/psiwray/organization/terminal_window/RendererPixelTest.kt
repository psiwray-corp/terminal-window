package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.AtlasBaker
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.internal.Renderer
import com.psiwray.organization.terminal_window.testing.GlAssertions
import com.psiwray.organization.terminal_window.testing.GlTestContext
import com.psiwray.organization.terminal_window.testing.TestFramebuffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class RendererPixelTest {
    private lateinit var context: GlTestContext
    private lateinit var font: BitmapFont

    @BeforeAll
    fun createContext() {
        context = GlTestContext.create()
        font = BitmapFont.parse(fontFixture())
    }

    @AfterAll
    fun destroyContext() {
        if (::context.isInitialized) {
            context.close()
        }
    }

    @AfterEach
    fun noErrors() {
        GlAssertions.assertNoGlError()
    }

    @Test
    @Timeout(30)
    fun `renders exact colors at 2x with upright orientation`() {
        val matrix =
            TerminalMatrix(
                columns = 1,
                rows = 1,
                defaultForeground = Colors.WHITE,
                defaultBackground = Colors.BLACK,
            )
        matrix.set(0, 0, 'A'.code, 0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        val renderer =
            Renderer(
                matrix = matrix,
                atlas = AtlasBaker.bake(font, 4096),
                framebufferWidth = 64,
                framebufferHeight = 64,
                cellWidthPx = 16,
                cellHeightPx = 32,
                atlasCellWidth = font.cellWidth,
                atlasCellHeight = font.cellHeight,
                swapAction = {},
            )
        TestFramebuffer(64, 64).use { framebuffer ->
            try {
                renderer.upload()
                renderer.draw()

                // A's top cap is a filled stroke at glyph rows 4-5. If the
                // glyph were vertically mirrored this pixel would be background.
                GlAssertions.assertPixel(0xFFFF0000.toInt(), framebuffer, 5, 9)
                GlAssertions.assertPixel(0xFF0000FF.toInt(), framebuffer, 0, 0)
                GlAssertions.assertPixel(0xFF0000FF.toInt(), framebuffer, 15, 31)
                GlAssertions.assertPixel(Colors.BLACK, framebuffer, 48, 48)
            } finally {
                renderer.close()
            }
        }
    }

    @Test
    @Timeout(30)
    fun `renders exact colors at 1x and 3x with upright orientation`() {
        assertGlyphAt(cellWidthPx = 8, cellHeightPx = 16)
        assertGlyphAt(cellWidthPx = 24, cellHeightPx = 48)
    }

    @Test
    @Timeout(60)
    fun `upload and draw do not allocate after warmup`() {
        val matrix = TerminalMatrix(80, 24)
        val renderer =
            Renderer(
                matrix = matrix,
                atlas = AtlasBaker.bake(font, 4096),
                framebufferWidth = 640,
                framebufferHeight = 384,
                cellWidthPx = 8,
                cellHeightPx = 16,
                atlasCellWidth = font.cellWidth,
                atlasCellHeight = font.cellHeight,
                swapAction = {},
            )
        try {
            var warmup = 0
            while (warmup < 20) {
                renderer.upload()
                renderer.draw()
                warmup++
            }

            val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
            val before = bean.currentThreadAllocatedBytes
            var frame = 0
            while (frame < 200) {
                renderer.upload()
                renderer.draw()
                frame++
            }
            val allocated = bean.currentThreadAllocatedBytes - before
            assertTrue(allocated / 200 < 1024, "allocated $allocated bytes over 200 frames")
        } finally {
            renderer.close()
        }
    }

    @Test
    @Timeout(30)
    fun `close is idempotent and rejects later rendering`() {
        val matrix = TerminalMatrix(1, 1)
        val renderer =
            Renderer(
                matrix = matrix,
                atlas = AtlasBaker.bake(font, 4096),
                framebufferWidth = 64,
                framebufferHeight = 64,
                cellWidthPx = 16,
                cellHeightPx = 32,
                atlasCellWidth = font.cellWidth,
                atlasCellHeight = font.cellHeight,
                swapAction = {},
            )
        renderer.close()
        renderer.close()
        assertFailsWith<IllegalStateException> { renderer.upload() }
        assertFailsWith<IllegalStateException> { renderer.draw() }
    }

    private fun assertGlyphAt(
        cellWidthPx: Int,
        cellHeightPx: Int,
    ) {
        val matrix =
            TerminalMatrix(
                columns = 1,
                rows = 1,
                defaultForeground = Colors.WHITE,
                defaultBackground = Colors.BLACK,
            )
        matrix.set(0, 0, 'A'.code, 0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        val renderer =
            Renderer(
                matrix = matrix,
                atlas = AtlasBaker.bake(font, 4096),
                framebufferWidth = 64,
                framebufferHeight = 64,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                atlasCellWidth = font.cellWidth,
                atlasCellHeight = font.cellHeight,
                swapAction = {},
            )
        TestFramebuffer(64, 64).use { framebuffer ->
            try {
                renderer.upload()
                renderer.draw()

                // A's top cap is a filled stroke at glyph rows 4-5; sample its
                // center at each integer scale to catch a vertical mirror.
                val scaleX = cellWidthPx / font.cellWidth
                val scaleY = cellHeightPx / font.cellHeight
                GlAssertions.assertPixel(
                    0xFFFF0000.toInt(),
                    framebuffer,
                    (2 * scaleX) + (scaleX / 2),
                    (4 * scaleY) + (scaleY / 2),
                )
                GlAssertions.assertPixel(0xFF0000FF.toInt(), framebuffer, 0, 0)
                GlAssertions.assertPixel(0xFF0000FF.toInt(), framebuffer, cellWidthPx - 1, cellHeightPx - 1)
                GlAssertions.assertPixel(Colors.BLACK, framebuffer, 48, 48)
            } finally {
                renderer.close()
            }
        }
    }

    private fun fontFixture(): Path {
        val candidates =
            listOf(
                Path.of("src/test/resources/fonts/valid/ascii-8x16.twf"),
                Path.of("library/src/test/resources/fonts/valid/ascii-8x16.twf"),
            )
        return candidates.firstOrNull { Files.isRegularFile(it) }
            ?: error("missing font fixture; tried ${candidates.joinToString()}")
    }
}
