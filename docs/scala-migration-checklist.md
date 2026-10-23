# Scala migration — checklist

Ordered execution steps. Each module step ends with the same three
validations — do all three before moving on, not just at the end of the
whole migration. Cross-references: [scala-migration-rules.md](scala-migration-rules.md)
for the constraints, [scala-migration-tech.md](scala-migration-tech.md) for
tool specifics, [scala-migration-usecases.md](scala-migration-usecases.md)
for what each piece has to keep doing.

## 0. Build-tool bring-up (before any app code moves) — DONE

- [x] Add `project/build.properties` (sbt 1.10.7), `project/plugins.sbt`
      (`sbt-native-image` 0.3.4), `build.sbt` (Scala 3.7.3, targets the
      GraalVM 25 JDK already on this machine).
- [x] Write a trivial `object Main` (`ScalaBootstrap`) and get it through
      `sbt nativeImage` successfully, with the full flag set ported
      (`-O3`, `--gc=serial`, `-march=native`,
      `--enable-native-access=ALL-UNNAMED`, `resolveGraalVmHome()`
      equivalent). Binary runs, exits 0. Nix env-var scrubbing turned out
      unnecessary here — `native-image` only emitted harmless warnings
      about the missing `NIX_*` vars, no build failure — left as a
      commented TODO in `build.sbt` rather than silently dropped, in case
      it resurfaces once real native/FFM code is ported (steps 7–8).
- [x] Extract `reload*`/`reload-panel`/`reload-wm`/`reload-theme`/
      `nix-link`/`nix-switch`/`nix-check`/`nix-update`/`nix-gc`/`bootstrap`/
      `deploy`/`dry-run`/`uninstall`/`quick-deploy`/`install-check`/`check`/
      `system-install`/`upgrade`/clean-family tasks into `Makefile`.
      Transitional: it still calls the Gradle-built `bin/invoker`, not
      `bin/invoker-scala` — repoint once `cli/`+`Orchestrator` (step 5) and
      close-out (step 9) land.
- [x] `nixos/templates/jvm/shell.nix`: uncommented the `scala`/`sbt` lines.

## 1. `domain/` (`Result`, `DotfileError`) — DONE

- [x] Ported to Scala 3 `enum`s (`Result[+T]` = `Ok`/`Err`,
      `DotfileError`); added `map`/`flatMap` to `Result`, which the Java
      version lacked.
- [x] Ported `ResultTest`/`DotfileErrorTest` near-verbatim.
- [x] `sbt nativeImage` succeeds.
- [x] `sbt test` passes (4/4).
- **Correction found while executing this step**: the tech doc's planned
  `net.aichler % jupiter-interface` library dependency silently discovers
  *zero* tests under sbt (unmaintained, and sbt's native test discovery
  doesn't recognize plain `@Test`-annotated classes without it). Fixed by
  switching to `com.github.sbt.junit % sbt-jupiter-interface`, which must
  be installed as an actual sbt **plugin** — see
  [scala-migration-tech.md](scala-migration-tech.md)'s testing section
  (already corrected) for the full explanation and the exact
  `project/plugins.sbt`/`build.sbt` lines.

## 2. `core/` (`DotfilePaths`, `Theme` — not `Orchestrator` yet) — DONE

- [x] Ported `DotfilePaths` and `Theme`: true leaves, no dependency on any
      not-yet-ported package.
- [x] `sbt nativeImage` succeeds (verified with `DotfilePaths`/`Theme`
      only).
- [x] `sbt test` passes (no existing tests for this package — fine,
      matches today; manually smoke-tested `DotfilePaths` behavior via a
      temporary addition to `ScalaBootstrap` — both with and without
      `DOTFILES_DIR` set, confirming both the env-var and
      executable-path/cwd fallback branches — then reverted).
- **Correction found while executing this step**: `Orchestrator` is *not*
  a mechanical leaf port like the other two — it imports and calls into
  `Linker`, `Xfconf`, `DesktopServices`, `ScreenLocker`,
  `GpuOffloadLauncher`, `Wallpaper`, and `WallpaperDaemonManager`, i.e.
  essentially every other package in the app. It can't be ported with
  real behavior until all of those exist in Scala, so it's deferred to
  step 5 (see below) — moved there, not dropped.

## 3. `desktop/` + `system/` (`Xfconf`, `DesktopNotifier`, `DesktopServices`,
   `KeyboardShortcuts`, `ProcessRunner`) — DONE

