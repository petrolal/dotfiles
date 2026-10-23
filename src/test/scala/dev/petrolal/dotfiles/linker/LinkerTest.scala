package dev.petrolal.dotfiles.linker

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.{Files, Path}

class LinkerTest:

  @Test
  def isPrefixMatchMatchesExactAndNestedPaths(): Unit =
    assertTrue(Linker.isPrefixMatch("config", "config"))
    assertTrue(Linker.isPrefixMatch("config", "config/gtk-3.0/settings.ini"))
    assertFalse(Linker.isPrefixMatch("config", "configure.sh"))
    assertFalse(Linker.isPrefixMatch("config/gtk-3.0", "config/gtk-2.0/gtkrc"))

  @Test
  def isExcludedMatchesListedFileAndSkipsUnrelatedOnes(): Unit =
    assertTrue(Linker.isExcluded("config/gtk-2.0/gtkrc"))
    assertFalse(Linker.isExcluded("config/gtk-3.0/settings.ini"))

  @Test
  def rootTargetForPicksLongestMatchingPrefix(): Unit =
    val target = Linker.rootTargetFor("config/applications/foo.desktop")
    assertTrue(target.isDefined)
    assertEquals(".local/share/applications", target.get.to)

    val generic = Linker.rootTargetFor("config/gtk-3.0/settings.ini")
    assertTrue(generic.isDefined)
    assertEquals(".config", generic.get.to)

    assertTrue(Linker.rootTargetFor("not-a-root/file").isEmpty)

  @Test
  def linkFileCreatesValidSymlinkAndUnlinkRemovesIt(@TempDir tempDir: Path): Unit =
    val root = tempDir.resolve("repo")
    val home = tempDir.resolve("home")
    Files.createDirectories(root)
    Files.createDirectories(home)

    val srcFile = root.resolve("config/test.ini")
    Files.createDirectories(srcFile.getParent)
    Files.writeString(srcFile, "hello=world\n")

    // Dry-run should not create link
    val dryResult = Linker.linkFile("config/test.ini", ".config/test.ini", root, home, true, false)
    assertTrue(dryResult.isOk)
    assertFalse(Files.exists(home.resolve(".config/test.ini")))

    // Real link should create valid symlink
    val realResult = Linker.linkFile("config/test.ini", ".config/test.ini", root, home, false, false)
    assertTrue(realResult.isOk)
    val dest = home.resolve(".config/test.ini")
    assertTrue(Files.isSymbolicLink(dest))
    assertEquals("hello=world\n", Files.readString(dest))

    // Unlink should remove symlink
    val unlinkOutcome = Linker.unlinkFile(".config/test.ini", home, false, false)
    assertEquals(Linker.UnlinkOutcome.REMOVED, unlinkOutcome)
    assertFalse(Files.exists(dest))
