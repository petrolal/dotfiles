# Scala migration — tech stack

Concrete tool/version choices for the port. See
[scala-migration-rules.md](scala-migration-rules.md) for the constraints
these choices have to satisfy.

## Language & JDK

| Today | Target |
| --- | --- |
| Java 25, `sourceCompatibility`/`targetCompatibility = 25` | Scala 3 (3.7.x line), compiled targeting the same Java 25/GraalVM JDK |

Scala 3 over Scala 2: standard bytecode output, no mandatory runtime
reflection for case classes/enums/pattern matching, cleaner native-image
story (Scala 2 implicits/macros historically generate more reflective
boilerplate). GraalVM stays the JDK distribution — `native-image` needs the
GraalVM toolchain regardless of source language, so
[dev-environments.md](dev-environments.md)'s `jvm-init`/`shell.nix` Java
setup is unaffected; just uncomment the `scala`/`sbt` line it already
documents.

## Build tool: sbt

Switching from Gradle to sbt per user preference (an explicit second risk
axis alongside the language change — see rule 10 in
[scala-migration-rules.md](scala-migration-rules.md)).

- **`project/build.properties`** pins the sbt launcher version.
- **`project/plugins.sbt`** adds `sbt-native-image`
  (`com.github.scalameta % sbt-native-image`), which wraps the
  `native-image` CLI and accepts arbitrary options via
  `nativeImageOptions`, so the existing build's flags carry over as plain
  strings:
  ```scala
  nativeImageOptions ++= Seq(
    "-O3",
    "--gc=serial", // see build.gradle comment: Epsilon leaked ~1GB RSS/3.5min
    "-march=native",
    "--enable-native-access=ALL-UNNAMED"
  )
  ```
- **Java/Scala joint compilation**: sbt compiles `.java` and `.scala` under
  the same source root natively — no special plugin needed for this part,
  unlike the native-image step.
- **What moves out of the build tool**: `reload`, `reload-panel`,
  `reload-wm`, `reload-theme`, `nix-link`, `nix-switch`, `nix-check`,
  `nix-update`, `nix-gc`, `deploy`, `dry-run`, `uninstall`,
  `system-install`, `upgrade`, and the `mostly-clean`/`dist-clean`/
  `maintainer-clean` family — these are shell orchestration around the
  built binary and NixOS, not Scala build concerns. They move to a
  Makefile (or equivalent shell script) that `sbt nativeImage` output feeds
  into, mirroring each Gradle `exec{}` block almost line for line.
- **What GraalVM config carries over unchanged**: the `resolveGraalVmHome()`
  logic (env var or `native-image` binary location sniffing) and the Nix
  sandbox env-var scrubbing (`NIX_BINTOOLS`, `NIX_CC`, etc.) — reimplement
  as sbt settings reading `sys.env`, same behavior as the Gradle Groovy
  version.
- **First milestone, before porting any app code**: an empty/trivial Scala
  `object Main` that successfully builds through `sbt nativeImage` with
  these settings. This validates the sbt+native-image pipeline in
  isolation, so build-tool problems and language-port problems never get
  debugged at the same time.

## Concurrency

No actor framework (rule 1). Default to plain `java.util.concurrent` from
Scala:

- `Thread.ofVirtual()` for background sampling/draining (telemetry, process
  stdout/stderr capture) — direct equivalent of today's
  `Executors.newVirtualThreadPerTaskExecutor()` / `Thread.ofVirtual()...start()`.
- `java.util.concurrent.StructuredTaskScope` where it clarifies lifetime
  (e.g. "wait for N subprocess-exit futures with a timeout," today done
  with `CompletableFuture.allOf(...).get(timeout)` in
  `WallpaperDaemonManager`).
- A dedicated single-thread `ExecutorService` for PAM calls, exactly as
  today (`PamAuthService`'s `pam-auth-worker`), returned as a
  `scala.concurrent.Future` or kept as `java.util.concurrent.Future` polled
  non-blockingly from the render loop — either works, prefer whichever
  needs the least glue code at the call site.

**Optional middle ground**: if hand-wrapping `java.util.concurrent` feels
too low-level, **Ox** (`com.softwaremill.ox`) is a structured-concurrency
library built directly on JDK virtual threads/`StructuredTaskScope`, with
no actor runtime and no reflective config loading — a much smaller
native-image surface than Pekko if the user wants something more
Scala-native than raw JDK APIs. Not adopted by default (rule 4: zero new
dependencies unless justified) — raise it only if the raw-JDK port turns
out unusually awkward.

## Native interop

Unchanged mechanism: `java.lang.foreign` (FFM API), called directly from
Scala with no wrapper. `MethodHandle.invokeExact`, `Arena`, and
`MemorySegment` usage need Scala-specific attention (exact-type invocation,
varargs syntax) but the API surface and the two bound libraries
(`libpam.so.0`, `libX11.so`/`libGL.so`/GLX) don't change. See
[scala-migration-usecases.md](scala-migration-usecases.md) for which
modules touch FFM directly vs. not.

## Testing

- Keep **JUnit 5** — sbt runs JUnit 5 against Scala-compiled classes via
  **`sbt-jupiter-interface`**, added as an actual sbt plugin in
  `project/plugins.sbt`:
  ```scala
  addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.14.0")
  ```
  plus the matching runtime dependency in `build.sbt`:
  ```scala
  libraryDependencies += "com.github.sbt.junit" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test
  ```
  **This has to be an sbt plugin, not just a library dependency.** It works
  by overriding sbt's own test-detection task with one that uses the JUnit
  Platform's own discovery mechanism — confirmed experimentally during the
  `domain` port (step 1): sbt's native, annotation-fingerprint-based
  discovery does not find plain `@Test`-annotated classes on its own, so
  without the plugin `sbt test` compiles everything, reports success, and
  silently runs zero tests. Also avoid the similarly-named but unmaintained
  `net.aichler % jupiter-interface` (last released 2022; pulls in a
  `junit-platform-launcher` too old for current `junit-jupiter`, which is
  the same silent-zero-tests failure by a different path) —
  `com.github.sbt.junit` is the maintained fork and the one actually wired
  up in `build.sbt`/`project/plugins.sbt` today.
  Mirrors today's Gradle `useJUnitPlatform()` + `@Test`-method style and the
  existing 1:1 package/test layout; no test-code rewrite needed beyond
  Java→Scala syntax.
- **MUnit** is a reasonable future upgrade (lightweight, Scala-native,
  reflection-free, and its `SubclassFingerprint`-based discovery sidesteps
  this whole class of problem) if the user later wants more idiomatic Scala
  assertions — not a blocker for this migration.

## Native-image reachability metadata

File stays in the unified `reachability-metadata.json` format at
`src/main/resources/META-INF/native-image/dev.petrolal/dotfiles-deployer/`.
Content today is minimal (two JDK-internal reflection entries plus one
`directUpcalls` entry for `PamAuthService.conversation`) — keep it that
small; any growth during the port is a signal something introduced
unwanted reflection (see checklist's per-module native-image rebuild step).
