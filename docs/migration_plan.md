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

### Phase 0A (deferred) — decommission XFCE/X11

Not done yet, on purpose. Once Hyprland + the overlay have been used
day-to-day and confirmed stable on this NVIDIA PRIME host, do a second pass:
remove `services.xserver.enable`, `desktopManager.xfce.enable`, and the
LightDM block from `desktop.nix`; migrate the Imp98/Chicago95 theming to its
GTK4/Wayland equivalent; drop XFCE-only packages (`xfce4-whiskermenu-plugin`).

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
| Language | Java (not Kotlin) | Repo is 100% Java already; mixing JVM languages buys nothing here and complicates the GraalVM native-image reachability analysis for no benefit. |
| Graphics | Raw LWJGL 3 + GLFW, not libGDX | `imp` builds as a **GraalVM native-image** binary, not a plain JVM app. libGDX relies on runtime reflection/classpath scanning (`AssetManager`, `Json`) that fights AOT compilation. LWJGL publishes to GraalVM's reachability-metadata-repository and has a proven native-image path. See `build.gradle` dependencies. |
| Math | JOML | Manual `Matrix4f`/perspective projection in place of libGDX's `PerspectiveCamera` — more code, but no framework dependency incompatible with native-image. |
| Native libs | System Mesa/GLFW via `/run/opengl-driver/lib`, not LWJGL's bundled `natives-linux` jars (except `lwjgl-stb`, which has no NixOS system equivalent) | Avoids glibc/ABI mismatches between LWJGL's precompiled `.so` files and NixOS's non-standard dynamic linker layout — the most common source of `UnsatisfiedLinkError` on NixOS. Implemented in `NativeLibraryPaths.java`. |

## 2. Desktop metadata extraction

`DesktopEntryScanner.java` crawls `/usr/share/applications`,
`/usr/local/share/applications`, and `~/.local/share/applications`, one
virtual thread per `.desktop` file (`Executors.newVirtualThreadPerTaskExecutor()`).
For each file it extracts `Name`, `Exec`, `Icon`, `Categories` from the
`[Desktop Entry]` section, skips entries marked `NoDisplay=true` or
`Hidden=true`, and strips desktop-entry-spec field codes (`%f`, `%U`, `%i`,
`%c`, `%k`, `%v`, `%m`) from `Exec` since the launcher has no file/URL
context to substitute. Output is `AppEntry` — a flat record (`id`, `name`,
`exec`, `icon`, `categories`) decoupled from rendering so the scanner has no
dependency on LWJGL/GL state.

## 3. Detached process execution

`ProcessLauncher.java` submits each launch to its own virtual-thread
executor and spawns via `ProcessBuilder` with `stdout`/`stderr` set to
`Redirect.DISCARD` and `stdin` redirected from a readable `/dev/null`. This
guarantees:
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
| `overlay/NativeLibraryPaths.java` | Routes LWJGL native loading to system Mesa/GLFW before any `org.lwjgl.*` class loads. |
| `overlay/AppEntry.java` | Immutable data record for one launchable app — the unit passed to the render layer. |
| `overlay/DesktopEntryScanner.java` | Concurrent `.desktop` file crawler → `List<AppEntry>`. No GL/rendering dependency. |
| `overlay/ProcessLauncher.java` | Detached app launch, off the render thread; wired to node selection in `OverlayWindow`. |
| `overlay/OrbitLayout.java` | Assigns `AppEntry` nodes to concentric category rings (first-match-wins against freedesktop.org `Categories=`) and computes each node's live (x, y) from a fixed radius/baseAngle/angularVelocity and elapsed time -- real-time orbital motion, not a static grid (see below). Pure math, no GL/LWJGL dependency; covered by `OrbitLayoutTest`. |
| `overlay/OverlayWindow.java` | GLFW window + GL context + render loop; owns the camera, renderers, and real node data; mouse click / arrow keys + Enter select and launch a node. |
| `overlay/OverlayCamera.java` | Hand-rolled JOML perspective/view matrices (no libGDX `PerspectiveCamera` — see section 1); also projects world positions to screen pixels (`worldToScreen`) for labels and mouse hit-testing. Pure math, no GL calls. |
| `overlay/Shader.java` | Compiles/links one vertex+fragment GLSL program, caches uniform locations. |
| `overlay/QuadRenderer.java` | Flat-colored quad mesh (VAO/VBO/EBO) + draw call for one app node. No texture sampling yet. |
| `overlay/FontAtlas.java` | Bakes one TrueType font (resolved via `fc-match`, not a hardcoded Nix store path) into a single-channel `stb_truetype` bitmap atlas. |
| `overlay/TextRenderer.java` | Dynamic-VBO text quads sampling a `FontAtlas`, in screen-space orthographic projection. No world-space billboarding yet (Integration work). |
| `overlay/IconLoader.java` | Resolves an icon name/path via a bounded-depth theme-directory search (Chicago95, then hicolor), decodes PNG via `stb_image`, caches by name; returns a checkerboard fallback texture for anything unresolved. |
| `overlay/IconRenderer.java` | Textured world-space quad (same geometry shape as `QuadRenderer`, different shader: samples a texture instead of a flat color). |

