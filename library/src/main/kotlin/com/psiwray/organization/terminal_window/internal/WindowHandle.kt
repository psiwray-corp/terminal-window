package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.ContextInfo
import com.psiwray.organization.terminal_window.CursorImage
import com.psiwray.organization.terminal_window.CursorMode
import com.psiwray.organization.terminal_window.CursorShape
import com.psiwray.organization.terminal_window.GlException
import com.psiwray.organization.terminal_window.GlfwBackend
import com.psiwray.organization.terminal_window.RenderStats
import com.psiwray.organization.terminal_window.ResizePolicy
import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.TerminalWindowService
import com.psiwray.organization.terminal_window.WindowIcon
import com.psiwray.organization.terminal_window.font.AtlasBaker
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.CharEvent
import com.psiwray.organization.terminal_window.input.CloseRequestedEvent
import com.psiwray.organization.terminal_window.input.ContentScaleEvent
import com.psiwray.organization.terminal_window.input.CursorEnterEvent
import com.psiwray.organization.terminal_window.input.CursorPosition
import com.psiwray.organization.terminal_window.input.FocusEvent
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.Key
import com.psiwray.organization.terminal_window.input.KeyAction
import com.psiwray.organization.terminal_window.input.KeyEvent
import com.psiwray.organization.terminal_window.input.Modifiers
import com.psiwray.organization.terminal_window.input.MouseButton
import com.psiwray.organization.terminal_window.input.MouseButtonEvent
import com.psiwray.organization.terminal_window.input.MouseMoveEvent
import com.psiwray.organization.terminal_window.input.ResizeEvent
import com.psiwray.organization.terminal_window.input.ScrollEvent
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/** Framebuffer dimensions copied from GLFW on the service thread. */
internal data class FramebufferSize(
    val width: Int,
    val height: Int,
)

/** Window content scale copied from GLFW on the service thread. */
internal data class WindowContentScale(
    val x: Float,
    val y: Float,
)

/** Pure cell-grid geometry shared with the resize path. */
internal object WindowGeometry {
    /** Applies integer scale and content scale, clamping to one pixel. */
    internal fun cellPixels(
        cellSize: Int,
        scale: Int,
        contentScale: Float,
    ): Int {
        require(cellSize >= 1) { "cellSize must be positive: $cellSize" }
        require(scale >= 1) { "scale must be positive: $scale" }
        require(contentScale > 0.0f && contentScale.isFinite()) {
            "contentScale must be finite and positive: $contentScale"
        }
        return max(1, (cellSize.toFloat() * scale.toFloat() * contentScale).roundToInt())
    }

    /** Converts a framebuffer dimension to a cell count, clamping to one cell. */
    internal fun dimension(
        framebufferSize: Int,
        cellPixels: Int,
    ): Int {
        require(framebufferSize >= 0) { "framebufferSize must be non-negative: $framebufferSize" }
        require(cellPixels >= 1) { "cellPixels must be positive: $cellPixels" }
        return max(1, framebufferSize / cellPixels)
    }
}

/**
 * Service-thread registry of open windows.
 *
 * The registry is the source of truth for `runEventLoop()` and for per-window
 * callback routing. It is confined to the service thread; no synchronization
 * is used.
 */
internal class WindowRegistry {
    private val windows: LinkedHashMap<Long, TerminalWindow> = LinkedHashMap()

    internal fun add(window: TerminalWindow) {
        val previous = windows.put(window.rawHandle, window)
        check(previous == null) { "window handle ${window.rawHandle} is already registered" }
    }

    internal fun remove(handle: Long): TerminalWindow? = windows.remove(handle)

    internal fun isEmpty(): Boolean = windows.isEmpty()

    internal fun forEach(action: (TerminalWindow) -> Unit) {
        for (window in windows.values) {
            action(window)
        }
    }
}

