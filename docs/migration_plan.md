# XFCE → Hyprland 3D Overlay Migration Plan

Architecture and rationale for replacing the XFCE app-finder/panel workflow
with a native-image LWJGL overlay launched from `imp`. This captures the
decisions made building `src/main/java/dev/petrolal/dotfiles/overlay/`,
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
- `waybar`, `hyprpaper`, `hyprlock`, `hypridle` as baseline session tooling.

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
| `overlay/NodeLayout.java` | Centered grid layout for `AppEntry` nodes in world space; sorted by name, capped at 24 (no scrolling/pagination yet). |
| `overlay/OverlayWindow.java` | GLFW window + GL context + render loop; owns the camera, renderers, and real node data; mouse click / arrow keys + Enter select and launch a node. |
| `overlay/OverlayCamera.java` | Hand-rolled JOML perspective/view matrices (no libGDX `PerspectiveCamera` — see section 1); also projects world positions to screen pixels (`worldToScreen`) for labels and mouse hit-testing. Pure math, no GL calls. |
| `overlay/Shader.java` | Compiles/links one vertex+fragment GLSL program, caches uniform locations. |
| `overlay/QuadRenderer.java` | Flat-colored quad mesh (VAO/VBO/EBO) + draw call for one app node. No texture sampling yet. |
| `overlay/FontAtlas.java` | Bakes one TrueType font (resolved via `fc-match`, not a hardcoded Nix store path) into a single-channel `stb_truetype` bitmap atlas. |
| `overlay/TextRenderer.java` | Dynamic-VBO text quads sampling a `FontAtlas`, in screen-space orthographic projection. No world-space billboarding yet (Integration work). |
| `overlay/IconLoader.java` | Resolves an icon name/path via a bounded-depth theme-directory search (Chicago95, then hicolor), decodes PNG via `stb_image`, caches by name; returns a checkerboard fallback texture for anything unresolved. |
| `overlay/IconRenderer.java` | Textured world-space quad (same geometry shape as `QuadRenderer`, different shader: samples a texture instead of a flat color). |
| `overlay/NodeLayout.java` | Centered grid layout for `AppEntry` nodes in world space; sorted by name, capped at 24 (no scrolling/pagination yet). |

**Bug found and fixed in `DesktopEntryScanner`:** its `SEARCH_DIRS` was a
hardcoded FHS-style list (`/usr/share/applications`, `/usr/local/share/applications`,
`~/.local/share/applications`). `/usr/share/applications` doesn't exist on
NixOS -- there is no `/usr` -- so the scanner only ever found the handful of
entries under `~/.local/share/applications` and silently missed everything
under `/run/current-system/sw/share/applications`, which is where the actual
system app list lives (Thunar, Chrome, the terminal, ...). This went
unnoticed until `NodeLayout` was wired in and the overlay rendered almost
nothing. Fixed by resolving search dirs from `$XDG_DATA_HOME`/`$XDG_DATA_DIRS`
per the XDG Base Directory spec (falling back to the spec's own default list
only if those env vars are unset), with priority-ordered dedup by desktop-file
id so a higher-priority dir's entry wins over a same-named one further down.

## Explicitly out of scope so far

No font rendering, icon texture loading, `DesktopEntryScanner`-to-render-layer
wiring, mouse/keyboard node selection, or `Cli` subcommand wiring exists yet.
See `docs/checklist.md` for the live status of each.
