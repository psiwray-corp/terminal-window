package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.internal.FrameHost
import com.psiwray.organization.terminal_window.internal.FrameScheduler
import com.psiwray.organization.terminal_window.internal.FrameSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FramePipelineTest {
    @Test
    fun `dirty matrix uploads once and reports exact stats`() {
        val matrix = TerminalMatrix(4, 3)
        val sink = RecordingSink()
        val host = TestHost()
        val scheduler = FrameScheduler(matrix, sink, host)

        matrix.set(0, 0, 'A'.code, Colors.WHITE, Colors.BLACK)
        val stats = scheduler.tick()

        assertEquals(1, stats.uploads)
        assertEquals(4 * 3 * 16L, stats.uploadBytes)
        assertEquals(4 * 3, stats.cellsDrawn)
        assertEquals(1, stats.drawCalls)
        assertEquals(listOf("upload", "draw", "swap"), sink.calls)
        assertFalse(matrix.isDirty)
    }

    @Test
    fun `ten thousand cell writes produce one upload`() {
        val matrix = TerminalMatrix(100, 100)
        var index = 0
        while (index < 10_000) {
            matrix.set(index % 100, index / 100, 'A'.code, Colors.WHITE, Colors.BLACK)
            index++
        }
        val sink = RecordingSink()
        val scheduler = FrameScheduler(matrix, sink, TestHost())

        val stats = scheduler.tick()

        assertEquals(1, stats.uploads)
        assertEquals(10_000, stats.cellsDrawn)
        assertEquals(1, stats.drawCalls)
        assertEquals(1, sink.uploads)
        assertEquals(1, sink.draws)
        assertEquals(1, sink.swaps)
    }

    @Test
    fun `clean unforced tick performs no sink work`() {
        val matrix = TerminalMatrix(2, 2)
        val sink = RecordingSink()
        val host = TestHost()
        val scheduler = FrameScheduler(matrix, sink, host)

        val stats = scheduler.tick()

        assertEquals(0, stats.uploads)
        assertEquals(0L, stats.uploadBytes)
        assertEquals(0, stats.drawCalls)
        assertEquals(0, stats.cellsDrawn)
        assertTrue(sink.calls.isEmpty())

        assertSame(stats, scheduler.tick(), "stable clean ticks reuse the zeroed stats")
    }

    @Test
    fun `force repaint draws every tick and uploads nothing while clean`() {
        val matrix = TerminalMatrix(2, 2)
        val sink = RecordingSink()
        val host = TestHost().apply { force = true }
        val scheduler = FrameScheduler(matrix, sink, host)

        val first = scheduler.tick()
        val second = scheduler.tick()

        assertEquals(0, first.uploads)
        assertEquals(0L, first.uploadBytes)
        assertEquals(1, first.drawCalls)
        assertEquals(0, first.cellsDrawn)
        assertEquals(1, second.drawCalls)
        assertEquals(listOf("draw", "swap", "draw", "swap"), sink.calls)

        host.force = false
        sink.calls.clear()
        scheduler.tick()
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `one-shot repaint renders exactly one tick`() {
        val matrix = TerminalMatrix(2, 2)
        val sink = RecordingSink()
        val host = TestHost().apply { repaintPending = true }
        val scheduler = FrameScheduler(matrix, sink, host)

        val stats = scheduler.tick()
        assertEquals(0, stats.uploads)
        assertEquals(1, stats.drawCalls)
        assertFalse(host.repaintPending)

        scheduler.tick()
        assertEquals(listOf("draw", "swap"), sink.calls)
    }

    @Test
    fun `resize produces one upload and one draw`() {
        val matrix = TerminalMatrix(2, 2)
        val sink = RecordingSink()
        val host =
            TestHost().apply {
                resizeAction = {
                    matrix.resize(3, 4, ResizePolicy.PRESERVE_TOP_LEFT)
                    true
                }
            }
        val scheduler = FrameScheduler(matrix, sink, host)

        val stats = scheduler.tick()

        assertEquals(1, stats.uploads)
        assertEquals(12, stats.cellsDrawn)
        assertEquals(12L * 16L, stats.uploadBytes)
        assertEquals(1, stats.drawCalls)
        assertFalse(matrix.isDirty)
    }

    @Test
    fun `minimized framebuffer performs no sink work and keeps a repaint request`() {
        val matrix = TerminalMatrix(2, 2)
        matrix.set(0, 0, 'A'.code, Colors.WHITE, Colors.BLACK)
        val sink = RecordingSink()
        val host =
            TestHost().apply {
                minimized = true
                repaintPending = true
                force = true
            }
        val scheduler = FrameScheduler(matrix, sink, host)

        val stats = scheduler.tick()

        assertEquals(0, stats.uploads)
        assertEquals(0L, stats.uploadBytes)
        assertEquals(0, stats.drawCalls)
        assertEquals(0, stats.cellsDrawn)
        assertTrue(host.repaintPending)
        assertTrue(matrix.isDirty)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `input dispatch completes before any sink call`() {
        val matrix = TerminalMatrix(2, 2)
        val sink = RecordingSink()
        val host =
            TestHost().apply {
                drainAction = {
                    sink.calls.add("drain")
                    matrix.set(0, 0, 'A'.code, Colors.WHITE, Colors.BLACK)
                }
            }
        val scheduler = FrameScheduler(matrix, sink, host)

        scheduler.tick()

        assertEquals(listOf("drain", "upload", "draw", "swap"), sink.calls)
    }

    @Test
    fun `listener exception aborts before upload and keeps the request pending`() {
        val matrix = TerminalMatrix(2, 2)
        matrix.set(0, 0, 'A'.code, Colors.WHITE, Colors.BLACK)
        val sink = RecordingSink()
        val host =
            TestHost().apply {
                repaintPending = true
                drainAction = { throw IllegalStateException("listener failed") }
            }
        val scheduler = FrameScheduler(matrix, sink, host)

        assertFailsWith<IllegalStateException> { scheduler.tick() }
        assertTrue(host.repaintPending)
        assertTrue(matrix.isDirty)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun `sink failure restores a consumed repaint request`() {
        val matrix = TerminalMatrix(2, 2)
        matrix.set(0, 0, 'A'.code, Colors.WHITE, Colors.BLACK)
        val host = TestHost().apply { repaintPending = true }
        val sink =
            object : FrameSink {
                override fun upload() {}

                override fun draw(): Unit = throw IllegalStateException("draw failed")

                override fun swap() {}
            }
        val scheduler = FrameScheduler(matrix, sink, host)

        assertFailsWith<IllegalStateException> { scheduler.tick() }
        assertTrue(host.repaintPending)
    }

    @Test
    fun `dropped events are reported from the host snapshot`() {
        val host = TestHost().apply { droppedEvents = 7 }
        val scheduler = FrameScheduler(TerminalMatrix(1, 1), RecordingSink(), host)

        val stats = scheduler.tick()

        assertEquals(7, stats.droppedEvents)
    }

    private class TestHost : FrameHost {
        var drainAction: () -> Unit = {}
        var resizeAction: () -> Boolean = { false }
        var repaintPending: Boolean = false
        var force: Boolean = false
        var minimized: Boolean = false
        override var droppedEvents: Int = 0

        override fun drainEvents() {
            drainAction()
        }

        override fun applyPendingResize(): Boolean = resizeAction()

        override fun consumeRepaintRequest(): Boolean {
            val consumed = repaintPending
            repaintPending = false
            return consumed
        }

        override fun restoreRepaintRequest() {
            repaintPending = true
        }

        override val forceRepaint: Boolean
            get() = force

        override val isMinimized: Boolean
            get() = minimized
    }

    private class RecordingSink : FrameSink {
        val calls: MutableList<String> = mutableListOf()
        var uploads: Int = 0
            private set
        var draws: Int = 0
            private set
        var swaps: Int = 0
            private set

        override fun upload() {
            uploads++
            calls.add("upload")
        }

        override fun draw() {
            draws++
            calls.add("draw")
        }

        override fun swap() {
            swaps++
            calls.add("swap")
        }
    }
}
