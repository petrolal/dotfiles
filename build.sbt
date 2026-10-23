import java.io.File
import scala.sys.process._

// Step 0 of docs/scala-migration-checklist.md: bring up the sbt + native-image
// pipeline in isolation, with a trivial bootstrap object, before any real
// app code is ported. `src/main/java` (the Gradle-built legacy app) is
// intentionally excluded from sbt's sources for now -- it stays owned by
// build.gradle until each package is ported module-by-module per the
// checklist, at which point its Scala replacement lands under
// src/main/scala and the matching Java file is retired.

ThisBuild / scalaVersion := "3.7.3"
ThisBuild / version := "1.0.0"
ThisBuild / organizationName := "dev.petrolal"

// Mirrors build.gradle's resolveGraalVmHome(): prefer GRAALVM_HOME, else
// sniff the native-image binary already on PATH (this machine's Nix profile
// provides one). Falls back to the plugin's own Coursier-managed GraalVM
// download if neither resolves.
lazy val graalVmHome: Option[File] =
  sys.env
    .get("GRAALVM_HOME")
    .orElse {
      scala.util
        .Try {
          Seq(
            "sh",
            "-c",
            "dirname $(dirname $(readlink -f $(command -v native-image 2>/dev/null) 2>/dev/null))"
          ).!!.trim
        }
        .toOption
        .filter(_.nonEmpty)
    }
    .map(new File(_))
    .filter(_.isDirectory)

// TODO (carry over from build.gradle if the Nix sandbox breaks native-image's
// own cc invocation the same way it did under Gradle): that build scrubbed
// NIX_BINTOOLS/NIX_CC/NIX_LDFLAGS/etc. from the environment before invoking
// native-image. sbt-native-image's `nativeImageCommand` key can be overridden
// to wrap the call in `env -u VAR ...` if this turns out to be needed here too
// -- left as a follow-up until a real nativeImage run surfaces (or doesn't)
// the same failure.
lazy val graalSettings: Seq[Def.Setting[_]] =
  graalVmHome.toSeq.flatMap { home =>
    Seq(
      javaHome := Some(home),
      nativeImageInstalled := true,
      nativeImageGraalHome := home.toPath
    )
  }

lazy val root = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "dotfiles-deployer",
    // Legacy src/main/java and src/test/java stay Gradle-owned until their
    // matching package is ported (docs/scala-migration-checklist.md) --
    // sbt only sees what's already been moved to Scala.
    Compile / unmanagedSourceDirectories := Seq(baseDirectory.value / "src" / "main" / "scala"),
    Test / unmanagedSourceDirectories := Seq(baseDirectory.value / "src" / "test" / "scala"),
    Compile / mainClass := Some("dev.petrolal.dotfiles.cli.Cli"),
    // sbt-jupiter-interface (project/plugins.sbt) replaces sbt's own
    // (annotation-fingerprint-based) test discovery with one that uses the
    // JUnit Platform's own discovery mechanism -- without the plugin,
    // @Test-annotated classes silently produce zero discovered tests. This
    // Test-scope dependency is the runtime half the plugin requires in
    // every module that runs JUnit 5 tests; JupiterKeys.jupiterVersion
    // picks a junit-jupiter-api/engine/platform version set known to work
    // together for the plugin version pinned above.
    libraryDependencies += "com.github.sbt.junit" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
    nativeImageOutput := baseDirectory.value / "bin" / "invoker",
    nativeImageOptions ++= Seq(
      "-O3",
      "--gc=serial",
      "-march=native",
      "--enable-native-access=ALL-UNNAMED"
    )
  )
  .settings(graalSettings: _*)
