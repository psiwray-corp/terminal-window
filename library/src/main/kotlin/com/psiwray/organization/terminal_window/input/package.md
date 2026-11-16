# Package com.psiwray.organization.terminal_window.input

Immutable input event values, the `InputListener` callback surface, and the
key, mouse-button, modifier, and cursor-coordinate types delivered to and read
by applications.

GLFW callbacks run on the service thread. Event delivery and listener calls
run on the window owner thread. Direct key, mouse-button, and cursor reads use
the latest service-thread snapshot and are safe from any thread; they never
query GLFW.