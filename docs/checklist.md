# Overlay Migration Checklist

Tracks progress toward replacing the XFCE app-finder/panel with the
Hyprland 3D overlay. See `docs/migration_plan.md` for the architecture
behind each item.

## NixOS session migration

- [x] `nixos/modules/hyprland.nix` — Hyprland + portals + polkit + NVIDIA/wlroots env vars, added additively alongside `desktop.nix`
- [x] `nix flake check .` passes for all three `nixosConfigurations` entries
- [ ] Run `sudo nixos-rebuild switch --flake nixos` on `primary` and confirm Hyprland appears as a LightDM session choice
- [ ] Log into Hyprland, confirm NVIDIA PRIME offload works (no cursor corruption, GL apps render)
- [ ] Use Hyprland day-to-day long enough to trust it as the daily driver
- [ ] **Phase 0A (deferred):** remove `services.xserver.enable`/XFCE/LightDM from `desktop.nix`, port Imp98/Chicago95 theming to GTK4/Wayland, drop XFCE-only packages

## Foundation (done)

- [x] Decide stack: Java + raw LWJGL3/GLFW + JOML, no libGDX (native-image fit)
- [x] Add LWJGL BOM, core/glfw/opengl, JOML, and `lwjgl-stb` (+ natives-linux) to `gradle/libs.versions.toml` / `build.gradle`
- [x] `NativeLibraryPaths` — route native loading to `/run/opengl-driver/lib`
- [x] `AppEntry` — app-node data record
- [x] `DesktopEntryScanner` — concurrent `.desktop` crawler with field-code stripping
- [x] `ProcessLauncher` — detached process spawn off the render thread
- [x] `./gradlew compileJava` passes with the new dependencies
- [x] Hyprland `windowrulev2` snippet drafted, layer-shell limitation documented

## Rendering pipeline (not started)

- [ ] GLFW window creation + OpenGL context (core profile, version pinned)
- [ ] Set `GLFW_X11_CLASS_NAME` / Wayland app-id to `imp-overlay` so Hyprland rules match
- [ ] Render loop (clear, swap buffers, poll events) on its own thread, never blocking on `ProcessLauncher`
- [ ] JOML-based perspective camera + view/projection matrices
- [ ] Basic mesh/quad renderer for app nodes
- [ ] Font rendering via `lwjgl-stb` (stb_truetype) for app labels
- [ ] Icon texture loading (theme lookup + fallback for missing icons)

## Integration (not started)

- [ ] Wire `DesktopEntryScanner` output into the render layer's node layout
- [ ] Wire node selection (mouse/keyboard) to `ProcessLauncher.launch(...)`
- [ ] Add a `Cli` subcommand (e.g. `imp overlay`) to launch the overlay
- [ ] Decide overlay exit behavior (Escape closes without killing launched children)

## Native-image build (not started)

- [ ] Verify `nativeCompile` succeeds with LWJGL on the classpath
- [ ] Add JNI/resource reachability config if native-image's static analysis misses LWJGL call sites (check against the GraalVM reachability-metadata-repository first — may not be needed)
- [ ] Confirm `org.lwjgl.librarypath` resolution works identically from the native binary, not just `./gradlew run`

## Hyprland integration (not started)

- [ ] Add the `windowrulev2` block from `docs/migration_plan.md` to `hyprland.conf`
- [ ] Bind a Hyprland keybind to launch `imp overlay` (mirrors the current `Shift+Super+p` → `imp toggle-hud` pattern in `xfce4-keyboard-shortcuts.xml`)
- [ ] Manual test: overlay opens fullscreen, borderless, pinned, doesn't fight workspace switching
- [ ] Decide whether true `wlr-layer-shell` binding is ever needed, given the documented limitation
