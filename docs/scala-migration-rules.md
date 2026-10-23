# Scala migration — rules

Hard constraints for the Java → Scala port of `dotfiles-deployer`. These are
not preferences; breaking one of these should be treated as a regression to
fix, not a tradeoff to accept.

## Concurrency

1. **No actor framework (Pekko/Akka).** Nothing in this app distributes
   work across processes/machines or needs supervision trees — it's a
   single-process desktop daemon. An actor runtime adds dependency weight
   (Netty, Typesafe Config, dynamic classloading) for no capability the app
   uses. Port existing patterns (virtual threads, single-thread executors,
   `volatile`/`Atomic` handoffs, `CompletableFuture`) into Scala as-is.
2. **The render loop stays on one thread.** `X11Canvas`/GLX calls are not
   thread-safe (no `XInitThreads()` anywhere). The wallpaper and lock-screen
   render loops must remain a plain single-threaded loop, never dispatched
   across a thread pool or scheduler, regardless of which concurrency
   library is used elsewhere.
3. **No `synchronized`.** The codebase is deliberately lock-free today
   (confinement by convention, `volatile`/`Atomic` single-writer handoffs).
   Keep that style; reach for a lock only if a genuine data race shows up
   that confinement can't solve.

## Dependencies

4. **Zero new runtime dependencies by default.** The app has none today
   (JUnit is test-only). Every dependency added during the port (a
   structured-concurrency library, a CLI-parsing library, a test framework)
   is a deliberate exception that must be justified against native-image
   cost — not a drive-by addition.
5. **No CLI parsing framework** (picocli, decline, scopt, ...) unless the
   user explicitly asks for one. The hand-rolled argv parser in `Cli` has
   zero reflection dependency; a framework swap is net-new native-image risk
   for no functional gain.
6. **No JSON/serialization library.** The app doesn't serialize anything
   today — keep it that way. Adding one (circe, upickle, Jackson) only to
   satisfy a "feels more idiomatic" urge reintroduces the reflection-heavy
   footprint the codebase has avoided.

## Native interop (FFM)

7. **Preserve `java.lang.foreign` usage as-is.** It's a plain Java API,
   fully usable from Scala without a wrapper library — don't introduce JNA
   or a Scala FFI wrapper crate "for ergonomics."
8. **Mirror the native-image reachability metadata exactly.** The one
   upcall entry (`PamAuthService.conversation`) must be updated in
   `reachability-metadata.json` the moment that class's package/name
   changes during the port — a silent mismatch fails native-image
   compilation, not the app at runtime.
9. **Keep `ShaderPipeline`/`BlitPipeline` FFM-free.** They currently never
   touch `MethodHandle`/`Arena`/`MemorySegment` directly, going through
   `X11Canvas`'s typed wrapper only — preserve that boundary; it's what
   makes those two classes safe to port before `X11Canvas` itself.

## Build

10. **sbt only builds/tests/packages.** Desktop-reload and NixOS
    orchestration tasks (`reload`, `nix-switch`, `deploy`, etc.) do not
    belong in `build.sbt` — they move to a Makefile/shell script that
    invokes the compiled binary. sbt's task DSL is the wrong tool for
    `exec{}`-style shell orchestration that Gradle's Groovy handled cheaply.
11. **Validate native-image after every module**, not just at the end.
    Rebuild and smoke-test before moving to the next package in the
    migration order — see [scala-migration-checklist.md](scala-migration-checklist.md).

## Process

12. **Incremental, never big-bang.** The app must build and run at every
    intermediate step. In practice (confirmed starting with steps 0–2):
    this means Gradle keeps full, untouched ownership of whatever hasn't
    been ported yet — it's the thing actually producing a working
    `bin/invoker` throughout the migration — while sbt's sources are
    scoped (`Compile`/`Test` `unmanagedSourceDirectories`) to see *only*
    the Scala files that already exist. A package only moves from "Gradle
    owns it" to "sbt owns it" once every one of its *callers* has also
    moved — e.g. `domain/` and `core/`'s leaves ported cleanly on their
    own, but `core/Orchestrator` had to be deferred (see checklist step 2)
    because its callees weren't ported yet. This is a stricter reading
    than literal joint Java+Scala compilation inside one sbt build; don't
    assume the two build tools need to compile each other's half-finished
    output at any point.
13. **Port behavior, not style.** Don't redesign package boundaries, CLI
    flags, or user-facing behavior as part of this migration — that's a
    separate change with its own review. Scala-idiomatic internals (sealed
    traits/enums instead of `sealed interface`+records, `Either`-style
    combinators) are in scope; changing *what the tool does* is not.

See also: [scala-migration-tech.md](scala-migration-tech.md) for the specific
toolchain, [scala-migration-usecases.md](scala-migration-usecases.md) for
what each command/module actually does, and
[scala-migration-checklist.md](scala-migration-checklist.md) for the ordered
execution steps.