- [x] Port `ProcessRunner.captureOutput`'s virtual-thread stdout/stderr
      draining pattern first — this becomes the reference implementation
      for virtual-thread usage in the rest of the Scala codebase.
- [x] Port `Xfconf`'s parallel `xfconf-query` application (virtual
      threads + whatever throttling the `Semaphore` use implies —
      check the semaphore bound carries over correctly).
- [x] Port `DesktopNotifier`, `DesktopServices`, `KeyboardShortcuts`
      (no concurrency, mechanical).
- [x] Port `XfconfTest`/`ProcessRunnerTest`.
- [x] `sbt nativeImage` succeeds.
- [x] `sbt test` passes (11/11, including the 4 new tests).

## 4. `linker/` — DONE

- [x] Mechanical port — filesystem logic, no concurrency.
- [x] Port `LinkerTest` (uses `@TempDir`, translated near-verbatim).
- [x] `sbt nativeImage` succeeds.
- [x] `sbt test` passes (15/15, including the 4 new tests).

## 5. `wallpaper/` — safer half first — DONE

- [x] Port `ImageLoader`, `GpuOffloadLauncher`, `WallpaperDaemonManager`
      (the `CompletableFuture`/telemetry-thread patterns — see
      [scala-migration-tech.md](scala-migration-tech.md) concurrency
      section for the `StructuredTaskScope` option). No existing Java
      tests for any of the three.
- [x] `sbt nativeImage` succeeds.
- [x] `sbt test` passes (15/15 — unchanged, no new tests exist to add).
- **Correction found while planning this step**: the original draft also
  put `ShaderPipeline`, `BlitPipeline`, `WallpaperEvents`, and
  `WallpaperTest` here, on the theory that the first two are "FFM-free,
  go through `X11Canvas`'s typed wrapper only." True, but irrelevant —
  all three take a live `X11Canvas` instance directly (constructor param
  for `ShaderPipeline`/`BlitPipeline`, `pollState`'s `canvas.queryMouse()`/
  `queryWorkspace()` for `WallpaperEvents`), and `WallpaperTest` exercises
  `Wallpaper.detectMonitors`, a method on the same class whose `start()`
  is wired straight into `X11Canvas`/GLX. None of the four can compile
  against a real `X11Canvas` type before step 6 exists, same class of
  mistake as the step-2/step-5(old) deferrals. Moved to step 6, not
  dropped.

## 6. `wallpaper/X11Canvas` + `Wallpaper` — the FFM/render-loop core

- [ ] Port `X11Canvas`'s FFM bindings (`libX11`, `libGL`/GLX,
      `glXGetProcAddress`-resolved GL calls) — watch `MethodHandle.invokeExact`
      exact-type and varargs syntax differences from Java.
