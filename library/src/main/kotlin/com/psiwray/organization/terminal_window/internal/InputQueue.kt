package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.input.CharEvent
import com.psiwray.organization.terminal_window.input.ClipboardListener
import com.psiwray.organization.terminal_window.input.CloseRequestedEvent
import com.psiwray.organization.terminal_window.input.ContentScaleEvent
import com.psiwray.organization.terminal_window.input.CursorEnterEvent
import com.psiwray.organization.terminal_window.input.FocusEvent
import com.psiwray.organization.terminal_window.input.InputEvent
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.KeyEvent
import com.psiwray.organization.terminal_window.input.MouseButtonEvent
import com.psiwray.organization.terminal_window.input.MouseMoveEvent
import com.psiwray.organization.terminal_window.input.ResizeEvent
import com.psiwray.organization.terminal_window.input.ScrollEvent
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Bounded, non-blocking handoff from GLFW callbacks to the window owner thread.
 *
 * Discrete events are queued in arrival order. Cursor movement and the latest
 * window-state events are coalesced in dedicated slots: only the newest value
 * is delivered, and these values are never dropped. State slots are delivered
 * after the discrete queue, in cursor, enter/leave, resize, content-scale, focus, close
 * order. Completed clipboard reads are delivered after the state slots. All
 * dispatch happens on the owner thread in [drain].
 */
internal class InputQueue {
    private val events: ArrayBlockingQueue<InputEvent> = ArrayBlockingQueue(CAPACITY)
    private val latestMove: AtomicReference<MouseMoveEvent?> = AtomicReference(null)
    private val latestCursorEnter: AtomicReference<CursorEnterEvent?> = AtomicReference(null)
    private val latestResize: AtomicReference<ResizeEvent?> = AtomicReference(null)
    private val latestContentScale: AtomicReference<ContentScaleEvent?> = AtomicReference(null)
    private val latestFocus: AtomicReference<FocusEvent?> = AtomicReference(null)
    private val latestClose: AtomicReference<CloseRequestedEvent?> = AtomicReference(null)
    private val dropped: AtomicInteger = AtomicInteger(0)
    private val listeners: CopyOnWriteArrayList<InputListener> = CopyOnWriteArrayList()

    /**
     * Completed clipboard reads waiting for the owner thread.
     *
     * Request-driven and low-frequency, so the queue is unbounded and results
     * are never coalesced or dropped: every request produces exactly one
     * delivery.
     */
    private val clipboardResults: ConcurrentLinkedQueue<ClipboardResult> = ConcurrentLinkedQueue()
    private val clipboardListeners: CopyOnWriteArrayList<ClipboardListener> = CopyOnWriteArrayList()

    /** Number of discrete events dropped because the queue was full. */
    internal val droppedEvents: Int
        get() = dropped.get()

    /** Registers [listener] for dispatch on the owner thread. */
    internal fun addListener(listener: InputListener) {
        listeners.addIfAbsent(listener)
    }

    /** Removes [listener]; a listener already being dispatched is unaffected. */
    internal fun removeListener(listener: InputListener) {
        listeners.remove(listener)
    }

    /** Registers [listener] for clipboard results on the owner thread. */
    internal fun addClipboardListener(listener: ClipboardListener) {
        clipboardListeners.addIfAbsent(listener)
    }

    /** Removes [listener]; results already queued for another listener are unaffected. */
    internal fun removeClipboardListener(listener: ClipboardListener) {
        clipboardListeners.remove(listener)
    }

    /**
     * Records one callback event without blocking or running user code.
     *
     * Moves and window-state events replace their previous slot value. All
     * other events use [ArrayBlockingQueue.offer]; a full queue drops the event
     * and increments [droppedEvents].
     */
    internal fun offer(event: InputEvent) {
        when (event) {
            is MouseMoveEvent -> {
                latestMove.set(event)
            }

            is CursorEnterEvent -> {
                latestCursorEnter.set(event)
            }

            is ResizeEvent -> {
                latestResize.set(event)
            }

            is ContentScaleEvent -> {
                latestContentScale.set(event)
            }

            is FocusEvent -> {
                latestFocus.set(event)
            }

            is CloseRequestedEvent -> {
                latestClose.set(event)
            }

            else -> {
                if (!events.offer(event)) {
                    dropped.incrementAndGet()
                }
            }
        }
    }

    /**
     * Records one completed clipboard read for owner-thread delivery.
     *
     * Never blocks and never runs user code. A `null` [text] means the
     * clipboard held no text.
     */
    internal fun offerClipboardText(text: String?) {
        clipboardResults.add(ClipboardResult(text))
    }

    /**
     * Drains pending events to registered listeners.
     *
     * Call only on the window owner thread. The listener list is a
     * copy-on-write snapshot: listeners added during this call receive only
     * events offered after this drain begins.
     */
    internal fun drain() {
        while (true) {
            val event = events.poll() ?: break
            dispatch(event)
        }

        val move = latestMove.getAndSet(null)
        if (move != null) {
            dispatch(move)
        }
        val cursorEnter = latestCursorEnter.getAndSet(null)
        if (cursorEnter != null) {
            dispatch(cursorEnter)
        }
        val resize = latestResize.getAndSet(null)
        if (resize != null) {
            dispatch(resize)
        }
        val contentScale = latestContentScale.getAndSet(null)
        if (contentScale != null) {
            dispatch(contentScale)
        }
        val focus = latestFocus.getAndSet(null)
        if (focus != null) {
            dispatch(focus)
        }
        val close = latestClose.getAndSet(null)
        if (close != null) {
            dispatch(close)
        }

        while (true) {
            val result = clipboardResults.poll() ?: break
            for (listener in clipboardListeners) {
                listener.onClipboardText(result.text)
            }
        }
    }

    /** Discards pending events; used when the native window is destroyed. */
    internal fun clear() {
        events.clear()
        clipboardResults.clear()
        latestMove.set(null)
        latestCursorEnter.set(null)
        latestResize.set(null)
        latestContentScale.set(null)
        latestFocus.set(null)
        latestClose.set(null)
    }

    private fun dispatch(event: InputEvent) {
        for (listener in listeners) {
            when (event) {
                is KeyEvent -> listener.onKey(event)
                is CharEvent -> listener.onChar(event)
                is CursorEnterEvent -> listener.onCursorEnter(event)
                is MouseMoveEvent -> listener.onMouseMove(event)
                is MouseButtonEvent -> listener.onMouseButton(event)
                is ScrollEvent -> listener.onScroll(event)
                is ResizeEvent -> listener.onResize(event)
                is ContentScaleEvent -> listener.onContentScale(event)
                is FocusEvent -> listener.onFocus(event)
                is CloseRequestedEvent -> listener.onCloseRequested(event)
            }
        }
    }

    /** Queue element for a completed read; [ConcurrentLinkedQueue] rejects `null` elements. */
    private class ClipboardResult(
        val text: String?,
    )

    internal companion object {
        internal const val CAPACITY: Int = 1024
    }
}
