package dev.petrolal.dotfiles.linker

import dev.petrolal.dotfiles.linker.Linker.UnlinkOutcome
import dev.petrolal.dotfiles.linker.Linker.isExcluded
import dev.petrolal.dotfiles.linker.Linker.isPrefixMatch
import dev.petrolal.dotfiles.linker.Linker.linkFile
import dev.petrolal.dotfiles.linker.Linker.rootTargetFor
import dev.petrolal.dotfiles.linker.Linker.unlinkFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal class LinkerTest {
    @Test
    fun isPrefixMatchMatchesExactAndNestedPaths() {
        Assertions.assertTrue(isPrefixMatch("config", "config"))
        Assertions.assertTrue(isPrefixMatch("config", "config/gtk-3.0/settings.ini"))
        Assertions.assertFalse(isPrefixMatch("config", "configure.sh"))
        Assertions.assertFalse(isPrefixMatch("config/gtk-3.0", "config/gtk-2.0/gtkrc"))
    }

    @Test
    fun isExcludedMatchesListedFileAndSkipsUnrelatedOnes() {
        Assertions.assertTrue(isExcluded("config/gtk-2.0/gtkrc"))
        Assertions.assertFalse(isExcluded("config/gtk-3.0/settings.ini"))
    }

    @Test
    @Throws(IOException::class)
    fun linkFileCreatesValidSymlinkAndUnlinkRemovesIt(@TempDir tempDir: Path) {
        val root = tempDir.resolve("repo")
        val home = tempDir.resolve("home")
        Files.createDirectories(root)
        Files.createDirectories(home)

        val srcFile = root.resolve("config/test.ini")
        Files.createDirectories(srcFile.getParent())
        Files.writeString(srcFile, "hello=world\n")

        // Dry-run should not create link
        val dryResult = linkFile("config/test.ini", ".config/test.ini", root, home, true, false)
        Assertions.assertTrue(dryResult.isOk())
        Assertions.assertFalse(Files.exists(home.resolve(".config/test.ini")))

        // Real link should create valid symlink
        val realResult = linkFile("config/test.ini", ".config/test.ini", root, home, false, false)
        Assertions.assertTrue(realResult.isOk())
        val dest = home.resolve(".config/test.ini")
        Assertions.assertTrue(Files.isSymbolicLink(dest))
        Assertions.assertEquals("hello=world\n", Files.readString(dest))

        // Unlink should remove symlink
        val unlinkOutcome = unlinkFile(".config/test.ini", home, false, false)
        Assertions.assertEquals(UnlinkOutcome.REMOVED, unlinkOutcome)
        Assertions.assertFalse(Files.exists(dest))
    }
}
