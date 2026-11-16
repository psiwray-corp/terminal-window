package com.psiwray.organization.terminal_window.examples

import com.psiwray.organization.terminal_window.Colors
import com.psiwray.organization.terminal_window.TerminalWindowService
import com.psiwray.organization.terminal_window.input.CharEvent
import com.psiwray.organization.terminal_window.input.Key
import com.psiwray.organization.terminal_window.input.KeyEvent
import com.psiwray.organization.terminal_window.input.Modifiers
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path

/** The font copied into the examples distribution and used by default. */
internal const val DEFAULT_FONT_PATH: String = "examples/fonts/ascii-8x16.twf"

/** One foreground/background pair used by the demos. */
internal data class ExampleTheme(
    val name: String,
    val foreground: Int,
    val background: Int,
    val accent: Int,
    val secondary: Int,
)

/** High-contrast palettes that remain readable with the opaque shader. */
internal val EXAMPLE_THEMES: List<ExampleTheme> =
    listOf(
        ExampleTheme(
            name = "amber",
            foreground = Colors.rgb(255, 183, 77),
            background = Colors.rgb(18, 12, 8),
            accent = Colors.rgb(255, 241, 118),
            secondary = Colors.rgb(255, 138, 101),
        ),
        ExampleTheme(
            name = "mint",
            foreground = Colors.rgb(128, 222, 234),
            background = Colors.rgb(8, 18, 20),
            accent = Colors.rgb(178, 255, 89),
            secondary = Colors.rgb(0, 172, 193),
        ),
        ExampleTheme(
            name = "violet",
            foreground = Colors.rgb(206, 147, 216),
            background = Colors.rgb(16, 8, 20),
            accent = Colors.rgb(244, 143, 177),
            secondary = Colors.rgb(121, 134, 203),
        ),
        ExampleTheme(
            name = "paper",
            foreground = Colors.rgb(38, 50, 56),
            background = Colors.rgb(245, 245, 245),
            accent = Colors.rgb(183, 28, 28),
            secondary = Colors.rgb(21, 101, 192),
        ),
    )

/**
 * Initializes the service, preferring X11 when an X display is available.
 *
 * The library's local debug profile targets X11 for RenderDoc; the examples
 * follow that default while still allowing a Wayland-only host to fall back to
 * GLFW's native platform choice.
 */
internal fun initializeExampleService(): TerminalWindowService {
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("linux") && !System.getenv("DISPLAY").isNullOrBlank()) {
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11)
    }
    return TerminalWindowService.initialize()
}

/** Parses the optional auto-close duration used by headless verification. */
internal fun autoCloseMillis(args: Array<String>): Long? {
    val raw = args.firstOrNull { it.startsWith("--auto-close-ms=") } ?: return null
    val value = raw.substringAfter('=')
    return value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("--auto-close-ms must be a positive integer: $raw")
}

/**
 * Resolves the first positional argument, or the checked-in ASCII font.
 *
 * The Gradle tasks set the root project as the working directory, so the
 * documented default path works from `./gradlew :examples:hello`.
 */
internal fun resolveFontPath(args: Array<String>): Path {
    val candidate = args.firstOrNull { !it.startsWith("--") }
    val candidates =
        if (candidate == null) {
            listOf(Path.of(DEFAULT_FONT_PATH), Path.of("fonts/ascii-8x16.twf"))
        } else {
            listOf(Path.of(candidate))
        }
    return candidates.firstOrNull { Files.isRegularFile(it) }
        ?: throw IllegalStateException(
            "font not found: ${candidates.joinToString(" or ") { it.toString() }}",
        )
}

/** Returns the platform-specific quit chord label used in titles and banners. */
internal fun quitChordLabel(): String =
    if (System.getProperty("os.name").lowercase().contains("mac")) "Cmd+Q" else "Ctrl+Q"

/** Both chords are accepted on every platform. */
internal fun KeyEvent.isQuitChord(): Boolean =
    key == Key.Q && (modifiers.has(Modifiers.CONTROL) || modifiers.has(Modifiers.SUPER))

/** True for text that should be inserted into a cell. */
internal fun CharEvent.isPrintable(): Boolean =
    codePoint >= 0x20 && codePoint != 0x7F && !Character.isISOControl(codePoint)

/** True once the optional example deadline has elapsed. */
internal fun deadlineReached(
    startNanos: Long,
    autoCloseMillis: Long?,
): Boolean = autoCloseMillis != null && System.nanoTime() - startNanos >= autoCloseMillis * 1_000_000L
