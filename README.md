# Dotfiles

NixOS system configuration and Wayland desktop powered by [Hyprland](https://hyprland.org/),
featuring a Kotlin port of [astroland_dots](https://codeberg.org/zacoons/astroland_dots) — a
ship/moon arcade scene where real desktop windows become physics bodies — plus a unified Kotlin
CLI/deployer and an integrated screen locker.

## Features

- **Hyprland Desktop**: Minimal, modern Wayland environment managed via Lua
  (`config/hypr/hyprland.lua`), with [greetd](https://man.sr.ht/~kennylevinsen/greetd/)/
  [tuigreet](https://github.com/apognu/tuigreet) as the login manager.
- **Astroland, the real Hyprland plugin** (`astroland/plugin/`, loaded via `hl.plugin.load(...)`):
  a genuine C++ Hyprland plugin whose actual game logic lives in Kotlin
  (`astroland/engine/`, compiled to a native `.so` and linked in). Flying your ship into a real
  window and shooting it is a real `general.layout = "astrolayout"` tiled algorithm, not a
  simulation. See [docs/astroland_port_handoff.md](docs/astroland_port_handoff.md) for the full
  architecture, what's faithfully ported vs. simplified, and **known risks before you load it**.
- **Astroland overlay windows** (`astroland/overlay/`, standalone JVM/LWJGL, no plugin required):
  - `imp overlay` — the same ship/moon game as a standalone window (no real-window integration).
  - `imp screensaver` — idle screensaver (bouncing DVD logo + hold music), triggered by `hypridle`.
  - `imp infooverlay` — battery %/clock HUD, autostarted.
  - `imp notifsoverlay` — a **real** `org.freedesktop.Notifications` D-Bus daemon + popup UI,
    autostarted.
- **Screen Locker**: PAM authentication via Java's Foreign Function & Memory API
  (`src/main/kotlin/dev/petrolal/dotfiles/locker/`) — currently wired to no UI of its own; actual
  session locking is `hyprlock` (see the port handoff doc for why).
- **Unified Kotlin CLI & Deployer** (`imp`): Kotlin + Spring Shell + GraalVM Native Image.
  Deterministic dotfiles symlink management plus all of the above as subcommands.

## From-Scratch Install

On a fresh NixOS installation:

```bash
git clone <repo-url> ~/dotfiles
cd ~/dotfiles
./bootstrap.sh
```

The bootstrap script only needs what base NixOS provides (`sh`, `git`, `sudo`, `nixos-rebuild`). It will:

1. Link the flake into `/etc/nixos`
2. Rebuild NixOS (installs Hyprland, greetd, audio, GPU drivers, tools)
3. Build the native `imp` binary (Gradle + GraalVM native-image)
4. Deploy dotfiles

Log in to Hyprland for all changes to take effect. The real Hyprland plugin (`astroland/plugin/`)
is **not** built by `bootstrap.sh` -- it needs its own build step and real-headers toolchain, see
the port handoff doc.

## Day-to-Day Usage

### Gradle Tasks

| Command | What it does |
|---|---|
| `./gradlew deploy` | Re-deploy dotfiles symlinks (rebuilds the native image first) |
| `./gradlew quick-deploy` | Fast JVM execution of deploy without rebuilding the native image |
| `./gradlew test` | Run all Kotlin unit tests |
| `./gradlew nativeCompile` | Compile `imp` to a standalone GraalVM native executable (`bin/imp`) |
| `./gradlew nix-switch` | Rebuild NixOS flake configuration (`sudo nixos-rebuild switch --flake ./nixos`) |
| `./gradlew system-install` | Rebuild NixOS + redeploy dotfiles |
| `./gradlew dry-run` | Preview what deploy would change without touching the filesystem |
| `./gradlew uninstall` | Safely remove all deployed dotfiles symlinks |
| `./gradlew tasks` | Show all available Gradle tasks |

### Imp CLI (`bin/imp`)

```bash
imp deploy [-n/--dry-run] [-q/--quiet]
imp uninstall [-n/--dry-run] [-q/--quiet]
imp version
```

Note: `bin/imp` is a GraalVM native-image build that can be rebuilt with `./gradlew nativeCompile`.
For quick CLI deployment during development, `./gradlew quick-deploy` runs directly on the JVM.

### Keybindings

See [docs/keybindings.md](docs/keybindings.md) for the full, current list (generated from
`config/hypr/hyprland.lua`'s `hl.bind(...)` calls) -- it covers windows, workspaces, screenshots,
multimedia keys, and controls while the astroland plugin is loaded.

### Shell Integration

Default login shell is [fish](https://fishshell.com/) (`nixos/modules/users.nix`), configured in `config/fish/`.

## Project Structure

```
dotfiles/
├── bootstrap.sh                 # First-time setup script
├── build.gradle / settings.gradle / gradlew
│
├── astroland/                   # Official Astroland (https://codeberg.org/zacoons/astroland_dots)
│   ├── assets/                    Sprites, audio, animated GIFs
│   └── plugin/                    Hyprland C++ plugin (`libastroland.so`)
│
├── nixos/                       # NixOS system configuration (flake.nix, modules/)
│
├── src/main/kotlin/dev/petrolal/dotfiles/  # Deployer CLI (GraalVM native binary)
│   ├── cli/                       CLI entrypoint & Spring Shell commands
│   ├── core/                      Orchestration & path resolution (Orchestrator, DotfilePaths)
│   ├── domain/                    Functional domain types (Result, DotfileError)
│   ├── linker/                    Deterministic symlink engine (Linker)
│   ├── locker/                    PAM authentication (ScreenLocker, PamAuthService)
│   ├── system/                    Detached subprocess execution (ProcessRunner)
│   └── telemetry/                 Real-time /proc metrics polling (SystemVitals)
│
├── src/test/kotlin/...           # Unit tests
│
├── config/                       # Dotfiles (symlinked to ~/.config/)
│   ├── hypr/                       hyprland.lua, hypridle.conf, vhs.frag
│   ├── quickshell/                 Astroland Quickshell overlays (HUD, lockscreen, screensaver)
│   ├── fish/, fastfetch/, scripts/
│
└── docs/
    ├── astroland_port_handoff.md  # Astroland integration & build guide
    └── keybindings.md             # Keybindings & autostart reference
```

## Documentation

- [docs/astroland_port_handoff.md](docs/astroland_port_handoff.md): Guide to Astroland plugin
  building, Quickshell components, and layout controls.
- [docs/keybindings.md](docs/keybindings.md): Complete list of Hyprland keybinds and shortcuts.

## License

GPL-3.0-or-later
