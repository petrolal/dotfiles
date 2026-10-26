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
