# Scala migration — use cases

What the app actually does today, organized by user-facing command and by
module, so the migration can be checked against behavior rather than code
shape. Every item here must still work, unchanged, after the port.

## User-facing CLI commands (`Cli` → `Orchestrator`)

| Command | What it does | Modules involved |
| --- | --- | --- |
| `deploy [flags]` | Symlinks dotfiles into `$HOME`, applies the XFCE/GTK theme and keyboard shortcuts | `linker`, `desktop`, `core` |
| `uninstall [flags]` | Removes all dotfiles symlinks previously created by `deploy` | `linker`, `core` |
| `wallpaper` | Starts the multi-monitor animated 3D (raymarched) OpenGL wallpaper daemon | `wallpaper`, `core` |
| `lock` | Locks the screen: grabs X11 input, shows the password UI over the wallpaper shader, authenticates via PAM | `locker`, `wallpaper` (shares the render pipeline), `core` |
| `toggle-hud` | Toggles an on-screen telemetry HUD (CPU/RAM/audio) on the running wallpaper daemon | `wallpaper` |
| `--dry-run` | Any of the above, reporting what would happen without changing anything | `core`/`domain` (`Result`/`DotfileError` reporting) |

## Module-level use cases

**`domain`** — Typed success/failure propagation used by every other
package (`Result<T>` = `Ok`/`Err`, `DotfileError` = `CommandFailed` /
`FileNotFound` / `IoError` / `ParseError`). Not a feature itself, but the
contract every other module's errors flow through.

**`core`** — Resolves `$HOME`/dotfiles-root paths (`DotfilePaths`), holds
theme constants (`Theme`: name `Imp98`, icon theme, GTK/QT env vars every
spawned process inherits), and sequences the four top-level operations
(`Orchestrator`) — the thing `Cli` calls into.

**`linker`** — Finds dotfile source files under configured prefixes and
creates/removes the symlinks into `$HOME`, with longest-prefix-wins
override semantics and an exclusion list. This is what makes `deploy`/
`uninstall` actually touch the filesystem.

**`desktop`** — Applies the actual desktop configuration: XFCE/GNOME
settings via `xfconf-query`/`gsettings` (`Xfconf`, parallelized across
multiple settings via virtual threads), keyboard shortcut definitions
(`KeyboardShortcuts`), desktop notifications over D-Bus (`DesktopNotifier`),
and restarting live XFCE components so new settings take effect
(`DesktopServices`: panel, xfwm4, xfsettingsd, notifyd, thunar).

**`system`** — Subprocess execution primitives used throughout: capture
stdout/stderr without pipe-buffer deadlock (virtual threads draining both
streams), run-and-discard, and fire-and-forget spawn.

**`wallpaper`** — The animated desktop background: per-monitor viewport/
vsync-paced render loop (`Wallpaper`), raw X11/GLX window and context setup
plus every GL draw call via FFM (`X11Canvas`), GLSL shader compile/link/
hot-reload (`ShaderPipeline`), a second blit pass to the real backbuffer
(`BlitPipeline`), image asset decoding (`ImageLoader`), hybrid-GPU laptop
`nvidia-offload` self-relaunch (`GpuOffloadLauncher`), the daemon-as-external-
process lifecycle (find/stop/start/restart — `WallpaperDaemonManager`), and
background CPU/RAM/audio/mouse telemetry sampling for the HUD
(`WallpaperEvents`).

**`locker`** — The interactive lock screen: reuses the wallpaper's render
pipeline, grabs X11 input, draws the "Windows 98 Enter Password" dialog via
a hand-rolled software rasterizer (`LoginPromptTexture` — deliberately no
`BufferedImage`, to avoid a documented native-image AWT issue), encodes
debug frame dumps to PNG with a dependency-free encoder (`PngWriter`, same
AWT-avoidance reason), and authenticates against the system via PAM calls
on a dedicated worker thread so a slow/hung auth backend never blocks the
60fps render loop (`PamAuthService`, `ScreenLocker`).

## Risk-flagged behavior (pre-existing, not introduced by this migration)

- `ImageLoader` uses `javax.imageio`/`BufferedImage` — the one place in the
  codebase that *doesn't* avoid AWT the way `locker` deliberately does.
  Whether this currently works reliably under native-image is untested;
  worth its own smoke test independent of the language port.
- `sun.misc.Signal` (SIGINT/SIGTERM handling in `ScreenLocker`, `Wallpaper`,
  `GpuOffloadLauncher`) is an internal/unsupported JDK API that happens to
  work today — re-verify under whatever final JDK build is used.

See [scala-migration-checklist.md](scala-migration-checklist.md) for how
these use cases map onto the ordered migration/validation steps, and
[scala-migration-rules.md](scala-migration-rules.md) for the constraints
the port must not violate while doing it.
