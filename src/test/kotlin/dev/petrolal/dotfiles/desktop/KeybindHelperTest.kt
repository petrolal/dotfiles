package dev.petrolal.dotfiles.desktop

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class KeybindHelperTest {
    @Test
    fun parsesRealKeybindingsLuaWithoutDrift() {
        val binds = KeybindHelper.loadBinds()
        Assertions.assertTrue(binds.isNotEmpty(), "expected to parse binds out of keybindings.lua")

        Assertions.assertTrue(
            binds.any { it.keys == "SUPER + RETURN" && it.action == "Open the terminal" },
            "expected a literal mainMod-concatenated bind to resolve"
        )
        Assertions.assertTrue(
            binds.any { it.keys == "ALT + SHIFT + Tab" },
            "expected a plain string-literal key bind to resolve"
        )
    }

    @Test
    fun collapsesTheWorkspaceForLoopIntoOneSyntheticRowEach() {
        val binds = KeybindHelper.loadBinds()

        val focusRow = binds.singleOrNull { it.action.startsWith("Focus workspace") }
        Assertions.assertTrue(focusRow != null, "expected exactly one collapsed 'Focus workspace' row")
        Assertions.assertEquals("SUPER + 0-9", focusRow!!.keys)

        val moveRow = binds.singleOrNull { it.action.startsWith("Move focused window to workspace") }
        Assertions.assertTrue(moveRow != null, "expected exactly one collapsed 'Move window to workspace' row")
        Assertions.assertEquals("SUPER + SHIFT + 0-9", moveRow!!.keys)
    }

    @Test
    fun mergesConsecutiveBindsForTheSameAction() {
        val binds = KeybindHelper.loadBinds()

        val lockRow = binds.singleOrNull { it.action == "Lock Screen" }
        Assertions.assertTrue(lockRow != null, "expected the two lock-screen binds to merge into one row")
        Assertions.assertEquals("SUPER + L / SUPER + CTRL + L", lockRow!!.keys)
    }

    @Test
    fun menuLabelIsBoundedAndIncludesKeysAndAction() {
        val label = KeybindHelper.Bind("SUPER + Q", "Kill active window").toMenuLabel()
        Assertions.assertEquals("SUPER + Q  →  Kill active window", label)
    }
}
