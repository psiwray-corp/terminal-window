# ADR-0004: Threading model

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** project owner
- **Supersedes:** none

## Context

GLFW imposes platform thread rules that the library cannot abstract away.
Quoted verbatim from the GLFW reference documentation (retrieved 2026-11-15;
the binding version follows LWJGL 3.4.3):

> Most GLFW functions must only be called from the main thread (the thread that
> calls main), but some may be called from any thread once the library has been
> initialized. Before initialization the whole library is thread-unsafe.

> Initialization, termination, event processing and the creation and destruction
> of windows, cursors and OpenGL and OpenGL ES contexts are all restricted to the
> main thread due to limitations of one or several platforms.

> Because event processing must be performed on the main thread, all callbacks
> except for the error callback will only be called on that thread. The error
> callback may be called on any thread, as any GLFW function may generate errors.

Per-function reference, same source:

| Function | Documented thread rule |
|---|---|
| `glfwInit` | "This function must only be called from the main thread." |
| `glfwTerminate` | "This function must only be called from the main thread." |
| `glfwCreateWindow` | "This function must only be called from the main thread." |
| `glfwDestroyWindow` | "This function must only be called from the main thread." |
| `glfwPollEvents` | "This function must only be called from the main thread." |
| `glfwMakeContextCurrent` | "This function may be called from any thread." |
| `glfwSwapBuffers` | "This function may be called from any thread." |
| `glfwSwapInterval` | "This function may be called from any thread." |
| `glfwGetFramebufferSize` | "This function must only be called from the main thread." |

Three consequences are easy to miss and are therefore fixed here:

1. `glfwGetFramebufferSize` is main-thread-only. DPI and resize queries cannot
   be made from the render thread; they must be answered by the service thread.
2. `glfwInit` is process-global. "Per-thread initialization" does not exist, and
   calling `glfwInit` more than once is not a way to relax the thread rule; the
   second call is a no-op only after the first succeeded.
3. The "main thread" is the thread that calls `main`. Using a dedicated
   non-main thread for GLFW may happen to work on X11 or Win32, but it is
   outside the documented contract and is not a supported configuration.

## Decision

We will use two roles, with one service thread per process and one owner thread
per window:

| Role | Owns | Thread |
|---|---|---|
| **Service thread** | `glfwInit`, `glfwTerminate`, window creation/destruction, `glfwPollEvents`, the window registry, one event queue per window, window state reads and writes (title, size, size limits, visibility, attributes), framebuffer-size queries, and every callback except the error callback | One thread for the process: the process main thread. It is the thread that calls `glfwInit`. |
| **Owner thread** | One window's OpenGL context while current: `glfwMakeContextCurrent`, all GL calls, buffer uploads, draw calls, `glfwSwapBuffers`, `glfwSwapInterval`, and draining that window's event queue | One thread per window; it drives only that window's frames. |

Rules:

1. **One GLFW initialization per process.** The service thread calls
   `glfwInit` before the first window and `glfwTerminate` after the last window
   is destroyed, on the same thread. There is no per-thread `glfwInit`.
2. **Service thread is the process main thread.** This is enforced by the
   platform on macOS and required by GLFW's documented contract everywhere
   else; the library does not rely on undocumented tolerance on X11 or Win32.
   Creating a window from a non-main thread is an error, reported as an
   exception, not undefined behavior.
3. **Context ownership is explicit.** A context is current on at most one
   thread. Transfer is `releaseContext()` on the current owner followed by
   `acquireContext()` on the next owner; both are explicit calls, never
   inferred. `glfwMakeContextCurrent` is any-thread, so this transfer is legal.
4. **Window state changes are messages.** Every setter that the GLFW docs
   restrict to the main thread is queued to the service thread, tagged with the
   target window, and applied before the next event poll. The caller does not
   execute GLFW code. These service-bound commands use a separate queue from
   the owner-bound event queue; the two directions are never mixed.
5. **Events cross per-window queues.** Input callbacks fire on the service
   thread and identify their window. The service thread routes each event to
   that window's own queue. The window's owner thread drains only its own queue
   at a defined point in its frame; no owner thread can observe another window's
   events. Event objects are immutable after enqueue.
6. **Resize and DPI data come from the service thread.** The service thread
   reads `glfwGetFramebufferSize` and window size per window, then publishes an
   immutable snapshot into that window's queue. The owner thread never queries
   GLFW for these values.
7. **No global hooks the caller did not ask for.** The library does not install
   a process-wide GLFW error callback. Errors are retrieved with `glfwGetError`
   on the thread that made the call, as documented.
