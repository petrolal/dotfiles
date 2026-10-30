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
}
