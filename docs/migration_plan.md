# XFCE → Hyprland 3D Overlay Migration Plan

Architecture and rationale for replacing the XFCE app-finder/panel workflow
with a native-image LWJGL overlay launched from `imp`. This captures the
decisions made building `src/main/java/dev/petrolal/dotfiles/overlay/`.

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
executor and spawns via `ProcessBuilder` with `stdout`/`stderr`/`stdin` all
set to `Redirect.DISCARD`. This guarantees:
- The GL render thread never blocks on fork/exec.
- A launched app outlives the overlay if the overlay closes.
- `Exec=` strings are passed through `sh -c` rather than a hand-rolled
  quoting-aware splitter, since the desktop-entry spec already defines them
  as shell-quoted strings.

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

## Module responsibilities

| File | Responsibility |
|---|---|
| `overlay/NativeLibraryPaths.java` | Routes LWJGL native loading to system Mesa/GLFW before any `org.lwjgl.*` class loads. |
| `overlay/AppEntry.java` | Immutable data record for one launchable app — the unit passed to the (not-yet-built) render layer. |
| `overlay/DesktopEntryScanner.java` | Concurrent `.desktop` file crawler → `List<AppEntry>`. No GL/rendering dependency. |
| `overlay/ProcessLauncher.java` | Detached app launch, off the render thread. |

## Explicitly out of scope so far

No GL render loop, window creation, camera, font rendering, or `Cli`
subcommand wiring exists yet — this plan and the files above are the
foundation layer only, as scoped in the prior session phases.