/**
 * Owner of one GLFW window handle and its context-current state.
 *
 * The handle is created on the service thread and starts with the context
 * current on that thread. Owners transfer the context explicitly:
 * [releaseContext] on the current owner, then [acquireContext] on the new
 * owner. Creation, destruction, and window-state calls remain on the service
 * thread.
 */
internal class WindowHandle private constructor(
    private val service: TerminalWindowService,
    private val backend: GlfwBackend,
    initialHandle: Long,
    internal val contextInfo: ContextInfo,
    initialFramebufferSize: FramebufferSize,
    initialContentScale: WindowContentScale,
    internal val matrix: TerminalMatrix,
    private val font: BitmapFont,
    private val cellWidth: Int,
    private val cellHeight: Int,
    private val cellScale: Int,
) : FrameHost {
    /** Latest service-thread framebuffer snapshot. */
    @Volatile
    internal var framebufferSize: FramebufferSize = initialFramebufferSize

    /** Latest service-thread content-scale snapshot. */
    @Volatile
    internal var contentScale: WindowContentScale = initialContentScale

    /** Framebuffer and scale last applied by the frame path. Owner-thread state. */
    private var appliedFramebufferSize: FramebufferSize
    private var appliedContentScale: WindowContentScale

    /** A zero-sized callback was observed since the last applied resize. */
    @Volatile
    private var minimizedSinceApply: Boolean =
        initialFramebufferSize.width <= 0 || initialFramebufferSize.height <= 0

    /** Bounded service-to-owner event queue for this window. */
    internal val inputQueue: InputQueue = InputQueue()

    @Volatile
    private var deviceState: DeviceState = DeviceState.EMPTY

    @Volatile
    private var cursorX: Double = 0.0

    @Volatile
    private var cursorY: Double = 0.0

    private var callbacks: GlfwCallbacks? = null

    /** Service-thread-owned custom cursor; null means a standard shape or default. */
    private var customCursor: Long = MemoryUtil.NULL

    /** Service-thread-owned cursor mode and visibility state. */
    private var cursorMode: CursorMode = CursorMode.NORMAL
    private var cursorVisible: Boolean = true

    private val renderer: Renderer
    private val scheduler: FrameScheduler

    @Volatile
    private var lastStats: RenderStats =
        RenderStats(
            columns = matrix.columns,
            rows = matrix.rows,
            uploads = 0,
            uploadBytes = 0L,
            drawCalls = 0,
            cellsDrawn = 0,
            droppedEvents = 0,
        )

    /** Continuous owner-thread repaint control. */
    @Volatile
    override var forceRepaint: Boolean = false

    private val lock: Any = Any()
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val repaintRequested: AtomicBoolean = AtomicBoolean(false)
    private val closedLatch: CountDownLatch = CountDownLatch(1)
    private val serviceThread: Thread = Thread.currentThread()

    @Volatile
    private var raw: Long = initialHandle

    @Volatile
    private var owner: Thread? = Thread.currentThread()

    @Volatile
    internal var shouldClose: Boolean = false
        private set

    init {
        val initialMetrics =
            ResizeCalculator.compute(
                font = font,
                scale = cellScale,
                contentScaleX = initialContentScale.x,
                contentScaleY = initialContentScale.y,
                fbW = initialFramebufferSize.width,
                fbH = initialFramebufferSize.height,
            )
        appliedFramebufferSize = initialFramebufferSize
        appliedContentScale = initialContentScale
        renderer =
            Renderer(
                matrix = matrix,
                atlas = AtlasBaker.bake(font, contextInfo.maxTextureSize),
                framebufferWidth = initialFramebufferSize.width,
                framebufferHeight = initialFramebufferSize.height,
                cellWidthPx = initialMetrics.cellPxW,
                cellHeightPx = initialMetrics.cellPxH,
                atlasCellWidth = cellWidth,
                atlasCellHeight = cellHeight,
            ) { backend.swapBuffers(raw) }
        scheduler = FrameScheduler(matrix, renderer, this)
        refreshDeviceState()
        callbacks = GlfwCallbacks(this, backend).also { it.install() }
    }

    internal val rawHandle: Long
        get() = raw

    internal val isClosed: Boolean
        get() = closed.get()

    override val droppedEvents: Int
        get() = inputQueue.droppedEvents

    override val isMinimized: Boolean
        get() = framebufferSize.width <= 0 || framebufferSize.height <= 0

    internal fun setTitle(title: String) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    backend.setWindowTitle(handle, title)
                }
            }
        }
    }

    internal fun setVisible(visible: Boolean) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    if (visible) backend.showWindow(handle) else backend.hideWindow(handle)
                }
            }
        }
    }

    internal fun setResizable(resizable: Boolean) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    backend.setWindowResizable(handle, resizable)
                }
            }
        }
    }

    internal fun setCursor(shape: CursorShape) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    val next = CursorHandle.standard(shape, backend)
                    val previous = customCursor
                    backend.setCursor(handle, next)
                    customCursor = MemoryUtil.NULL
                    CursorHandle.destroy(previous, backend)
                }
            }
        }
    }

    internal fun setCursor(image: CursorImage) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    val next = CursorHandle.create(image, backend)
                    val previous = customCursor
                    try {
                        backend.setCursor(handle, next)
                    } catch (failure: Throwable) {
                        CursorHandle.destroy(next, backend)
                        throw failure
                    }
                    customCursor = next
                    CursorHandle.destroy(previous, backend)
                }
            }
        }
    }

    internal fun setCursorVisible(visible: Boolean) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    cursorVisible = visible
                    applyCursorMode(handle)
                }
            }
        }
    }

    internal fun setCursorMode(mode: CursorMode) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    cursorMode = mode
                    applyCursorMode(handle)
                }
            }
        }
    }

    internal fun setIcon(icons: Array<out WindowIcon>) {
        checkOpen()
        val copy = icons.toList()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    backend.setWindowIcon(handle, copy)
                }
            }
        }
    }

    private fun applyCursorMode(handle: Long) {
        val value =
            when {
                cursorMode == CursorMode.DISABLED -> GLFW.GLFW_CURSOR_DISABLED
                !cursorVisible -> GLFW.GLFW_CURSOR_HIDDEN
                else -> GLFW.GLFW_CURSOR_NORMAL
            }
        backend.setInputMode(handle, GLFW.GLFW_CURSOR, value)
    }

    internal fun setCloseRequested(requested: Boolean) {
        checkOpen()
        service.enqueueStateOp {
            if (!closed.get()) {
                val handle = raw
                if (handle != MemoryUtil.NULL) {
                    backend.setWindowShouldClose(handle, requested)
                    shouldClose = requested
                }
            }
        }
    }

    internal fun refreshShouldClose() {
        if (!closed.get()) {
            val handle = raw
            if (handle != MemoryUtil.NULL) {
                shouldClose = backend.windowShouldClose(handle)
            }
        }
    }

    /** Refreshes direct key, mouse-button, and cursor state after a poll. */
    internal fun refreshDeviceState() {
        if (closed.get()) {
            return
        }
        val handle = raw
        if (handle == MemoryUtil.NULL) {
            return
        }
        val sampled = backend.cursorPosition(handle)
        val cursor =
            if (sampled.x.isFinite() && sampled.y.isFinite()) {
                sampled
            } else {
                CursorSample(cursorX, cursorY)
            }
        cursorX = cursor.x
        cursorY = cursor.y
        val current = deviceState
        if (current.pressedKeys.isEmpty() &&
            current.pressedMouseButtons.isEmpty() &&
            current.cursor.screenX == cursor.x &&
            current.cursor.screenY == cursor.y &&
            noInputIsPressed(handle)
        ) {
            return
        }
        deviceState =
            sampleDeviceState(
                keyState = { backend.getKey(handle, it) },
                mouseButtonState = { backend.getMouseButton(handle, it) },
                cursor = cursor,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                scale = cellScale,
            )
    }

    private fun noInputIsPressed(handle: Long): Boolean {
        val keys = Key.entries
        var index = 0
        while (index < keys.size) {
            val key = keys[index]
            if (key != Key.UNKNOWN && backend.getKey(handle, key.glfwCode) == GLFW.GLFW_PRESS) {
                return false
            }
            index++
        }
        val buttons = MouseButton.entries
        index = 0
        while (index < buttons.size) {
            if (backend.getMouseButton(handle, buttons[index].glfwCode) == GLFW.GLFW_PRESS) {
                return false
            }
            index++
        }
        return true
    }

    internal fun isKeyPressed(key: Key): Boolean {
        checkOpen()
        return deviceState.isKeyPressed(key)
    }

    internal fun isMouseButtonPressed(button: MouseButton): Boolean {
        checkOpen()
        return deviceState.isMouseButtonPressed(button)
    }

    internal fun cursorPositionSnapshot(): CursorPosition {
        checkOpen()
        return deviceState.cursor
    }

    internal fun addInputListener(listener: InputListener) {
        checkOpen()
        inputQueue.addListener(listener)
    }

    internal fun removeInputListener(listener: InputListener) {
        checkOpen()
        inputQueue.removeListener(listener)
    }

    /** Dispatches pending events; legal only on the thread that owns the context. */
    override fun drainEvents() {
        checkOpen()
        val current = Thread.currentThread()
        val expected = owner
        check(expected != null && expected === current) {
            "drainEvents must run on the window owner thread '${expected?.name ?: "none"}'; " +
                "current thread is '${current.name}'"
        }
        inputQueue.drain()
    }

    internal fun onKey(
        key: Int,
        scancode: Int,
        action: Int,
        mods: Int,
    ) {
        if (closed.get()) {
            return
        }
        val mappedKey = Key.fromGlfw(key)
        val mappedAction = KeyAction.fromGlfw(action) ?: return
        inputQueue.offer(
            KeyEvent(
                key = mappedKey,
                scancode = scancode,
                action = mappedAction,
                modifiers = Modifiers.fromGlfw(mods),
                repeat = mappedAction == KeyAction.REPEAT,
            ),
        )
    }

    internal fun onChar(codePoint: Int) {
        if (closed.get()) {
            return
        }
        val valid = codePoint in 0..MAX_CODE_POINT && codePoint !in SURROGATE_RANGE
        if (valid) {
            inputQueue.offer(CharEvent(codePoint))
        }
    }

    internal fun onCursorPos(
        x: Double,
        y: Double,
    ) {
        if (closed.get() || !x.isFinite() || !y.isFinite()) {
            return
        }
        cursorX = x
        cursorY = y
        val cursor = cursorPosition(CursorSample(x, y), cellWidth, cellHeight, cellScale)
        inputQueue.offer(
            MouseMoveEvent(
                pixelX = (x * contentScale.x).roundToInt(),
                pixelY = (y * contentScale.y).roundToInt(),
                column = cursor.column,
                row = cursor.row,
            ),
        )
    }

    internal fun onCursorEnter(entered: Boolean) {
        if (closed.get()) {
            return
        }
        inputQueue.offer(CursorEnterEvent(entered))
    }

    internal fun onMouseButton(
        button: Int,
        action: Int,
        mods: Int,
    ) {
        if (closed.get()) {
            return
        }
        val mappedButton = MouseButton.fromGlfw(button) ?: return
        val mappedAction = KeyAction.fromGlfw(action) ?: return
        val cursor = cursorPosition(CursorSample(cursorX, cursorY), cellWidth, cellHeight, cellScale)
        inputQueue.offer(
            MouseButtonEvent(
                button = mappedButton,
                action = mappedAction,
                modifiers = Modifiers.fromGlfw(mods),
                column = cursor.column,
                row = cursor.row,
            ),
        )
    }

    internal fun onScroll(
        xOffset: Double,
        yOffset: Double,
    ) {
        if (closed.get() || !xOffset.isFinite() || !yOffset.isFinite()) {
            return
        }
        inputQueue.offer(ScrollEvent(xOffset, yOffset))
    }

    internal fun onFramebufferSize(
        width: Int,
        height: Int,
    ) {
        if (closed.get() || width < 0 || height < 0) {
            return
        }
        if (width == 0 || height == 0) {
            minimizedSinceApply = true
        }
        framebufferSize = FramebufferSize(width, height)
        inputQueue.offer(ResizeEvent(width, height))
    }

    internal fun onContentScale(
        x: Float,
        y: Float,
    ) {
        if (closed.get() || x <= 0.0f || y <= 0.0f || !x.isFinite() || !y.isFinite()) {
            return
        }
        contentScale = WindowContentScale(x, y)
        inputQueue.offer(ContentScaleEvent(x, y))
    }

    internal fun onFocus(focused: Boolean) {
        if (closed.get()) {
            return
        }
        inputQueue.offer(FocusEvent(focused))
    }

    internal fun onCloseRequested() {
        if (closed.get()) {
            return
        }
        shouldClose = true
        inputQueue.offer(CloseRequestedEvent)
    }

    internal fun requestRepaint() {
        checkOpen()
        repaintRequested.set(true)
    }

    override fun consumeRepaintRequest(): Boolean = repaintRequested.getAndSet(false)

    override fun restoreRepaintRequest() {
        repaintRequested.set(true)
    }

    override fun applyPendingResize(): Boolean {
        val pendingFramebuffer = framebufferSize
        if (pendingFramebuffer.width <= 0 || pendingFramebuffer.height <= 0) {
            return false
        }

        val pendingScale = contentScale
        if (!minimizedSinceApply &&
            pendingFramebuffer == appliedFramebufferSize &&
            pendingScale == appliedContentScale
        ) {
            return false
        }

        val metrics =
            ResizeCalculator.compute(
                font = font,
                scale = cellScale,
                contentScaleX = pendingScale.x,
                contentScaleY = pendingScale.y,
                fbW = pendingFramebuffer.width,
                fbH = pendingFramebuffer.height,
            )
        matrix.resize(metrics.columns, metrics.rows, ResizePolicy.PRESERVE_TOP_LEFT)
        matrix.markDirty()
        renderer.resize(
            framebufferWidth = pendingFramebuffer.width,
            framebufferHeight = pendingFramebuffer.height,
            cellWidthPx = metrics.cellPxW,
            cellHeightPx = metrics.cellPxH,
        )

        appliedFramebufferSize = pendingFramebuffer
        appliedContentScale = pendingScale
        minimizedSinceApply = false
        return true
    }

    /** Runs one frame opportunity and records its statistics. */
    internal fun frame() {
        checkOpen()
        if (Thread.currentThread() === serviceThread) {
            service.pump()
        }
        lastStats = scheduler.tick()
    }

    /** Returns the statistics from the most recent frame opportunity. */
    internal fun renderStats(): RenderStats = lastStats

    internal fun acquireContext() {
        synchronized(lock) {
            checkOpen()
            val current = Thread.currentThread()
            check(owner == null) {
                "context is already acquired by '${owner?.name}'; release it before acquiring"
            }
            backend.makeContextCurrent(raw)
            try {
                GL.createCapabilities()
            } catch (failure: Throwable) {
                backend.makeContextCurrent(MemoryUtil.NULL)
                throw failure
            }
            owner = current
        }
    }

    internal fun releaseContext() {
        synchronized(lock) {
            checkOpen()
            val current = Thread.currentThread()
            check(owner === current) {
                "releaseContext must run on the owner thread '${owner?.name}', current thread is '${current.name}'"
            }
            backend.makeContextCurrent(MemoryUtil.NULL)
            GL.setCapabilities(null)
            owner = null
        }
    }

    internal fun close() {
        val current = Thread.currentThread()
        synchronized(lock) {
            if (closed.get()) {
                return
            }
            val currentOwner = owner
            check(currentOwner == null || currentOwner === current) {
                "close must run on the owner thread '${currentOwner?.name}' or after releaseContext(); " +
                    "current thread is '${current.name}'"
            }
            if (currentOwner === current) {
                renderer.close()
                backend.makeContextCurrent(MemoryUtil.NULL)
                GL.setCapabilities(null)
                owner = null
            }
            closed.set(true)
            try {
                service.enqueueDestroyOp { destroy() }
            } catch (failure: Throwable) {
                closed.set(false)
                throw failure
            }
        }
    }

    internal fun awaitClosed() {
        if (Thread.currentThread() === serviceThread) {
            return
        }
        closedLatch.await()
    }

    private fun destroy() {
        try {
            val handle = raw
            if (handle != MemoryUtil.NULL) {
                try {
                    if (owner == null) {
                        backend.makeContextCurrent(handle)
                        try {
                            GL.createCapabilities()
                            renderer.close()
                        } finally {
                            backend.makeContextCurrent(MemoryUtil.NULL)
                            GL.setCapabilities(null)
                        }
                    }
                    callbacks?.uninstall()
                } finally {
                    callbacks = null
                    inputQueue.clear()
                }
                val cursor = customCursor
                customCursor = MemoryUtil.NULL
                try {
                    CursorHandle.destroy(cursor, backend)
                } finally {
                    backend.destroyWindow(handle)
                    raw = MemoryUtil.NULL
                    service.windowClosed(handle)
                }
            }
        } finally {
            closedLatch.countDown()
        }
    }

    private fun checkOpen() {
        check(!closed.get()) { "TerminalWindow is closed" }
    }

    internal companion object {
        internal fun create(
            service: TerminalWindowService,
            backend: GlfwBackend,
            config: TerminalWindowConfig,
        ): WindowHandle {
            val screenWidth = config.columns.toLong() * config.font.cellWidth * config.scale
            val screenHeight = config.rows.toLong() * config.font.cellHeight * config.scale
            require(screenWidth <= Int.MAX_VALUE.toLong()) {
                "requested screen width $screenWidth exceeds Int range"
            }
            require(screenHeight <= Int.MAX_VALUE.toLong()) {
                "requested screen height $screenHeight exceeds Int range"
            }

            val requested = requestedContextVersion()
            var raw = MemoryUtil.NULL
            backend.defaultWindowHints()
            try {
                backend.windowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, requested.first)
                backend.windowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, requested.second)
                backend.windowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
                backend.windowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
                backend.windowHint(GLFW.GLFW_RESIZABLE, if (config.resizable) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
                backend.windowHint(GLFW.GLFW_VISIBLE, if (config.visible) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
                backend.windowHint(GLFW.GLFW_SCALE_FRAMEBUFFER, GLFW.GLFW_TRUE)

                raw = backend.createWindow(screenWidth.toInt(), screenHeight.toInt(), config.title)
                if (raw == MemoryUtil.NULL) {
                    backend.takeError()?.let { throw it.asException() }
                    throw GlException(
                        "GLFW could not create a ${requested.first}.${requested.second} core window " +
                            "for ${screenWidth}x$screenHeight screen pixels",
                    )
                }

                backend.makeContextCurrent(raw)
                backend.swapInterval(if (config.vsync) 1 else 0)
                GL.createCapabilities()

                val versionString = GL11.glGetString(GL11.GL_VERSION).orEmpty()
                val renderer = GL11.glGetString(GL11.GL_RENDERER).orEmpty()
                val vendor = GL11.glGetString(GL11.GL_VENDOR).orEmpty()
                val shadingLanguageVersion = GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION).orEmpty()
                val (actualMajor, actualMinor) = parseVersion(versionString)
                if (actualMajor < requested.first ||
                    (actualMajor == requested.first && actualMinor < requested.second)
                ) {
                    throw GlException(
                        "OpenGL ${requested.first}.${requested.second} core or newer is required; " +
                            "GLFW created $actualMajor.$actualMinor ($versionString), renderer '$renderer', " +
                            "vendor '$vendor'. ${platformHint()}",
                    )
                }

                val maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)
                if (maxTextureSize < ContextInfo.MIN_MAX_TEXTURE_SIZE) {
                    throw GlException(
                        "OpenGL ${ContextInfo.MIN_OPENGL_MAJOR}.${ContextInfo.MIN_OPENGL_MINOR} core requires " +
                            "GL_MAX_TEXTURE_SIZE >= " +
                            "${ContextInfo.MIN_MAX_TEXTURE_SIZE}; the driver reported $maxTextureSize",
                    )
                }

                val framebufferSize = backend.framebufferSize(raw)
                val contentScale = backend.contentScale(raw)
                val metrics =
                    ResizeCalculator.compute(
                        font = config.font,
                        scale = config.scale,
                        contentScaleX = contentScale.x,
                        contentScaleY = contentScale.y,
                        fbW = framebufferSize.width,
                        fbH = framebufferSize.height,
                    )
                val matrix =
                    TerminalMatrix(
                        metrics.columns,
                        metrics.rows,
                        config.defaultForeground,
                        config.defaultBackground,
                    )
                val contextInfo =
                    ContextInfo(
                        requestedMajor = requested.first,
                        requestedMinor = requested.second,
                        actualMajor = actualMajor,
                        actualMinor = actualMinor,
                        versionString = versionString,
                        renderer = renderer,
                        vendor = vendor,
                        shadingLanguageVersion = shadingLanguageVersion,
                        maxTextureSize = maxTextureSize,
                    )
                return WindowHandle(
                    service = service,
                    backend = backend,
                    initialHandle = raw,
                    contextInfo = contextInfo,
                    initialFramebufferSize = framebufferSize,
                    initialContentScale = contentScale,
                    matrix = matrix,
                    font = config.font,
                    cellWidth = config.font.cellWidth,
                    cellHeight = config.font.cellHeight,
                    cellScale = config.scale,
                )
            } catch (failure: Throwable) {
                if (raw != MemoryUtil.NULL) {
                    try {
                        backend.makeContextCurrent(MemoryUtil.NULL)
                        GL.setCapabilities(null)
                        backend.destroyWindow(raw)
                    } catch (cleanupFailure: Throwable) {
                        failure.addSuppressed(cleanupFailure)
                    }
                }
                throw failure
            } finally {
                backend.defaultWindowHints()
            }
        }

        private fun requestedContextVersion(): Pair<Int, Int> {
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            return if (osName.contains("mac") || osName.contains("darwin")) {
                MACOS_OPENGL_MAJOR to MACOS_OPENGL_MINOR
            } else {
                ContextInfo.MIN_OPENGL_MAJOR to ContextInfo.MIN_OPENGL_MINOR
            }
        }

        private fun platformHint(): String =
            if (requestedContextVersion().first == MACOS_OPENGL_MAJOR) {
                "macOS provides 4.1 core, not 3.3 core."
            } else {
                "The driver must offer an OpenGL ${ContextInfo.MIN_OPENGL_MAJOR}.${ContextInfo.MIN_OPENGL_MINOR} core context."
            }

        private const val MACOS_OPENGL_MAJOR: Int = 4
        private const val MACOS_OPENGL_MINOR: Int = 1

        private const val MAX_CODE_POINT: Int = 0x10FFFF
        private val SURROGATE_RANGE: IntRange = 0xD800..0xDFFF

        private fun parseVersion(version: String): Pair<Int, Int> {
            val match = VERSION_PATTERN.find(version) ?: return 0 to 0
            return match.groupValues[1].toIntOrNull().orZero() to match.groupValues[2].toIntOrNull().orZero()
        }

        private fun Int?.orZero(): Int = this ?: 0

        private val VERSION_PATTERN: Regex = Regex("""(\d+)\.(\d+)""")
    }
}
