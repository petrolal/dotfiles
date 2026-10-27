# XFCE → Hyprland 3D Overlay Migration Plan

Architecture and rationale for replacing the XFCE app-finder/panel workflow
with a native-image LWJGL overlay launched from `imp`. This captures the
decisions made building `../src/main/kotlin`,
plus the underlying OS-level session migration (NixOS config), which is a
separate concern from the overlay app itself.

## 0. OS-level session migration (NixOS)

`nixos/modules/desktop.nix` is pure X11/XFCE today: `services.xserver.enable`,
LightDM, `desktopManager.xfce.enable`, plus real investment in X11-specific
theming (Imp98/Chicago95 LightDM greeter, icon/cursor themes, `QT_STYLE_OVERRIDE`).
Host `primary` also has NVIDIA PRIME hybrid graphics enabled
(`modules/nvidia.nix`) — a known rough edge for wlroots compositors, so this
migration is deliberately staged rather than a one-shot swap, to avoid
losing GUI access if Hyprland/NVIDIA misbehave on first boot.

### Phase 0A (done) — decommission XFCE/X11

`services.xserver.enable`, `desktopManager.xfce.enable`, and the LightDM
block are gone from `desktop.nix`; `hyprland.nix` installs `greetd` +
`tuigreet` as the sole, Wayland-native login manager. The Imp98/Chicago95
theme was dropped entirely rather than ported (decision: no GTK4/Wayland
theming work, start Hyprland/AGS styling from scratch later) — this removed
`Theme.kt`, `Xfconf.kt`, `KeyboardShortcuts.kt`, `DesktopServices.kt`, the
`themes/Imp98` package, `config/icons/Chicago95`, and every `xfce4-*`
package/script/desktop-entry. One correctness fix fell out of this: the
screen locker's PAM service name was `"xfce4-screensaver"`, which only
existed because XFCE's PAM service file happened to be present — it's now
`"imp-lock"`, explicitly declared via `security.pam.services.imp-lock` in
`hyprland.nix`.

### Phase 0B (done) — install Hyprland additively

`nixos/modules/hyprland.nix`, imported from `configuration.nix` alongside
(not instead of) `desktop.nix`:
- `programs.hyprland.enable = true` with `xwayland.enable = true` — adds a
  `wayland-sessions` entry automatically, so LightDM's greeter lists both
  XFCE (X11) and Hyprland (Wayland) as session choices.
- `xdg.portal` with `xdg-desktop-portal-hyprland` — screen share, file
  pickers, etc. for Wayland apps.
- `security.polkit.enable` plus a `hyprpolkitagent` systemd user service —
  XFCE ships its own polkit agent, Hyprland doesn't.
- `hardware.graphics.enable`/`enable32Bit` — required for any Wayland
  compositor; the existing X11/XFCE stack never turned this on.
- NVIDIA/wlroots env vars and kernel params (`nvidia_drm.modeset=1`,
  `nvidia_drm.fbdev=1`, `WLR_NO_HARDWARE_CURSORS=1`,
  `LIBVA_DRIVER_NAME`/`GBM_BACKEND`/`__GLX_VENDOR_LIBRARY_NAME=nvidia`),
  gated behind `config.dotfiles.nvidia.enable` so hosts without the PRIME
  setup are unaffected.