- [ ] Port `ShaderPipeline`, `BlitPipeline` (confirmed FFM-free themselves,
      go through `X11Canvas`'s typed wrapper only, but need the real
      `X11Canvas` type to compile against — see step 5's correction note).
- [ ] Port `WallpaperEvents` (telemetry virtual-thread + `X11Canvas.queryMouse`/
      `queryWorkspace` calls from `pollState`).
- [ ] Port `Wallpaper`'s render loop, including `detectMonitors`/
      `parseListMonitors`/`parseXrandrQuery` (pure xrandr-subprocess logic,
      but kept in the same class as `start()` per rule 13 — don't split
      the class across files just to port the leaf method earlier).
      **Keep the render loop a plain single-threaded loop** (rule 2) — do
      not route it through any executor/dispatcher.
- [ ] Preserve the shutdown-hook thread that `join()`s the render thread
      with the bounded 2s grace period.
- [ ] Port `WallpaperTest` (`detectMonitors`).
- [ ] `sbt nativeImage` succeeds.
- [ ] `sbt test` passes.
- [ ] **Manual smoke test**: launch the wallpaper daemon for real (no unit
      test covers the render loop itself) — confirm render output,
      hot-reload, multi-monitor viewport handling, and clean shutdown on
      SIGTERM.
- [ ] Dedicated check on `ImageLoader`'s `BufferedImage` usage under
      native-image (pre-existing risk, independent of the language port —
      see use-cases doc).

## 7. `locker/`

- [ ] Port `LoginPromptTexture`, `PngWriter` (hand-rolled rasterizer/PNG
      encoder — keep AWT-free, same reason as today).
- [ ] Port `PamAuthService`: FFM bindings to `libpam.so.0`, the native
      upcall for the conversation callback, the dedicated single-thread
      `pam-auth-worker` executor.
- [ ] **Update `reachability-metadata.json`'s `directUpcalls` entry** to
      match the new (Scala) fully-qualified name/signature of the
      conversation callback — do this in the same commit as the port, not
      after (rule 8).
- [ ] Port `ScreenLocker` (X11 input grab, password buffer, non-blocking
      poll of the PAM `Future` each frame, signal handling).
- [ ] `sbt nativeImage` succeeds.
- [ ] **Manual smoke test**: trigger the real lock screen, enter a real
      password, confirm both the success and failure/retry paths, confirm
      `sun.misc.Signal`-based SIGINT/SIGTERM handling still unlocks/exits
      cleanly.

## 8. `cli/` (plus the deferred `core/Orchestrator` from step 2)

- [ ] Port `core/Orchestrator` now — every package it calls into
      (`linker`, `desktop`, `wallpaper`, `locker`) is ported by this point.
- [ ] Port the hand-rolled argv parser as-is — **do not** introduce
      decline/scopt/picocli (rule 5).
- [ ] Port `CliTest`.
- [ ] `sbt nativeImage` succeeds.
- [ ] `sbt test` passes.
- [ ] Manual smoke test: every subcommand (`deploy --dry-run`,
      `uninstall --dry-run`) runs and reports the same output as the Java
      version did, on the same machine/state, for a direct before/after
      comparison.
- **Correction found while planning this step**: the original draft of
  this checklist placed `cli/`+`Orchestrator` at step 5, before
  `wallpaper/`/`locker/` (steps 6–8 at the time). That's backwards —
  `Orchestrator.deploy`/`lockScreen`/`toggleWallpaperHud` call directly
  into `WallpaperDaemonManager`, `ScreenLocker`, and `GpuOffloadLauncher`,
  and since sbt only compiles Scala sources that already exist (rule 12),
  `Orchestrator.scala` can't type-check until those packages are ported.
  Reordered so `wallpaper/` and `locker/` (now steps 5–7) land first and
  `cli/`+`Orchestrator` (now step 8) is genuinely last before close-out —
  same reasoning as the step-2 deferral, just caught before execution
  instead of during it.

## 9. Close-out

- [ ] Delete the old `src/main/java` tree once every package above is
      ported and validated (don't delete incrementally mid-migration —
      keep the Java originals as a reference until the matching Scala file
      has passed its own checklist row).
- [ ] Confirm `reachability-metadata.json` is still minimal (two JDK
      entries + one upcall) — any growth versus the pre-migration baseline
      means something introduced incidental reflection; track it down
      before calling the migration done.
- [ ] Full end-to-end pass: `deploy`, `uninstall`, `wallpaper`, `lock`,
      `toggle-hud`, each `--dry-run` variant, on real hardware.
- [ ] Update [dev-environments.md](dev-environments.md) if the sbt setup
      changed anything about the Nix `shell.nix` Scala/sbt lines beyond
      what's already documented there.
