package com.psiwray.organization.terminal_window.benchmarks

import com.psiwray.organization.terminal_window.ContextInfo
import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.TerminalWindowService
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.sun.management.ThreadMXBean
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.SplittableRandom

private const val WARMUP_FRAMES: Int = 2_000
private const val MEASURED_FRAMES: Int = 10_000
private const val MAX_ALLOCATION_PER_FRAME: Double = 1_024.0
private const val DEFAULT_FONT_PATH: String = "examples/fonts/ascii-8x16.twf"
private const val OPAQUE_ALPHA: Int = 0xFF000000.toInt()

private val SCENARIOS: Array<Scenario> =
    arrayOf(
        Scenario("full-80x24", columns = 80, rows = 24, singleCell = false),
        Scenario("full-240x67", columns = 240, rows = 67, singleCell = false),
        Scenario("one-cell-240x67", columns = 240, rows = 67, singleCell = true),
    )

/**
 * Runs the section 20 offscreen frame benchmark.
 *
 * Each scenario opens a hidden, vsyncless window, binds an RGBA8 FBO, renders
 * the full matrix after 2,000 warm-up frames, then measures 10,000 frames.
 * The output is a table of upload bytes, frame time, allocation, draw calls,
 * upload throughput, and pixel throughput.
 *
 * Optional `--warmup=N`, `--frames=N`, and a positional font path are accepted
 * for local investigation. The defaults are the documented benchmark run.
 */
public fun main(args: Array<String>) {
    val warmupFrames = positiveArgument(args, "--warmup=", WARMUP_FRAMES)
    val measuredFrames = positiveArgument(args, "--frames=", MEASURED_FRAMES)
    val font = BitmapFont.parse(fontPath(args))
    val service = TerminalWindowService.initialize()
    try {
        val results = ArrayList<ScenarioResult>(SCENARIOS.size)
        var scenarioIndex = 0
        while (scenarioIndex < SCENARIOS.size) {
            val scenario = SCENARIOS[scenarioIndex]
            val window =
                service.openWindow(
                    TerminalWindowConfig(
                        font = font,
                        title = "benchmark | ${scenario.name}",
                        columns = scenario.columns,
                        rows = scenario.rows,
                        resizable = false,
                        visible = false,
                        vsync = false,
                    ),
                )
            try {
                results.add(runScenario(window, scenario, font, warmupFrames, measuredFrames))
            } finally {
                window.close()
                service.pump()
            }
            scenarioIndex++
        }

        printReport(font, results, warmupFrames, measuredFrames)
        var resultIndex = 0
        while (resultIndex < results.size) {
            val result = results[resultIndex]
            check(result.allocationPerFrame <= MAX_ALLOCATION_PER_FRAME) {
                "${result.scenario.name}: ${result.allocationPerFrame} B/frame exceeds " +
                    "the $MAX_ALLOCATION_PER_FRAME B/frame budget"
            }
            check(result.mismatches == 0) {
                "${result.scenario.name}: RenderStats did not report one upload and one draw for every frame"
            }
            check(result.uploads == measuredFrames.toLong()) {
                "${result.scenario.name}: expected $measuredFrames uploads, got ${result.uploads}"
            }
            check(result.drawCalls == measuredFrames.toLong()) {
                "${result.scenario.name}: expected $measuredFrames draw calls, got ${result.drawCalls}"
            }
            resultIndex++
        }
    } finally {
        service.close()
    }
}