- `ags`, `hyprpaper`, `hyprlock`, `hypridle` as baseline session tooling. AGS
  (Aylur's GTK Shell) stands in for Waybar — the custom 3D overlay launcher
  is a from-scratch replacement for rofi/app-launchers, and AGS is the
  user's choice for the bar/shell layer around it instead of Waybar.

Verified with `nix flake check .` across all three `nixosConfigurations`
entries. **Not yet applied** — applying it means `sudo nixos-rebuild switch`
on the real machine, which is handed to the user to run rather than executed
automatically.

## 1. Stack selection

| Decision | Choice | Why |
|---|---|---|
| Language | Kotlin (migrated from Java) | Modernized codebase completely to Kotlin with Spring Shell integration. Native-image compilation is fully supported via GraalVM Native Build Tools and Kotlin JVM plugin. |
| Graphics | Raw LWJGL 3 + GLFW, not libGDX | `imp` builds as a **GraalVM native-image** binary, not a plain JVM app. libGDX relies on runtime reflection/classpath scanning (`AssetManager`, `Json`) that fights AOT compilation. LWJGL publishes to GraalVM's reachability-metadata-repository and has a proven native-image path. See `build.gradle` dependencies. |
| Math | JOML | Manual `Matrix4f`/perspective projection in place of libGDX's `PerspectiveCamera` — more code, but no framework dependency incompatible with native-image. |
| Native libs | System Mesa/GLFW via `/run/opengl-driver/lib`, not LWJGL's bundled `natives-linux` jars (except `lwjgl-stb`, which has no NixOS system equivalent) | Avoids glibc/ABI mismatches between LWJGL's precompiled `.so` files and NixOS's non-standard dynamic linker layout — the most common source of `UnsatisfiedLinkError` on NixOS. Implemented in `NativeLibraryPaths.kt`. |

## 2. Desktop app node architecture

The initial prototype utilized `DesktopEntryScanner` to crawl desktop directories. In the current Astroland wireframe-orrery architecture, the launcher pivots to a deliberate, high-cohesion set of apps configured directly in `CelestialSystem.kt`. Apps are mapped as planetary nodes (`PlanetNode.kt`) with fixed orbital radii, Keplerian angular velocities, Platonic polyhedron geometries, and launch commands:
- Terminal: `kitty`
- Web Browser: `google-chrome-stable`
- Editor: `idea`
- Telegram: `telegram-desktop`
- File Manager: `thunar`

The data model cleanly decouples node celestial coordinates, hover/selection state, and execution definitions from the GL rendering layer.

## 3. Detached process execution

`ProcessLauncher.kt` submits each launch to a virtual-thread executor and spawns via `ProcessBuilder` with `stdout`/`stderr` set to `Redirect.DISCARD` and `stdin` redirected from a readable `/dev/null`. This guarantees:
- The GL render thread never blocks on fork/exec.
- A launched app outlives the overlay if the overlay closes.
- `Exec=` strings are passed through `sh -c` rather than a hand-rolled
  quoting-aware splitter, since the desktop-entry spec already defines them
  as shell-quoted strings.

**Bug found and fixed:** node selection (click or Enter) silently did
nothing — no error, no launched process, no exit code. Root cause:
`Redirect.DISCARD` is backed by a write-only `/dev/null` file descriptor,
valid for `redirectOutput`/`redirectError` but not `redirectInput`, which
throws `IllegalArgumentException: Redirect invalid for reading: WRITE`.
Because the launch ran inside `ExecutorService.submit()` and the returned
`Future` was never inspected (no `.get()`), that exception vanished
silently — the task started (confirmed via temporary diagnostic logging),
reached `ProcessBuilder.start()`, threw, and the executor swallowed it with
no trace anywhere. Fixed by redirecting stdin from
`Redirect.from(new File("/dev/null"))` instead of `Redirect.DISCARD`.

## 4. Hyprland window layering

**Known limitation:** GLFW (and therefore LWJGL) only implements the
`xdg_wm_base` protocol — it creates ordinary toplevel windows. It does not
bind `zwlr_layer_shell_v1` ("wlr-layer-shell"), which is what gives true
panels/docks (waybar, rofi's Wayland layer mode) their screen-anchored,
non-stacking behavior. As of GLFW 3.4 there is no upstream layer-shell
support, and LWJGL doesn't add its own. So this overlay cannot register as
a real compositor layer surface — it is a regular window that Hyprland is
told to *treat* like one via window rules. Practical consequences:
- It participates in normal workspace/focus semantics unless pinned.
- Anything that specifically respects layer-shell surfaces (some lock
  screens, "show desktop" gestures) won't treat it specially.
- If true panel-level layering is needed later, the only path is raw JNI
  bindings to `wlr-layer-shell-protocol` — out of scope for now.

Until then, approximate panel behavior with Hyprland window rules in
`hyprland.conf` (set the GLFW window class via
`glfwWindowHintString(GLFW_X11_CLASS_NAME, "imp-overlay")` / the Wayland
app-id equivalent so these match):

```
windowrulev2 = float, class:^(imp-overlay)$
windowrulev2 = fullscreen, class:^(imp-overlay)$
windowrulev2 = noborder, class:^(imp-overlay)$
windowrulev2 = noshadow, class:^(imp-overlay)$
windowrulev2 = norounding, class:^(imp-overlay)$
windowrulev2 = pin, class:^(imp-overlay)$
windowrulev2 = stayfocused, class:^(imp-overlay)$
```

No rule is needed to keep it "below system popups" — Wayland's own
stacking order already renders layer-shell `top`/`overlay` surfaces (e.g.
notification daemons) above ordinary toplevels regardless of pinning.

## 5. NixOS-specific dependency paths

| Path | Purpose |
|---|---|
| `/run/opengl-driver/lib` | Mesa/GL driver symlink NixOS maintains; `NativeLibraryPaths` points `org.lwjgl.librarypath` here. |
| `/run/opengl-driver-32/lib` | 32-bit counterpart, checked as a fallback. |
| `LWJGL_SYSTEM_LIBRARY_PATH` env var | Override for the above, if the Nix-managed path ever changes. |
| `gradle/libs.versions.toml` | `lwjgl` (3.3.6) and `joml` (1.10.8) version pins. |

**Correction found while testing milestone 2 (quad renderer):** the original
assumption that only `lwjgl-stb` needs its bundled `natives-linux` jar was
wrong. Every LWJGL module ships its *own* JNI glue library
(`liblwjgl.so`, `liblwjgl-glfw.so`, `liblwjgl-opengl.so`) — that's LWJGL's
compiled bridge code, not a Mesa/driver library, so there's no NixOS system
equivalent for it either, same as `lwjgl-stb`. `/run/opengl-driver/lib` only
ever covered the *driver* libraries (`libGL`, `libglfw`) those bridge
libraries `dlopen()` at runtime. Fixed by adding `runtimeOnly(...::natives-linux)`
for `lwjgl`, `lwjgl-glfw`, and `lwjgl-opengl` in `build.gradle`, matching the
pattern already used for `lwjgl-stb`.

**X11/GLX only, not fixed, and not a blocker:** running the overlay as a
plain X11 GLFW window on this host's NVIDIA PRIME setup (Intel iGPU drives
the display, NVIDIA is offload-only — confirmed via `lspci`: `i915` is the
display controller, `nvidia` is the discrete GPU) fails with `GLX: Failed to
create context: GLXBadFBConfig`, because `/run/opengl-driver/lib` resolves to
the NVIDIA driver's `libGL.so` here, and loading it directly for a GLX
context against the Intel-driven X server's FBConfigs doesn't work without
GLVND's vendor dispatch (`libGLX.so.0`/`libOpenGL.so.0`) and PRIME
render-offload env vars. **Confirmed this does not reproduce under
Hyprland/Wayland** — live-tested via `quick-overlay` logged into Hyprland on
this same host: window opens, renders the quad-renderer's demo row, closes
cleanly, cursor unaffected. Since X11 is not the deployment target, this is
left undiagnosed rather than chased further.

**Wayland native-lib gap, fixed:** the same class of problem as the
LWJGL-natives correction above, but for GLFW's *Wayland* backend instead of
X11: its bundled native `dlopen()`s `libwayland-client`/`libwayland-cursor`/
`libxkbcommon` by soname, and nothing exposed them on a stable path. Fixed by
adding `wayland` and `libxkbcommon` to `hyprland.nix`'s
`environment.systemPackages` (lands at `/run/current-system/sw/lib`) and
pointing `LD_LIBRARY_PATH` there from the `quick-overlay` Gradle task — this
has to be `LD_LIBRARY_PATH`, not `org.lwjgl.librarypath`/`NativeLibraryPaths`,
because that property only covers LWJGL's own `System.load()` calls, not a
native library's internal `dlopen()`s by soname. **This same requirement will
apply to the eventual native-image `imp overlay` binary** once the `Cli`
subcommand and native-image build exist (see checklist) — whatever launches
that binary (desktop file, Hyprland keybind, wrapper script) will need
`LD_LIBRARY_PATH` set the same way; there's no Gradle task to lean on there.

**Second bug found and fixed in `OverlayWindow`:** on Wayland,
`glfwShowWindow` fires the framebuffer-size callback *synchronously* (an
immediate compositor configure event) rather than later on the event loop.
The original code called `glfwShowWindow` before `GL.createCapabilities()`
and before constructing `camera`/`quadRenderer`, so the callback's
`glViewport` call aborted the JVM (`FATAL ERROR in native method: ... No
context is current`) and would have NPE'd on `camera` right after. Fixed by
moving `GL.createCapabilities()` and the camera/quadRenderer construction
before the framebuffer-size callback is registered, and registering that
callback right before `glfwShowWindow`.

**Gradle bug found and fixed:** `quick-overlay` (and `quick-deploy`, same
issue) are `JavaExec` tasks run purely for side effects -- launching a GUI
app, deploying symlinks -- with no declared outputs. Gradle can mark a task
with no declared outputs `UP-TO-DATE` and skip it entirely on a re-run with
an unchanged classpath/args, which is exactly what happened: `./gradlew
quick-overlay` reported `BUILD SUCCESSFUL` in under a second, printed nothing,
and never actually started the JVM -- so node selection appeared to do
nothing (no window, no error, no diagnostic output) when the real problem was
that the task plain didn't run. Fixed with `outputs.upToDateWhen { false }`
on both tasks. Caught while debugging node selection seeming to silently fail
to launch anything: selection itself worked correctly (confirmed by adding a
temporary `[overlay] launching ...` log line and running the app's command
directly outside the overlay, which worked fine), but the overlay process
behind it had, twice in a row, simply never started.

## Module responsibilities

| File | Responsibility |
|---|---|
| `overlay/NativeLibraryPaths.kt` | Routes LWJGL native loading to system Mesa/GLFW before any `org.lwjgl.*` class loads. |
| `overlay/CelestialSystem.kt` | Coordinates planetary app nodes, orbit radii, speeds, and state. |
| `overlay/PlanetNode.kt` | Immutable data and dynamic hit-test state for one planetary app node. |
| `overlay/PlanetRenderer.kt` | Dual-pass rendering for Platonic solids: translucent facet body + glowing wireframe edges. |
| `overlay/PolyhedronMesh.kt` | Generates Platonic solid meshes (tetrahedron, octahedron, icosahedron) with unit-length vertices. |
| `overlay/SphereMesh.kt` | Procedural UV sphere mesh with position, normal, and UV attributes. |
| `overlay/StarRenderer.kt` | Central glowing star with CPU-reactive bioluminescent pulse. |
| `overlay/StarfieldRenderer.kt` | 3D background starfield backdrop. |
| `overlay/OrbitRingMesh.kt` / `OrbitRingRenderer.kt` | Generates and renders glowing orbital ribbon meshes with click-ripple animation decay. |
| `overlay/VectorBeamRenderer.kt` | Dynamic vector rays connecting the central star to planetary nodes. |
| `overlay/HudLabelRenderer.kt` | Screen-space HUD text labels with world-to-screen connector lines tracking nodes. |
| `overlay/FontAtlas.kt` / `TextRenderer.kt` | TTF baking via `stb_truetype` and dynamic-VBO text rendering. |
| `overlay/Raycaster.kt` | Ray-sphere intersection math for mouse hover and click hit-testing. |
| `overlay/OverlayCamera.kt` | Perspective/view matrix calculations and unproject math (NDC to world ray). |
| `overlay/PostProcessPipeline.kt` | Offscreen FBO post-processing: chromatic aberration, breathing vignette, CRT scanlines, and load tint. |
| `overlay/OverlayWindow.kt` | GLFW window loop, event handling, and pipeline orchestration. |
| `overlay/ProcessLauncher.kt` | Detached application launcher off the GL render thread. |
| `telemetry/SystemVitals.kt` | Real-time `/proc` metrics (CPU, RAM) sampling. |
| `locker/ScreenLocker.kt` | Fullscreen OpenGL screen locker. |
| `locker/PamAuthService.kt` | Asynchronous PAM authentication using Project Panama FFM against `imp-lock`. |
| `locker/LoginPromptTexture.kt` | Renders graphical login prompt and password dots. |

## 6. Native-image build

`imp overlay` (see the `Cli`/`Orchestrator` wiring above) now builds as part
of the same `nativeCompile` as every other command -- there is one `imp`
binary, not a separate overlay executable.

**Bug found and fixed: LWJGL's own JNI bridge libraries were silently
dropped from the image.** `./gradlew nativeCompile` succeeded and produced a
binary, but `imp overlay` failed immediately with `[LWJGL] Platform/
architecture mismatch detected for module: org.lwjgl` /
`Failed to locate library: liblwjgl.so`. Root cause: `liblwjgl.so`,
`libglfw.so`, `liblwjgl_opengl.so`, and `liblwjgl_stb.so` are not system
driver libraries -- they're LWJGL's own compiled JNI glue, shipped as plain
classpath resources inside each module's `natives-linux` jar (e.g.
`linux/x64/org/lwjgl/liblwjgl.so`), extracted to a temp dir at runtime by
LWJGL's `SharedLibraryLoader`. Native-image's closed-world analysis only
bundles classpath resources it can prove are reachable, and nothing in this
app's code *references* those resource paths directly -- `SharedLibraryLoader`
reaches them by string-built path, invisible to static analysis. Checked
first per `docs/migration_plan.md` section 1's assumption that this would be
covered by the GraalVM reachability-metadata-repository: it is **not** --
there is no `org.lwjgl` entry in that repository at all (confirmed by
inspecting the downloaded `graalvm-reachability-metadata-*-repository.zip`),
and the LWJGL jars themselves ship no `META-INF/native-image/*` metadata
either (confirmed by listing the natives-linux jar contents). Fixed by hand,
with explicit `resources` glob entries for exactly these four paths added to
this project's own
`src/main/resources/META-INF/native-image/dev.petrolal/dotfiles-deployer/reachability-metadata.json`
(native-image auto-discovers and merges any `META-INF/native-image/**/*.json`
found on the classpath -- no build-script wiring needed). Verified by image
heap growing from 10 to 14 embedded resources and ~2MiB in the build output,
and `imp overlay` getting past library loading on the next run.

