package com.psiwray.organization.terminal_window.examples

import com.psiwray.organization.terminal_window.Colors
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.TerminalWindowService
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.Key
import com.psiwray.organization.terminal_window.input.KeyEvent
import java.nio.file.Path

/**
 * The quickstart printed in the README. Keep this file and that block in sync.
 *
 * Run from the repository root with a visible desktop; `Q` requests a close.
 */
public fun main() {
    TerminalWindowService.initialize().use { service ->
        service
            .openWindow(
                TerminalWindowConfig(font = BitmapFont.parse(Path.of("examples/fonts/ascii-8x16.twf"))),
            ).use { window ->
                window.matrix.write(0, 0, "Hello, terminal-window!", Colors.WHITE, Colors.BLACK)
                window.addInputListener(
                    object : InputListener {
                        override fun onKey(event: KeyEvent) {
                            if (event.key == Key.Q) window.requestClose()
                        }
                    },
                )
                while (!window.shouldClose) window.frame()
            }
    }
}