private fun runScenario(
    window: TerminalWindow,
    scenario: Scenario,
    font: BitmapFont,
    warmupFrames: Int,
    measuredFrames: Int,
): ScenarioResult {
    val matrix = window.matrix
    val codePoints = font.codePoints.toIntArray()
    check(codePoints.isNotEmpty()) { "font has no glyphs" }
    val random = SplittableRandom(0x5EED_2026L)
    window.forceRepaint = true

    val viewport = queryViewport()
    check(viewport.width > 0 && viewport.height > 0) {
        "window framebuffer is ${viewport.width}x${viewport.height}"
    }
    val target = OffscreenTarget.create(viewport.width, viewport.height)
    try {
        fillFull(matrix, codePoints, random)
        var warmupIndex = 0
        while (warmupIndex < warmupFrames) {
            updateMatrix(matrix, codePoints, random, scenario, warmupIndex)
            window.frame()
            warmupIndex++
        }

        val allocationBean = allocationBean()
        val threadId = Thread.currentThread().threadId()
        var rotationIndex = 0
        val allocatedBefore = allocationBean.getThreadAllocatedBytes(threadId)
        val started = System.nanoTime()
        var frameIndex = 0
        var uploads = 0L
        var uploadBytes = 0L
        var drawCalls = 0L
        var cellsDrawn = 0L
        var mismatches = 0
        val expectedCells = scenario.columns.toLong() * scenario.rows
        while (frameIndex < measuredFrames) {
            if (scenario.singleCell) {
                rotationIndex = updateOneCell(matrix, codePoints, random, rotationIndex)
            } else {
                fillFull(matrix, codePoints, random)
            }
            window.frame()
            val stats = window.renderStats()
            uploads += stats.uploads.toLong()
            uploadBytes += stats.uploadBytes
            drawCalls += stats.drawCalls.toLong()
            cellsDrawn += stats.cellsDrawn.toLong()
            if (stats.uploads != 1 ||
                stats.drawCalls != 1 ||
                stats.cellsDrawn.toLong() != expectedCells ||
                stats.uploadBytes != expectedCells * 16L
            ) {
                mismatches++
            }
            frameIndex++
        }
        val allocatedAfter = allocationBean.getThreadAllocatedBytes(threadId)
        val elapsedNanos = System.nanoTime() - started
        return ScenarioResult(
            scenario = scenario,
            contextInfo = window.contextInfo,
            framebufferWidth = target.width,
            framebufferHeight = target.height,
            frames = measuredFrames,
            elapsedNanos = elapsedNanos,
            allocatedBytes = allocatedAfter - allocatedBefore,
            uploads = uploads,
            uploadBytes = uploadBytes,
            drawCalls = drawCalls,
            cellsDrawn = cellsDrawn,
            mismatches = mismatches,
        )
    } finally {
        target.close()
    }
}

private fun updateMatrix(
    matrix: TerminalMatrix,
    codePoints: IntArray,
    random: SplittableRandom,
    scenario: Scenario,
    index: Int,
) {
    if (scenario.singleCell) {
        updateOneCell(matrix, codePoints, random, index)
    } else {
        fillFull(matrix, codePoints, random)
    }
}

private fun fillFull(
    matrix: TerminalMatrix,
    codePoints: IntArray,
    random: SplittableRandom,
) {
    var row = 0
    while (row < matrix.rows) {
        var column = 0
        while (column < matrix.columns) {
            matrix.set(
                column,
                row,
                codePoints[random.nextInt(codePoints.size)],
                randomColor(random),
                randomColor(random),
            )
            column++
        }
        row++
    }
}

private fun updateOneCell(
    matrix: TerminalMatrix,
    codePoints: IntArray,
    random: SplittableRandom,
    index: Int,
): Int {
    val columns = matrix.columns
    val row = index / columns
    val column = index - row * columns
    matrix.set(
        column,
        row,
        codePoints[random.nextInt(codePoints.size)],
        randomColor(random),
        randomColor(random),
    )
    return if (index + 1 >= columns * matrix.rows) 0 else index + 1
}

private fun randomColor(random: SplittableRandom): Int = random.nextInt() or OPAQUE_ALPHA

