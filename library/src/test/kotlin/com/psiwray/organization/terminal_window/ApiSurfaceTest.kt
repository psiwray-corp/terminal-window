package com.psiwray.organization.terminal_window

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.valueParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Enforces the small, LWJGL-free public surface promised by the library.
 *
 * Kotlin reflection sees source visibility (including `internal`), unlike raw
 * JVM reflection, which sees internal classes as public bytecode.
 */
class ApiSurfaceTest {
    @Test
    fun `public API surface is explicit and free of LWJGL types`() {
        val discoveredNames = discoverClasses().map { it.java.name }.toSet()
        val expected = EXPECTED_PUBLIC_TYPES + EXPECTED_INTERNAL_TYPES
        assertEquals(expected, discoveredNames, "unexpected class in a public package")

        val failures = ArrayList<String>()
        for (typeName in EXPECTED_PUBLIC_TYPES.sorted()) {
            val type = load(typeName)
            val visibility = visibilityOf(type)
            if (visibility != KVisibility.PUBLIC) {
                failures += "$typeName must be public, was $visibility"
            }
            auditMembers(type, failures)
            type.companionObject?.let { auditMembers(it, failures) }
        }
        for (typeName in EXPECTED_INTERNAL_TYPES.sorted()) {
            val visibility = visibilityOf(load(typeName))
            if (visibility != KVisibility.INTERNAL) {
                failures += "$typeName must be internal, was $visibility"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun auditMembers(
        type: KClass<*>,
        failures: MutableList<String>,
    ) {
        type.constructors
            .filter { it.visibility == KVisibility.PUBLIC }
            .forEach { auditFunction(type, it, failures) }
        type.declaredMemberFunctions
            .filter { it.visibility == KVisibility.PUBLIC }
            .forEach { auditFunction(type, it, failures) }
        type.declaredMemberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .forEach { property ->
                rejectLwjgl(property.returnType, "${type.qualifiedName}.${property.name}", failures)
            }
    }

    private fun auditFunction(
        type: KClass<*>,
        function: KFunction<*>,
        failures: MutableList<String>,
    ) {
        val owner = type.qualifiedName ?: type.java.name
        val label = "$owner.${function.name}"
        rejectLwjgl(function.returnType, label, failures)
        function.valueParameters.forEach { parameter ->
            rejectLwjgl(parameter.type, "$label(${parameter.name})", failures)
        }
    }

    private fun rejectLwjgl(
        type: KType,
        context: String,
        failures: MutableList<String>,
    ) {
        val classifier = type.classifier
        if (classifier is KClass<*>) {
            val name = classifier.qualifiedName ?: classifier.java.name
            if (name.startsWith("org.lwjgl")) {
                failures += "$context exposes LWJGL type $name"
            }
        }
        type.arguments.forEach { argument ->
            argument.type?.let { rejectLwjgl(it, context, failures) }
        }
    }

    private fun discoverClasses(): List<KClass<*>> {
        val loader = ApiSurfaceTest::class.java.classLoader
        val classes = ArrayList<KClass<*>>()
        for (packageName in PUBLIC_PACKAGES) {
            val path = packageName.replace('.', '/')
            val resources = loader.getResources(path)
            while (resources.hasMoreElements()) {
                val url = resources.nextElement()
                if (url.protocol != "file") {
                    continue
                }
                val normalizedPath = url.path.replace('\\', '/')
                if (normalizedPath.contains("/classes/kotlin/test") || normalizedPath.contains("/classes/java/test")) {
                    continue
                }
                val directory = Path.of(url.toURI())
                Files.list(directory).use { paths ->
                    val names =
                        paths
                            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
                            .map { it.fileName.toString().removeSuffix(".class") }
                            .collect(Collectors.toList())
                    for (simpleName in names) {
                        if (simpleName.contains('$')) {
                            continue
                        }
                        classes += load("$packageName.$simpleName")
                    }
                }
            }
        }
        return classes
    }

    private fun load(name: String): KClass<*> =
        Class.forName(name, false, ApiSurfaceTest::class.java.classLoader).kotlin

    private fun visibilityOf(type: KClass<*>): KVisibility =
        (type as Any).javaClass.getMethod("getVisibility").invoke(type) as KVisibility

    private companion object {
        private val PUBLIC_PACKAGES =
            listOf(
                "com.psiwray.organization.terminal_window",
                "com.psiwray.organization.terminal_window.font",
                "com.psiwray.organization.terminal_window.input",
            )

        private val EXPECTED_INTERNAL_TYPES =
            setOf(
                "com.psiwray.organization.terminal_window.GlfwBackend",
                "com.psiwray.organization.terminal_window.GlfwError",
                "com.psiwray.organization.terminal_window.font.Atlas",
                "com.psiwray.organization.terminal_window.font.AtlasBaker",
                "com.psiwray.organization.terminal_window.font.AtlasLayout",
                "com.psiwray.organization.terminal_window.font.BitmapFontParser",
                "com.psiwray.organization.terminal_window.font.Glyph",
            )

        private val EXPECTED_PUBLIC_TYPES =
            setOf(
                "com.psiwray.organization.terminal_window.Colors",
                "com.psiwray.organization.terminal_window.ContextInfo",
                "com.psiwray.organization.terminal_window.CursorImage",
                "com.psiwray.organization.terminal_window.CursorMode",
                "com.psiwray.organization.terminal_window.CursorShape",
                "com.psiwray.organization.terminal_window.GlException",
                "com.psiwray.organization.terminal_window.GlfwException",
                "com.psiwray.organization.terminal_window.GlfwOwnership",
                "com.psiwray.organization.terminal_window.RenderStats",
                "com.psiwray.organization.terminal_window.ResizePolicy",
                "com.psiwray.organization.terminal_window.TerminalMatrix",
                "com.psiwray.organization.terminal_window.TerminalWindow",
                "com.psiwray.organization.terminal_window.TerminalWindowConfig",
                "com.psiwray.organization.terminal_window.TerminalWindowService",
                "com.psiwray.organization.terminal_window.WindowIcon",
                "com.psiwray.organization.terminal_window.font.BitmapFont",
                "com.psiwray.organization.terminal_window.font.FontFormatException",
                "com.psiwray.organization.terminal_window.input.CharEvent",
                "com.psiwray.organization.terminal_window.input.ClipboardListener",
                "com.psiwray.organization.terminal_window.input.CloseRequestedEvent",
                "com.psiwray.organization.terminal_window.input.ContentScaleEvent",
                "com.psiwray.organization.terminal_window.input.CursorEnterEvent",
                "com.psiwray.organization.terminal_window.input.CursorPosition",
                "com.psiwray.organization.terminal_window.input.FocusEvent",
                "com.psiwray.organization.terminal_window.input.InputEvent",
                "com.psiwray.organization.terminal_window.input.InputListener",
                "com.psiwray.organization.terminal_window.input.Key",
                "com.psiwray.organization.terminal_window.input.KeyAction",
                "com.psiwray.organization.terminal_window.input.KeyEvent",
                "com.psiwray.organization.terminal_window.input.Modifiers",
                "com.psiwray.organization.terminal_window.input.MouseButton",
                "com.psiwray.organization.terminal_window.input.MouseButtonEvent",
                "com.psiwray.organization.terminal_window.input.MouseMoveEvent",
                "com.psiwray.organization.terminal_window.input.ResizeEvent",
                "com.psiwray.organization.terminal_window.input.ScrollEvent",
            )
    }
}