**Confirmed (the hard way) that `org.lwjgl.librarypath` resolution does
*not* work identically from the native binary, in two separate ways:**

1. The same Wayland native-lib gap documented in section 5 for `quick-overlay`
   applies unchanged to the native binary: GLFW's Wayland backend `dlopen()`s
   `libwayland-client`/`libxkbcommon` by soname, and nothing provides that for
   a bare `./bin/imp overlay` invocation outside Gradle's `JavaExec`
   environment block. Reproduced directly: `imp overlay` fails with `Wayland:
   Failed to load libwayland-client` until `LD_LIBRARY_PATH` includes
   `/run/current-system/sw/lib` (same value `quick-overlay`'s Gradle task
   sets). **Not yet wired anywhere for the shipped binary** -- whatever
   finally launches `imp overlay` for real (a `.desktop` file or a Hyprland
   keybind, see the Hyprland integration checklist) will need to export this
   same `LD_LIBRARY_PATH` first. A desktop file's `Exec=` line can't set env
   vars directly; use `env LD_LIBRARY_PATH=... imp overlay` or a tiny wrapper.

2. **New native-image-specific bug, found, not fixed, not a blocker yet:**
   with `LD_LIBRARY_PATH` set correctly, `glfwInit()` now succeeds (progress
   over #1), but the process segfaults a few hundred milliseconds later --
   consistently, across repeated runs -- with `si_addr` a small offset
   (`0x40`) past a null-like base, on a thread GraalVM's crash handler
   describes as "an unattached thread" with "no full set of VM thread
   locals". That description matches a known class of Native Image problem:
   some native library calling back into LWJGL/JNI-registered code from a
   thread the Substrate VM runtime was never told to attach, so the callback
   dereferences isolate-thread state that doesn't exist on that thread. This
   is specific to native-image: the existing `quick-overlay` Gradle task
   (plain JVM) is live-verified working under this exact Wayland session
   (see section 5). **Tried forcing `GLFW_PLATFORM=x11`** (to test whether
   this is Wayland-specific, the way section 5's GLX/NVIDIA-PRIME issue is)
   -- same crash, byte-for-byte identical faulting instruction, so this is
   *not* about which GLFW backend is in use; something else in the
   native-image/LWJGL/GLFW interaction is at fault, independent of windowing
   protocol. Not chased further given the investigation already spent --
   the next diagnostic step, if picked back up, would be building with
   `-H:+PreserveFramePointer` plus a native debugger (`gdb`, not installed
   in this environment) attached to a core dump
   (`-XX:-InstallSegfaultHandler`) to get a real symbolized backtrace, since
   the crash handler's own output only identifies the faulting thread as
   "unattached" with no further symbol info. Until resolved, **`imp overlay`
   is only reliably usable via `quick-overlay` during development, not as
   the shipped native binary**, which is a real limitation on top of the
   Hyprland integration checklist below, not just a missing keybind.

## 7. Astroland wireframe-orrery 3D launcher

The launcher architecture transitioned from flat 2D icon grids and static solar-system planes to an interactive, full-3D **Astroland wireframe-orrery launcher** (`imp overlay`):

### Geometric Platonic Solid Nodes
Apps are represented as geometric Platonic solids (`PolyhedronMesh.kt`) rather than bitmap icons:
- **Tetrahedron**: 4 triangular faces.
- **Octahedron**: 8 triangular faces.
- **Icosahedron**: 20 triangular faces.
Each mesh generates unit-length vertices with outwards-facing winding and vertex normals.

### Dual-Pass Node Rendering (`PlanetRenderer.kt`)
Each planetary node is rendered in two passes:
1. **Facet body**: Translucent obsidian interior with an amber Fresnel rim glow.
2. **Wireframe edges**: Glowing vector line overlay highlighting the polyhedron's edges.

### Celestial Orrery & Telemetry
- **Central Star (`StarRenderer.kt`)**: A central glowing star pulsating dynamically based on system CPU metrics sampled via `SystemVitals.kt`.
- **Vector Beams (`VectorBeamRenderer.kt`)**: Glowing `GL_LINES` rays connecting the star core to each planetary node, flaring when hovered.
- **Orbital Ribbons (`OrbitRingMesh.kt`, `OrbitRingRenderer.kt`)**: Circular ring geometry with animated click-ripple decay propagation when a node is triggered.
- **Starfield (`StarfieldRenderer.kt`)**: Procedural 3D background starfield providing depth.

### Raycasting & HUD Interaction
- **Ray-Sphere Hit Testing (`Raycaster.kt`)**: The mouse cursor is unprojected through the inverse view-projection matrix of `OverlayCamera.kt` into a 3D world ray (`OverlayCamera.unproject`), testing for intersection against the bounding spheres of orbiting nodes.
- **HUD Connector Lines & Labels (`HudLabelRenderer.kt`)**: Screen-space text labels with dynamic connector lines tracking the 3D world positions of hovered and selected planetary bodies.

### Post-Processing Pipeline (`PostProcessPipeline.kt`)
The entire 3D scene renders to an offscreen Framebuffer Object (FBO) backed by `GL_RGBA8` color and `GL_DEPTH24_STENCIL8` renderbuffers. The post-processing fragment shader applies:
- Radial chromatic aberration
- Breathing vignette
- Subtle CRT scanlines
- Telemetry-driven color tinting based on CPU vitals

HUD text and 2D overlay labels are drawn directly to the default framebuffer afterwards, ensuring crisp readability.

## 8. Screen Locker (`imp lock`) & PAM Integration

`imp` includes a standalone, OpenGL-rendered screen locker designed for Wayland / Hyprland sessions:
- **Foreign Function & Memory (FFM) API**: Implemented in `PamAuthService.kt` via Java 25 / Kotlin Project Panama, dynamically loading `libpam.so` without external JNI C glue libraries.
- **`imp-lock` PAM Service**: Configured via `security.pam.services.imp-lock = { };` in `nixos/modules/hyprland.nix`. This delegates authentication to the standard setuid `unix_chkpwd` helper.
- **Asynchronous Execution**: PAM conversation callbacks run on dedicated virtual threads so the OpenGL render thread never stutters during password verification.
- **Login Prompt UI (`LoginPromptTexture.kt`)**: Real-time visual feedback with masked password glyphs, failure indicators, and timeout resets.

## 9. Kotlin CLI & Deployment Architecture

- **Spring Shell & Kotlin**: The CLI layer was rewritten in Kotlin (`ImpApplication.kt`, `DotfilesCommands.kt`, `Cli.kt`), enabling interactive prompt mode and subcommands (`deploy`, `uninstall`, `wallpaper`, `overlay`, `lock`, `toggle-hud`).
- **Decommissioned XFCE Reload Layer**: The legacy XFCE settings applicator (`Xfconf.kt`), desktop reloaders (`DesktopServices.kt`), and CLI options (`--no-xfconf`, `--no-reload`) were removed. `imp deploy` now focuses purely on deterministic symlink deployment and 3D wallpaper management.
- **Rapid JVM Execution**: Tasks `quick-deploy` and `quick-overlay` allow instant iteration during development without waiting for GraalVM AOT compilation.