private fun queryViewport(): Viewport {
    val stack = MemoryStack.stackPush()
    return try {
        val viewport = stack.mallocInt(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        Viewport(viewport.get(2), viewport.get(3))
    } finally {
        stack.pop()
    }
}

private fun allocationBean(): ThreadMXBean {
    val bean =
        ManagementFactory.getThreadMXBean() as? ThreadMXBean
            ?: error("com.sun.management.ThreadMXBean is unavailable")
    check(bean.isThreadAllocatedMemorySupported) {
        "thread allocation measurement is not supported by this JVM"
    }
    check(bean.isThreadAllocatedMemoryEnabled) {
        "thread allocation measurement is disabled by this JVM"
    }
    return bean
}

private fun printReport(
    font: BitmapFont,
    results: List<ScenarioResult>,
    warmupFrames: Int,
    measuredFrames: Int,
) {
    val context = results.first().contextInfo
    val atlasBytes = font.glyphCount.toLong() * font.cellWidth * font.cellHeight
    println("context: ${context.vendor} | ${context.renderer} | ${context.versionString}")
    println(
        "font: ${font.glyphCount} glyphs, ${font.cellWidth}x${font.cellHeight} cells, " +
            "atlas budget $atlasBytes B (gutter-free, 1 B/px)",
    )
    println(
        "frames: warm-up=$warmupFrames measured=$measuredFrames; hidden window, vsync=0, FBO bound",
    )
    println(
        "JOML: the library cursor mapping uses primitive Double math; no Vector2f is used per cell.",
    )
    println(
        String.format(
            Locale.ROOT,
            "%-18s %7s %13s %12s %12s %11s %9s %11s %9s",
            "scenario",
            "cells",
            "framebuffer",
            "upload B/f",
            "ns/frame",
            "alloc B/f",
            "draw/f",
            "upload MB/s",
            "MPix/s",
        ),
    )
    var index = 0
    while (index < results.size) {
        val result = results[index]
        val pixels = result.framebufferWidth.toLong() * result.framebufferHeight
        val pixelRate = pixels * measuredFrames * 1_000.0 / result.elapsedNanos
        val uploadRate = result.uploadBytes * 1_000.0 / result.elapsedNanos
        println(
            String.format(
                Locale.ROOT,
                "%-18s %7d %13s %12d %12.0f %11.1f %9.2f %11.2f %9.2f",
                result.scenario.name,
                result.scenario.columns * result.scenario.rows,
                "${result.framebufferWidth}x${result.framebufferHeight}",
                result.uploadBytes / measuredFrames,
                result.nanosecondsPerFrame,
                result.allocationPerFrame,
                result.drawCalls.toDouble() / measuredFrames,
                uploadRate,
                pixelRate,
            ),
        )
        index++
    }
}

private fun positiveArgument(
    args: Array<String>,
    prefix: String,
    defaultValue: Int,
): Int {
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        if (argument.startsWith(prefix)) {
            val value =
                argument.substring(prefix.length).toIntOrNull()
                    ?: error("$prefix expects a positive integer: $argument")
            require(value > 0) { "$prefix expects a positive integer: $argument" }
            return value
        }
        index++
    }
    return defaultValue
}

private fun fontPath(args: Array<String>): Path {
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        if (!argument.startsWith("--")) {
            return Path.of(argument)
        }
        index++
    }
    val default = Path.of(DEFAULT_FONT_PATH)
    check(Files.isRegularFile(default)) { "font not found: $default" }
    return default
}

private class Scenario(
    val name: String,
    val columns: Int,
    val rows: Int,
    val singleCell: Boolean,
)

private class ScenarioResult(
    val scenario: Scenario,
    val contextInfo: ContextInfo,
    val framebufferWidth: Int,
    val framebufferHeight: Int,
    val frames: Int,
    val elapsedNanos: Long,
    val allocatedBytes: Long,
    val uploads: Long,
    val uploadBytes: Long,
    val drawCalls: Long,
    val cellsDrawn: Long,
    val mismatches: Int,
) {
    val nanosecondsPerFrame: Double get() = elapsedNanos.toDouble() / frames

    val allocationPerFrame: Double get() = allocatedBytes.toDouble() / frames
}

private data class Viewport(
    val width: Int,
    val height: Int,
)

private class OffscreenTarget private constructor(
    val width: Int,
    val height: Int,
    private val framebuffer: Int,
    private val texture: Int,
) : AutoCloseable {
    override fun close() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
        GL11.glDeleteTextures(texture)
        GL30.glDeleteFramebuffers(framebuffer)
    }

    companion object {
        fun create(
            width: Int,
            height: Int,
        ): OffscreenTarget {
            val framebuffer = GL30.glGenFramebuffers()
            val texture = GL11.glGenTextures()
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    width,
                    height,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    null as ByteBuffer?,
                )
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
                GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    texture,
                    0,
                )
                val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                check(status == GL30.GL_FRAMEBUFFER_COMPLETE) {
                    "FBO ${width}x$height is incomplete: 0x${status.toString(16)}"
                }
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0)
                return OffscreenTarget(width, height, framebuffer, texture)
            } catch (failure: Throwable) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
                GL11.glDeleteTextures(texture)
                GL30.glDeleteFramebuffers(framebuffer)
                throw failure
            }
        }
    }
}
