// Linker.scala --- Symlink discovery, creation, and removal
// License: GPL-3.0-or-later
// Ported from Linker.java (docs/scala-migration-checklist.md step 4).
package dev.petrolal.dotfiles.linker

import java.io.IOException
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor, StandardCopyOption}
import java.nio.file.attribute.BasicFileAttributes
import scala.jdk.CollectionConverters.*
import scala.util.Try

import dev.petrolal.dotfiles.core.Theme
import dev.petrolal.dotfiles.domain.{DotfileError, Result}

object Linker:

  /** A (from -> to) path pair: used both for root-target prefixes and resolved mappings. */
  case class Mapping(from: String, to: String)

  enum UnlinkOutcome:
    case SKIPPED, REMOVED, FAILED

  /** Source prefixes (relative to the repo root) auto-discovered and mirrored
    * file-by-file under the given target base (relative to $HOME). Longest
    * matching prefix wins, so a more specific entry can override a general one. */
  val ROOT_TARGETS: List[Mapping] = List(
    Mapping("config/applications", ".local/share/applications"),
    Mapping("config/icons", ".local/share/icons"),
    Mapping("config", ".config")
  )

  /** Source paths skipped entirely during auto-discovery; a listed directory
    * is not descended into. */
  val EXCLUDES: List[String] = List(
    "config/gtk-2.0/gtkrc"
  )

  /** Explicit (source -> target) pairs for paths that don't fit the generic
    * root-mirrored layout: renames, or a source linked to more than one target. */
  val OVERRIDES: List[Mapping] = List(
    Mapping("config/gtk-2.0/gtkrc", ".gtkrc-2.0"),
    Mapping(s"themes/${Theme.NAME}", s".themes/${Theme.NAME}"),
    Mapping(s"themes/${Theme.NAME}", s".local/share/themes/${Theme.NAME}")
  )

  def isExcluded(relpath: String): Boolean =
    EXCLUDES.exists(prefix => isPrefixMatch(prefix, relpath))

  def isPrefixMatch(prefix: String, relpath: String): Boolean =
    relpath.length >= prefix.length
      && relpath.startsWith(prefix)
      && (relpath.length == prefix.length || relpath.charAt(prefix.length) == '/')

  def rootTargetFor(relpath: String): Option[Mapping] =
    ROOT_TARGETS.foldLeft(Option.empty[Mapping]) { (best, entry) =>
      if isPrefixMatch(entry.from, relpath) && best.forall(entry.from.length > _.from.length) then Some(entry)
      else best
    }

  def topLevelRoots(): List[String] =
    val prefixes = ROOT_TARGETS.map(_.from)
    prefixes.filterNot { prefix =>
      prefixes.exists(other => other != prefix && isPrefixMatch(other, prefix))
    }

  def scanTree(relpath: String, root: Path): List[String] =
    val dir = root.resolve(relpath)
    if !Files.isDirectory(dir) then Nil
    else
      val entries =
        try
          val stream = Files.list(dir)
          try stream.sorted().iterator().asScala.toList
          finally stream.close()
        catch case _: IOException => Nil
      entries.flatMap { entry =>
        val childRel = s"$relpath/${entry.getFileName}"
        if isExcluded(childRel) then Nil
        else if Files.isDirectory(entry) then scanTree(childRel, root)
        else List(childRel)
      }

  def collectAllMappings(root: Path): List[Mapping] =
    val discovered = for
      top <- topLevelRoots()
      fileRel <- scanTree(top, root)
      entry <- rootTargetFor(fileRel)
    yield Mapping(fileRel, entry.to + fileRel.substring(entry.from.length))
    OVERRIDES ++ discovered

  private def deleteRecursively(path: Path): Unit =
    Files.walkFileTree(path, new SimpleFileVisitor[Path]:
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
        Files.delete(file)
        FileVisitResult.CONTINUE

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult =
        Files.delete(dir)
        FileVisitResult.CONTINUE
    )
    ()

  def ensureRealDirectory(dir: Path, home: Path): Unit =
    val relOpt = Try(home.relativize(dir)).toOption
    relOpt match
      case None =>
        Files.createDirectories(dir)
      case Some(rel) =>
        var current = home
        for part <- rel.asScala do
          current = current.resolve(part)
          if Files.isSymbolicLink(current) then Files.delete(current)
        Files.createDirectories(dir)

  private def atomicSymlink(dest: Path, target: Path): Unit =
    val parent = dest.getParent
    val tmp = Files.createTempFile(parent, dest.getFileName.toString, ".tmp-symlink")
    Files.delete(tmp)
    Files.createSymbolicLink(tmp, target)
    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    ()

  def linkFile(sourceRel: String, targetRel: String, root: Path, home: Path,
               dryRun: Boolean, verbose: Boolean): Result[Unit] =
    val src = root.resolve(sourceRel)
    val dest = home.resolve(targetRel)
    if !Files.exists(src) then
      if verbose then System.err.println(s"[SKIP] Source missing: $src")
      Result.err(DotfileError.FileNotFound(src))
    else if dryRun then
      println(s"[DRY-RUN] Would link: $dest -> $src")
      Result.ok(())
    else
      try
        ensureRealDirectory(dest.getParent, home)
        if Files.isDirectory(src) && Files.isDirectory(dest) && !Files.isSymbolicLink(dest) then
          deleteRecursively(dest)
        atomicSymlink(dest, src)
        if verbose then println(s"[OK] Linked: $dest -> $src")
        Result.ok(())
      catch
        case e: IOException =>
          System.err.println(s"[FAIL] Failed to link $dest -> $src")
          Result.err(DotfileError.IoError(e.getMessage))

  def unlinkFile(targetRel: String, home: Path, dryRun: Boolean, verbose: Boolean): UnlinkOutcome =
    val dest = home.resolve(targetRel)
    if !Files.isSymbolicLink(dest) then
      if verbose then
        if Files.exists(dest) then System.err.println(s"[SKIP] Not a symlink: $dest (refusing to delete)")
        else println(s"[SKIP] Symlink does not exist: $dest")
      UnlinkOutcome.SKIPPED
    else if dryRun then
      println(s"[DRY-RUN] Would remove symlink: $dest")
      UnlinkOutcome.REMOVED
    else
      try
        Files.delete(dest)
        if verbose then println(s"[OK] Removed symlink: $dest")
        UnlinkOutcome.REMOVED
      catch
        case _: IOException =>
          System.err.println(s"[FAIL] Failed to remove symlink $dest")
          UnlinkOutcome.FAILED