8. **No `System.exit`, no thread interruption as control flow.** A failed
   initialization unwinds with an exception; threads are stopped by their own
   command loop.
9. **The renderer never calls GLFW window functions.** It receives plain values
   (framebuffer width/height, cell metrics) and issues only GL calls.
10. **One window registry.** The service thread owns the mapping from GLFW
    window handle to per-window state, owner thread, and queue. Callbacks
    resolve their window through this registry, so an event can never be
    delivered to another window's queue. Registry mutation happens only on the
    service thread.
11. **Bounded queues, no silent loss.** Each queue is bounded. State-like events
    (cursor position, framebuffer size) may coalesce; discrete events (key,
    text, button, scroll, close) are never dropped silently. Overflow is
    surfaced to the owning thread and counted. Capacity and coalescing rules
    belong to section 13.
12. **Lifecycle is ordered.** Closing is a two-phase handshake: the service
    thread marks the window closing and enqueues the close event; the owner
    thread stops submitting frames, releases the context, and acknowledges.
    Only then does the service thread remove the registry entry and call
    `glfwDestroyWindow`. Routing is removed before destruction, so no callback
    can enqueue into a destroyed window's queue. Section 12 owns the handshake.
13. **A blocked service loop is woken.** If the service thread waits in
    `glfwWaitEvents` or `glfwWaitEventsTimeout`, a queued state change or
    lifecycle request wakes it with `glfwPostEmptyEvent`, which GLFW documents
    as callable from any thread. A poll loop needs no wake.

The service thread runs one event loop for all windows on the process main
thread; each owner thread runs frame production for its own window. A blocking
lifecycle call (open, close) waits for the service thread to acknowledge
completion. The wait is bounded; the bound is owned by the section that
implements lifecycle (section 12), not by this ADR.

## Consequences

### Positive

- The GLFW contract is satisfied by construction: every restricted call has
  exactly one legal thread, visible in the code as one class.
- Rendering is decoupled from event polling; a slow frame does not violate the
  event-loop thread rule.
- The same design works on macOS, Windows, X11, and Wayland without a
  platform-specific threading branch inside the renderer.
- DPI and resize races are eliminated because only one thread reads the values
  and it publishes immutable snapshots per window.
- A slow or blocked window thread cannot delay another window's events or
  frames; each window has its own queue and owner thread.

### Negative

- Cross-thread handoffs cost a queue operation per input event and per frame.
- Users must not call GLFW themselves on the owner thread; the documentation
  must say so loudly.
- The library cannot be initialized from an arbitrary thread: callers must
  open windows on the process main thread. On macOS the launcher must also pass
  `-XstartOnFirstThread` (ADR-0009).
- Blocking lifecycle calls can deadlock if a user callback blocks the service
  thread; callbacks must be non-blocking, and a blocking callback stalls every
  window.

### Neutral

- `glfwSwapBuffers` and `glfwSwapInterval` are any-thread and are called from
  the owner thread; this is permitted, not an exception to the model.
- The error callback is the only callback that may fire on any thread; because
  the library installs none, no callback is delivered on an unexpected thread.
- Multiple windows share one `glfwInit`/`glfwTerminate` lifetime and one polling
  thread; each window still has exactly one owner thread and one queue. Window
  creation and destruction remain serialized on the service thread.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Create and poll on the caller's thread, render on the same thread | Simplest, but the caller cannot block in event polling while rendering, and a user callback would run inside the GL frame. |
| Do everything on one dedicated thread | On macOS GLFW must run on the process main thread; a dedicated thread cannot create windows there. |
| Poll events from the owner thread | Violates the documented main-thread rule and is undefined on macOS. |
| Query framebuffer size from the owner thread | Violates the documented main-thread rule. |
| `glfwInit` per window | GLFW initialization is process-global; repeated calls do not create independent state and the main-thread rule still applies. |
| Rely on GLFW's error callback | Installs a global hook the caller did not request, and the callback can fire on any thread without a defined handoff. |
| Implicit context transfer on frame boundaries | Hides ownership bugs; two threads can believe they own the context. |
| One global event queue drained by all window threads | Cross-window head-of-line blocking, risk of delivering an event to the wrong window, and no per-window backpressure. |

## References

- GLFW introduction, "Thread safety": <https://www.glfw.org/docs/latest/intro_guide.html#thread_safety>
- GLFW window reference: <https://www.glfw.org/docs/latest/group__window.html>
- GLFW context reference: <https://www.glfw.org/docs/latest/group__context.html>
- `docs/architecture.md` for the thread map and frame sequence.
