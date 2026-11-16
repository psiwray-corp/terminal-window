# Package com.psiwray.organization.terminal_window

Public entry points for the terminal window: the window and service handles,
immutable configuration and diagnostics, the cell matrix, ARGB color helpers,
and the checked exception types.

The package is an automatic JPMS module named
`com.psiwray.organization.terminal_window`. Automatic modules export every
package, including `.internal`; Kotlin `internal` is not a JPMS boundary.

Typical lifecycle: initialize a `TerminalWindowService` on the process main
thread, open a `TerminalWindow`, pump or run the service loop on that thread,
and render with `TerminalWindow.frame()` on the context-owning thread.