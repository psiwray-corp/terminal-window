# 11 — GLFW service and threading contract

**Phase:** runtime · **Depends on:** 03 · **Blocks:** 12

## Goal

One process-global GLFW owner (the service thread) plus a documented protocol that lets each window be rendered on a different owner thread, without violating GLFW.

## Verified GLFW rules (do not soften these)

- Main thread only: `glfwInit`, `glfwTerminate`, `glfwCreateWindow`, `glfwDestroyWindow`, `glfwPollEvents`/`WaitEvents`, and nearly every window-state setter/getter (title, size, visibility, should-close, input mode, framebuffer size query, content scale).
- Any thread: `glfwMakeContextCurrent`, `glfwGetCurrentContext`, `glfwSwapBuffers`, `glfwSwapInterval`, `glfwExtensionSupported`.
- One context current on at most one thread at a time; detach before moving.
- Event processing and window destruction are not reentrant (never call them from a callback).

## Deliverables

- `docs/threading.md`: the rules above, the two usage modes below, and a do/don't table.
- `.../TerminalWindowService.kt`
- `.../internal/ServiceThread.kt` (thread identity + `assertServiceThread()`)
- `.../GlfwException.kt`
- `library/src/test/kotlin/.../TerminalWindowServiceTest.kt`

## Steps

1. **Service lifecycle.**
   ```kotlin
   class TerminalWindowService private constructor(private val serviceThread: Thread) : AutoCloseable {
       companion object {
           @JvmStatic fun initialize(ownership: GlfwOwnership = GlfwOwnership.OWNED): TerminalWindowService
       }
       fun pump()
       override fun close()
   }
   ```
   `initialize` captures the current thread, calls `glfwInit`, increments a refcount. `close()` decrements; at zero calls `glfwTerminate`. A second `initialize` on another thread throws `IllegalStateException` quoting the GLFW rule.
2. **Ownership mode.** `enum class GlfwOwnership { OWNED, EXTERNAL }`: `OWNED` initializes/terminates; `EXTERNAL` assumes the app already did and never terminates. Mixing modes is an error.
3. **Error channel.** Install a GLFW error callback that copies code+description immediately (the string is transient) into a `GlfwException`; the next checked operation throws it. No logging by default.
4. **Task queues.** Service-thread-drained queues: `stateOps` (title/size/visibility/should-close), `destroyOps`, and a future `pendingCreates`. Use `ConcurrentLinkedQueue` (low frequency); `destroy` and `close` requests must never be dropped.
5. **Two usage modes.**
   - *Single-threaded:* everything on the service thread; `window.frame()` pumps events automatically.
   - *Multi-threaded:* service thread runs `service.pump()` in a loop (or `pump(Duration)`); owner threads call `window.frame()`, which does **not** pump.
6. **Ownership transfer.** `window.releaseContext()` (owner thread: make current `NULL`) then `window.acquireContext()` on the new thread (`glfwMakeContextCurrent`). Both `check(...)` the state and throw a readable message on misuse.
7. **Shutdown.** `close()` on the service thread drains destroy requests, then terminates (if `OWNED`); pending owner-thread work is the caller's responsibility (documented).
8. **Tests.** Refcount init/terminate; double close; wrong-thread initialize; error mapping; queued state op executes only on pump; destroy request survives a saturated queue; `EXTERNAL` never calls terminate.

## Acceptance

- [ ] Initializing from a non-main thread fails with a message naming GLFW's rule.
- [ ] `close()` twice is safe; a new `initialize()` after full termination works.
- [ ] A state op enqueued from a worker thread is applied only when the service thread pumps.
- [ ] `EXTERNAL` mode never calls `glfwTerminate`.
- [ ] No public API exposes `Long` window handles or GLFW types.
- [ ] `@Volatile`/`AtomicBoolean` used for the refcount; no unsynchronized check-then-act.

## Pitfalls

- `glfwInit` is idempotent but has no "already initialized by someone else" signal; `EXTERNAL` exists for that reason, not magic detection.
- Do not run `glfwPollEvents` on a background thread "temporarily"; it is undefined on macOS and racy on X11.
- Error descriptions are valid only until the callback returns; copy them.
- The service thread is not a new thread; it is the caller's thread (main by convention).
- Kotlin `synchronized(lock) {}` is fine for lifecycle transitions; never hold it while calling user code.

## Plan seed

> Plan section 11 only: implement the process-global GLFW service in Kotlin (refcount, ownership mode, error mapping, service-thread assertion) and the state/destroy queues, plus the documented threading contract. No window creation yet.