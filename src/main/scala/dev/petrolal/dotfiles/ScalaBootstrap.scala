package dev.petrolal.dotfiles

// Placeholder entry point for docs/scala-migration-checklist.md step 0:
// exists only to prove the sbt + native-image pipeline works end to end,
// before any real module is ported. Deleted once step 5 (`cli/`) ports the
// real Cli entry point into this build.
object ScalaBootstrap:
  def main(args: Array[String]): Unit =
    println("sbt + native-image pipeline OK (Scala migration step 0)")