**Bug found and fixed in `DesktopEntryScanner`:** its `SEARCH_DIRS` was a
hardcoded FHS-style list (`/usr/share/applications`, `/usr/local/share/applications`,
`~/.local/share/applications`). `/usr/share/applications` doesn't exist on
NixOS -- there is no `/usr` -- so the scanner only ever found the handful of
entries under `~/.local/share/applications` and silently missed everything
under `/run/current-system/sw/share/applications`, which is where the actual
system app list lives (Thunar, Chrome, the terminal, ...). This went
unnoticed until the layout code was wired in and the overlay rendered almost
nothing. Fixed by resolving search dirs from `$XDG_DATA_HOME`/`$XDG_DATA_DIRS`
per the XDG Base Directory spec (falling back to the spec's own default list
only if those env vars are unset), with priority-ordered dedup by desktop-file
id so a higher-priority dir's entry wins over a same-named one further down.

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

## 7. Orbital ("solar system") layout

`OrbitLayout.java` replaced the earlier static centered grid (`NodeLayout`,
removed). Apps no longer sit at fixed grid positions; they orbit a fixed
center in real time, grouped into concentric rings by category.

**Ring assignment.** First-match-wins scan of each `AppEntry`'s
freedesktop.org `Categories=` list against a small ordered list
(`Development` -> ring 0, `Network` -> ring 1, `AudioVideo` -> ring 2);
anything matching none of those lands in a final catch-all ring. This is
deliberately coarse -- three named rings plus a catch-all, not one ring per
freedesktop main category -- to keep the rings visually distinct instead of
producing a dozen near-identical radii for categories with only one or two
installed apps.

**Position math.** Each node gets fixed orbital parameters once
(`ring`, `radius`, `baseAngle`, `angularVelocity`) in `OrbitLayout.assign`;
its position at any instant is pure trigonometry from those plus elapsed
time, recomputed every frame in `OverlayWindow.loop()`:

```
radius(ring)          = INNER_RADIUS + ring * RING_SPACING
angularVelocity(ring)  = BASE_ANGULAR_VELOCITY / sqrt(1 + ring)
angle(t)               = baseAngle + angularVelocity * t
x(t)                   = radius * cos(angle(t))
y(t)                   = radius * sin(angle(t))
```

`baseAngle` for the i-th of `count` name-sorted apps in a ring is
`2*pi*i/count`, evenly spacing that ring's apps around it. Outer rings orbit
slower than inner ones (`angularVelocity` falls with ring index) -- loosely
modeled on Kepler's third law (orbital period grows with radius), not a
physically-accurate simulation; just enough for outer rings to visibly lag
inner ones instead of the whole layout spinning as one rigid disc. `t` is
`glfwGetTime()` (seconds since `glfwInit`), not a hand-rolled clock.

A decorative, non-interactive "sun" quad renders at the origin every frame
-- it isn't a node, has no entry in `OrbitLayout`, and never participates in
selection or hit-testing.

**Keyboard navigation reworked for rings, not a grid.** Left/right now move
to the adjacent node *within the same ring* (wrapping around); this stays
valid at any elapsed time because every node in a ring shares one
`angularVelocity`, so their relative order along the ring never changes even
as they all keep moving. Up/down jump to the nearest-`baseAngle` node in the
next non-empty ring in/out -- compared against each node's fixed
`baseAngle`, not its live angle, since rings orbit at different speeds and
comparing live angles would make "straight up" drift away from what it
looked like a moment ago. This is a documented approximation, not a precise
hit test; it's good enough for keyboard navigation, which only needs "the
next thing that way," not an exact spatial match.

Live-verified under Hyprland via `quick-overlay`: ran to completion with no
exceptions across repeated manual runs. Not independently screenshot-verified
in this environment (no Wayland screenshot tool -- `grim`/`slurp` -- was
available to capture and inspect a frame); `OrbitLayoutTest` instead covers
the pure layout math (ring assignment, relative angular velocity, the
position formula, and the `MAX_NODES` cap) without needing a GL context.

## Explicitly out of scope so far

No font rendering, icon texture loading, or `DesktopEntryScanner`-to-render-layer
wiring is missing anymore -- those are done (see the module table above).
What's left is making the shipped native `imp overlay` binary actually launch
cleanly under real Wayland (the segfault in section 6) and the Hyprland-side
wiring (window rules, keybind) in `docs/checklist.md`. Ring-guide circles
(a visible outline per orbit, not just the nodes on it) and letting the sun
be an interactive "home"/settings node are both deferred -- neither changes
the launch-an-app core loop, and the sun's role as a pure visual anchor was
a deliberate scoping choice (see section 7), not an oversight.
