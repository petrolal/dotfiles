// KeybindHelper.kt --- Tofi-based keybinding cheat sheet for Hyprland
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.desktop

import dev.petrolal.dotfiles.core.DotfilePaths
import java.nio.file.Files

object KeybindHelper {
    data class Bind(val keys: String, val action: String) {
        fun toMenuLabel(): String {
            val label = "$keys  →  $action"
            return if (label.length > 70) label.take(67) + "..." else label
        }
    }

    /** `mainMod` is set as a plain Lua global by config/hypr/hyprland.lua
     * (see that file's header comment) and is always "SUPER" there -- kept
     * here as the one hardcoded fact this parser needs, since it can't run
     * the Lua to ask hyprland.lua what the global actually resolved to. */
    private const val MAIN_MOD = "SUPER"

    /** Parses config/hypr/conf/keybindings.lua's `hl.bind(keys, dispatcher,
     * { ..., description = "..." })` calls directly, instead of hand-maintaining
     * a separate list here that silently drifts out of sync with the real
     * binds (this file used to be exactly that drift-prone list).
     *
     * This is a targeted scrape of this file's specific call shape, not a
     * Lua interpreter: it resolves `mainMod .. " + X"` key expressions and
     * literal string keys, but a key expression built from a non-literal,
     * non-mainMod identifier (as in the workspace for-loop below, which binds
     * a `key` loop variable) can't be resolved statically. Those two dynamic
     * binds are recognized by their description prefix and replaced with a
     * single hand-written summary row each; anything else dynamic is
     * silently omitted rather than guessed at. */
    @JvmStatic
    fun loadBinds(): List<Bind> {
        val luaPath = DotfilePaths.findDotfilesRoot().resolve("config/hypr/conf/keybindings.lua")
        if (!Files.exists(luaPath)) return emptyList()
        val text = Files.readString(luaPath)

        val parsed = mutableListOf<Bind>()
        var searchFrom = 0
        while (true) {
            val callStart = text.indexOf("hl.bind(", searchFrom)
            if (callStart < 0) break
            val argsStart = callStart + "hl.bind(".length
            val callEnd = findMatchingParen(text, argsStart - 1)
            if (callEnd < 0) break
            val callBody = text.substring(argsStart, callEnd)
            searchFrom = callEnd + 1

            val keysEnd = findTopLevelComma(callBody)
            if (keysEnd < 0) continue
            val keysExpr = callBody.substring(0, keysEnd).trim()
            val description = DESCRIPTION_RE.find(callBody.substring(keysEnd))?.groupValues?.get(1) ?: continue

            toBind(keysExpr, description)?.let { bind ->
                // The workspace for-loop below produces the same synthetic
                // placeholder bind 10 times in a row; collapse to one.
                if (parsed.lastOrNull() != bind) parsed.add(bind)
            }
        }

        return mergeSameAction(parsed)
    }

    private fun toBind(keysExpr: String, description: String): Bind? {
        val keys = resolveKeys(keysExpr)
        if (keys != null) return Bind(keys, description)
        return when {
            description.startsWith("Focus workspace") ->
                Bind("$MAIN_MOD + 0-9", "Focus workspace 1-10 (0 = workspace 10)")
            description.startsWith("Move window to workspace") ->
                Bind("$MAIN_MOD + SHIFT + 0-9", "Move focused window to workspace 1-10")
            else -> null // dynamic key expression this parser can't resolve; omit rather than guess.
        }
    }

    /** Resolves a `hl.bind` keys argument to a display string, or null if it
     * depends on something other than a string literal or `mainMod`. */
    private fun resolveKeys(expr: String): String? {
        if (!expr.contains("..")) return unquote(expr)
        val parts = expr.split("..").map { it.trim() }
        val resolved = StringBuilder()
        for (part in parts) {
            when {
                part == "mainMod" -> resolved.append(MAIN_MOD)
                part.startsWith("\"") && part.endsWith("\"") -> resolved.append(unquote(part))
                else -> return null
            }
        }
        return resolved.toString()
    }

    private fun unquote(s: String): String? =
        if (s.startsWith("\"") && s.endsWith("\"")) s.substring(1, s.length - 1) else null

    /** Collapses consecutive binds with the same description into one row
     * with their keys joined (e.g. "SUPER + L" and "SUPER + CTRL + L", both
     * "Lock Screen", become one "SUPER + L / SUPER + CTRL + L" row). */
    private fun mergeSameAction(binds: List<Bind>): List<Bind> {
        val merged = mutableListOf<Bind>()
        for (bind in binds) {
            val last = merged.lastOrNull()
            if (last != null && last.action == bind.action) {
                merged[merged.size - 1] = Bind("${last.keys} / ${bind.keys}", last.action)
            } else {
                merged.add(bind)
            }
        }
        return merged
    }

    private fun findMatchingParen(text: String, openParenIndex: Int): Int {
        var depth = 0
        var inString = false
        var i = openParenIndex
        while (i < text.length) {
            val c = text[i]
            if (c == '"') inString = !inString
            if (!inString) {
                if (c == '(') depth++
                if (c == ')') {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun findTopLevelComma(s: String): Int {
        var depth = 0
        var inString = false
        for (i in s.indices) {
            val c = s[i]
            if (c == '"') inString = !inString
            if (inString) continue
            when (c) {
                '(', '{' -> depth++
                ')', '}' -> depth--
                ',' -> if (depth == 0) return i
            }
        }
        return -1
    }

    private val DESCRIPTION_RE = Regex("description\\s*=\\s*\"([^\"]*)\"")

    @JvmStatic
    fun show(): Int {
        val binds = loadBinds()
        val menu = if (binds.isEmpty()) {
            "(No keybindings found -- is config/hypr/conf/keybindings.lua readable?)"
        } else {
            binds.joinToString("\n") { it.toMenuLabel() }
        }
        Tofi.prompt(menu, "> ", "keybinds:")
        return 0
    }
}
