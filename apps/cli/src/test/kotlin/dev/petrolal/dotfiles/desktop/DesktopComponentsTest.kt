package dev.petrolal.dotfiles.desktop

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

internal class DesktopComponentsTest {
    @Test
    fun browserLauncherFlagsIncludeWaylandAndGpu() {
        val flags = BrowserLauncher.OPTIMIZED_FLAGS
        Assertions.assertTrue(flags.contains("--ozone-platform=wayland"))
        Assertions.assertTrue(flags.contains("--use-gl=angle"))
        Assertions.assertTrue(flags.contains("--use-angle=gl"))
        Assertions.assertTrue(flags.contains("--enable-zero-copy"))
    }

    @Test
    fun notificationLoggerCacheDirExists() {
        val dir = NotificationLogger.getCacheDir()
        Assertions.assertTrue(dir.exists())
        Assertions.assertTrue(dir.isDirectory)
    }

    @Test
    fun notificationLoggerHistoryFileExists() {
        val file = NotificationLogger.getHistoryFile()
        Assertions.assertTrue(file.exists())
    }

    @Test
    fun notificationEntryFormatting() {
        val entry = NotificationCenter.NotificationEntry(
            line = 0,
            datetime = "28/10 15:30",
            app = "Terminal",
            summary = "Done",
            body = "OK"
        )
        Assertions.assertEquals("[28/10 15:30] Terminal: Done — OK", entry.toMenuLabel())

        val entryWithoutBody = NotificationCenter.NotificationEntry(
            line = 1,
            datetime = "28/10 15:30",
            app = "Terminal",
            summary = "Build Complete",
            body = ""
        )
        Assertions.assertEquals("[28/10 15:30] Terminal: Build Complete", entryWithoutBody.toMenuLabel())

        val longEntry = NotificationCenter.NotificationEntry(
            line = 2,
            datetime = "28/10 15:30",
            app = "Terminal",
            summary = "Super long summary that exceeds the maximum display length for tofi",
            body = "And a body that is also long"
        )
        Assertions.assertTrue(longEntry.toMenuLabel().endsWith("..."))
        Assertions.assertTrue(longEntry.toMenuLabel().length <= 60)
    }

    @Test
    fun barLauncherInvalidActionReturnsErr() {
        val result = BarLauncher.run("invalid-action")
        Assertions.assertTrue(result.isErr())
    }

    @Test
    fun hudLauncherInvalidActionReturnsErr() {
        val result = HudLauncher.run("invalid-action")
        Assertions.assertTrue(result.isErr())
    }

    @Test
    fun retroRoomControlAnchorValidation() {
        Assertions.assertTrue(RetroRoomControl.VALID_ANCHORS.contains("home"))
        Assertions.assertTrue(RetroRoomControl.VALID_ANCHORS.contains("terminal"))
        Assertions.assertTrue(RetroRoomControl.VALID_ANCHORS.contains("music"))

        val invalidResult = RetroRoomControl.travel("invalid-anchor")
        Assertions.assertTrue(invalidResult.isErr())

        val statusResult = RetroRoomControl.status()
        Assertions.assertTrue(statusResult.isOk())
        Assertions.assertTrue(statusResult.getOrNull()!!.contains("3D Retro Room Status"))
    }

    @Test
    fun doctorRunsDiagnosticChecks() {
        val result = Doctor.run()
        Assertions.assertTrue(result.isOk())
        val output = result.getOrNull()!!
        Assertions.assertTrue(output.contains("RetroVHSSucks Desktop Doctor"))
        Assertions.assertTrue(output.contains("Compositor (Hyprland)"))
        Assertions.assertTrue(output.contains("Status Bar (AGS)"))
        Assertions.assertTrue(output.contains("HUD Overlays (Quickshell)"))
    }
}
