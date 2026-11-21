package com.psiwray.organization.terminal_window.input

import com.psiwray.organization.terminal_window.internal.InputQueue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class InputQueueTest {
    @Test
    fun `discrete events preserve enqueue order`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        queue.addListener(recorded)
        val press = KeyEvent(Key.A, 30, KeyAction.PRESS, Modifiers.NONE, repeat = false)
        val release = KeyEvent(Key.A, 30, KeyAction.RELEASE, Modifiers.NONE, repeat = false)
        val char = CharEvent(0x1F600)
        val scroll = ScrollEvent(0.0, -1.0)

        queue.offer(press)
        queue.offer(release)
        queue.offer(char)
        queue.offer(scroll)
        queue.drain()

        assertEquals(listOf(press, release, char, scroll), recorded.events)
    }

    @Test
    fun `cursor moves coalesce to the latest value after discrete events`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        queue.addListener(recorded)
        val key = KeyEvent(Key.B, 48, KeyAction.PRESS, Modifiers.NONE, repeat = false)
        val first = MouseMoveEvent(1, 2, 0, 0)
        val latest = MouseMoveEvent(3, 4, 1, 1)

        queue.offer(first)
        queue.offer(key)
        queue.offer(latest)
        queue.drain()

        assertEquals(listOf(key, latest), recorded.events)
    }

    @Test
    fun `cursor enter and leave coalesce to the latest value`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        queue.addListener(recorded)
        queue.offer(CursorEnterEvent(true))
        queue.offer(CursorEnterEvent(false))
        queue.drain()

        assertEquals(listOf<InputEvent>(CursorEnterEvent(false)), recorded.events)
    }

    @Test
    fun `window state slots keep the latest value and are never dropped`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        queue.addListener(recorded)
        repeat(
            InputQueue.CAPACITY,
        ) { queue.offer(KeyEvent(Key.A, 30, KeyAction.PRESS, Modifiers.NONE, repeat = false)) }

        queue.offer(ResizeEvent(640, 480))
        queue.offer(ResizeEvent(800, 600))
        queue.offer(ContentScaleEvent(1.0f, 1.0f))
        queue.offer(ContentScaleEvent(2.0f, 2.0f))
        queue.offer(FocusEvent(true))
        queue.offer(FocusEvent(false))
        queue.offer(CloseRequestedEvent)
        queue.drain()

        assertEquals(InputQueue.CAPACITY + 4, recorded.events.size)
        assertIs<ResizeEvent>(recorded.events[InputQueue.CAPACITY])
        assertEquals(800, (recorded.events[InputQueue.CAPACITY] as ResizeEvent).framebufferWidth)
        assertIs<ContentScaleEvent>(recorded.events[InputQueue.CAPACITY + 1])
        assertEquals(2.0f, (recorded.events[InputQueue.CAPACITY + 1] as ContentScaleEvent).x)
        assertEquals(FocusEvent(false), recorded.events[InputQueue.CAPACITY + 2])
        assertSame(CloseRequestedEvent, recorded.events[InputQueue.CAPACITY + 3])
        assertEquals(0, queue.droppedEvents)
    }

    @Test
    fun `a full queue drops discrete events and increments the counter`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        queue.addListener(recorded)
        val event = KeyEvent(Key.C, 46, KeyAction.PRESS, Modifiers.NONE, repeat = false)
        repeat(InputQueue.CAPACITY + 5) { queue.offer(event) }

        queue.drain()

        assertEquals(InputQueue.CAPACITY, recorded.events.size)
        assertEquals(5, queue.droppedEvents)
    }

    @Test
    fun `close survives a full queue`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        queue.addListener(recorded)
        val event = KeyEvent(Key.D, 32, KeyAction.PRESS, Modifiers.NONE, repeat = false)
        repeat(InputQueue.CAPACITY) { queue.offer(event) }

        queue.offer(CloseRequestedEvent)
        queue.drain()

        assertEquals(InputQueue.CAPACITY + 1, recorded.events.size)
        assertSame(CloseRequestedEvent, recorded.events.last())
        assertEquals(0, queue.droppedEvents)
    }

    @Test
    fun `listener addition during dispatch affects only subsequent events`() {
        val queue = InputQueue()
        val first = RecordingListener()
        val late = RecordingListener()
        val second = RecordingListener()
        val added = booleanArrayOf(false)
        queue.addListener(
            object : InputListener {
                override fun onKey(event: KeyEvent) {
                    first.onKey(event)
                    if (!added[0]) {
                        added[0] = true
                        queue.addListener(late)
                    }
                }
            },
        )
        queue.addListener(
            object : InputListener {
                override fun onKey(event: KeyEvent) {
                    second.onKey(event)
                }
            },
        )

        val one = KeyEvent(Key.E, 18, KeyAction.PRESS, Modifiers.NONE, repeat = false)
        val two = KeyEvent(Key.E, 18, KeyAction.RELEASE, Modifiers.NONE, repeat = false)
        queue.offer(one)
        queue.offer(two)
        queue.drain()

        assertEquals(listOf<InputEvent>(one, two), first.events)
        assertEquals(listOf<InputEvent>(one, two), second.events)
        assertEquals(listOf<InputEvent>(two), late.events)
    }

    @Test
    fun `listener removal during dispatch is safe`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        val removed = RecordingListener()
        queue.addListener(recorded)
        queue.addListener(
            object : InputListener {
                override fun onKey(event: KeyEvent) {
                    removed.onKey(event)
                    queue.removeListener(this)
                }
            },
        )

        val one = KeyEvent(Key.F, 33, KeyAction.PRESS, Modifiers.NONE, repeat = false)
        val two = KeyEvent(Key.F, 33, KeyAction.RELEASE, Modifiers.NONE, repeat = false)
        queue.offer(one)
        queue.offer(two)
        queue.drain()

        assertEquals(listOf<InputEvent>(one, two), recorded.events)
        assertEquals(listOf<InputEvent>(one), removed.events)
    }

    @Test
    fun `dispatch runs on the thread that drains the queue`() {
        val queue = InputQueue()
        val dispatcher = AtomicReference<String>()
        queue.addListener(
            object : InputListener {
                override fun onKey(event: KeyEvent) {
                    dispatcher.set(Thread.currentThread().name)
                }
            },
        )
        queue.offer(KeyEvent(Key.H, 35, KeyAction.PRESS, Modifiers.NONE, repeat = false))

        val owner = Thread({ queue.drain() }, "input-owner")
        owner.start()
        owner.join(5_000)

        assertEquals("input-owner", dispatcher.get())
    }

    @Test
    fun `clear discards pending events`() {
        val queue = InputQueue()
        val recorded = RecordingListener()
        queue.addListener(recorded)
        queue.offer(KeyEvent(Key.G, 34, KeyAction.PRESS, Modifiers.NONE, repeat = false))
        queue.offer(CloseRequestedEvent)

        queue.clear()
        queue.drain()

        assertEquals(emptyList(), recorded.events)
    }

    @Test
    fun `clipboard results follow input events in request order`() {
        val queue = InputQueue()
        val events = RecordingListener()
        val clipboard = mutableListOf<String?>()
        queue.addListener(events)
        queue.addClipboardListener { clipboard.add(it) }
        val key = KeyEvent(Key.I, 23, KeyAction.PRESS, Modifiers.NONE, repeat = false)

        queue.offer(key)
        queue.offerClipboardText("first")
        queue.offerClipboardText(null)
        queue.offerClipboardText("Grüße 世界 😀")
        queue.drain()

        assertEquals(listOf<InputEvent>(key), events.events)
        assertEquals(listOf("first", null, "Grüße 世界 😀"), clipboard)
    }

    @Test
    fun `clipboard results are not dropped by a full event queue`() {
        val queue = InputQueue()
        val clipboard = mutableListOf<String?>()
        queue.addClipboardListener { clipboard.add(it) }
        val event = KeyEvent(Key.J, 36, KeyAction.PRESS, Modifiers.NONE, repeat = false)
        repeat(InputQueue.CAPACITY + 1) { queue.offer(event) }

        queue.offerClipboardText("paste")
        queue.drain()

        assertEquals(1, queue.droppedEvents)
        assertEquals(listOf<String?>("paste"), clipboard)
    }

    @Test
    fun `clipboard results go only to listeners registered at drain time`() {
        val queue = InputQueue()
        val removed = mutableListOf<String?>()
        val recorded = mutableListOf<String?>()
        val listener = ClipboardRecorder(removed)
        queue.addClipboardListener(listener)
        queue.addClipboardListener(ClipboardRecorder(recorded))

        queue.offerClipboardText("one")
        queue.removeClipboardListener(listener)
        queue.offerClipboardText("two")
        queue.drain()

        assertEquals(emptyList<String?>(), removed)
        assertEquals(listOf<String?>("one", "two"), recorded)
    }

    @Test
    fun `clipboard results are discarded with the queue`() {
        val queue = InputQueue()
        val clipboard = mutableListOf<String?>()
        queue.addClipboardListener { clipboard.add(it) }

        queue.offerClipboardText("stale")
        queue.clear()
        queue.drain()

        assertEquals(emptyList(), clipboard)
    }

    private class ClipboardRecorder(
        private val texts: MutableList<String?>,
    ) : ClipboardListener {
        override fun onClipboardText(text: String?) {
            texts.add(text)
        }
    }

    private class RecordingListener : InputListener {
        val events: MutableList<InputEvent> = mutableListOf()

        override fun onKey(event: KeyEvent) {
            events.add(event)
        }

        override fun onChar(event: CharEvent) {
            events.add(event)
        }

        override fun onCursorEnter(event: CursorEnterEvent) {
            events.add(event)
        }

        override fun onMouseMove(event: MouseMoveEvent) {
            events.add(event)
        }

        override fun onMouseButton(event: MouseButtonEvent) {
            events.add(event)
        }

        override fun onScroll(event: ScrollEvent) {
            events.add(event)
        }

        override fun onResize(event: ResizeEvent) {
            events.add(event)
        }

        override fun onContentScale(event: ContentScaleEvent) {
            events.add(event)
        }

        override fun onFocus(event: FocusEvent) {
            events.add(event)
        }

        override fun onCloseRequested(event: CloseRequestedEvent) {
            events.add(event)
        }
    }
}
