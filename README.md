# Dotfiles

NixOS system configuration and Wayland desktop powered by [Hyprland](https://hyprland.org/),
with a retro VHS/camcorder visual identity (Quickshell overlays + an AGS/Astal top bar), a unified
Kotlin CLI/deployer, and an integrated screen locker.

## Features

- **Hyprland Desktop**: Minimal, modern Wayland environment managed via Lua
  (`config/hypr/hyprland.lua`), with [greetd](https://man.sr.ht/~kennylevinsen/greetd/)/
  [tuigreet](https://github.com/apognu/tuigreet) as the login manager.
- **RetroVHSSucks retro-VHS overlays** (`assets/` + `apps/quickshell/`, run via Quickshell (`qs`)):
  - `overlay/` — battery %/clock HUD, autostarted.
  - `notif/` — a `org.freedesktop.Notifications` D-Bus daemon + popup UI, autostarted.
  - `screensaver/` — idle screensaver (bouncing DVD logo + hold music), triggered by `hypridle`.
  - `lock/` — the retro lockscreen (`imp lock` → `qs ipc call lockscreen lock`), PAM auth via
    `apps/quickshell/pam/password.conf`.
  - `apps/ags/` — a separate AGS/Astal top bar in the same visual style (workspaces, clock,
    tray, battery). See `apps/ags/README.md`.
  See [docs/retrovhssucks_port_handoff.md](docs/retrovhssucks_port_handoff.md) for the full architecture.
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

Log in to Hyprland for all changes to take effect.

## Day-to-Day Usage

### Gradle Tasks

| Command | What it does |
|---|---|
| `./gradlew deploy` | Re-deploy dotfiles symlinks (rebuilds the native image first) |
| `./gradlew quick-deploy` | Fast JVM execution of deploy without rebuilding the native image |
| `./gradlew test` | Run all test suites across modules (Kotlin JUnit + C++ Qt Test + AGS bundle check) |
| `./gradlew compileRetroRoom` | Compile C++ Qt6 QML plugin using CMake & Ninja (`plugins/retroroom`) |
| `./gradlew testRetroRoom` | Run Qt Test suite for RetroRoom C++ plugin |
| `./gradlew checkAgs` | Typecheck and verify AGS TypeScript top bar |
| `./gradlew nativeCompile` | Compile `imp` to a standalone GraalVM native executable (`bin/imp`) |
| `./gradlew all` | Compile native GraalVM `imp` and C++ QML plugin (default goal) |
| `./gradlew nix-switch` | Rebuild NixOS flake configuration (`sudo nixos-rebuild switch --flake ./nixos`) |
| `./gradlew system-install` | Rebuild NixOS + redeploy dotfiles |
| `./gradlew dry-run` | Preview what deploy would change without touching the filesystem |
| `./gradlew uninstall` | Safely remove all deployed dotfiles symlinks |
| `./gradlew clean` | Delete the build directory and output binaries |
| `./gradlew tasks` | Show all available Gradle tasks |

### Imp CLI (`bin/imp`)

```bash
imp deploy [-n/--dry-run] [-q/--quiet]
imp doctor                             # System diagnostic health check
imp bar [--action start|stop|restart|status]   # Manage AGS bar
imp hud [--action start|stop|restart|status]   # Manage Quickshell HUD overlays
imp room [--action travel|reset|status|vhs]    # Control 3D Retro Room camera
imp toggle-vhs                         # Toggle CRT post-processing shader
imp lock                               # Authenticated retro lockscreen
imp uninstall [-n/--dry-run] [-q/--quiet]
imp version
```

Note: `bin/imp` is a GraalVM native-image build that can be rebuilt with `./gradlew nativeCompile`.
For quick CLI deployment during development, `./gradlew quick-deploy` runs directly on the JVM.

### Keybindings

See [docs/keybindings.md](docs/keybindings.md) for the full, current list (generated from
`config/hypr/hyprland.lua`'s `hl.bind(...)` calls) -- it covers windows, workspaces, screenshots,
and multimedia keys.

### Shell Integration

Default login shell is [fish](https://fishshell.com/) (`nixos/modules/users.nix`), configured in `config/fish/`.

## Project Structure

```
dotfiles/
├── apps/                      # Software applications with dedicated toolchains
│   ├── cli/                   # Kotlin + GraalVM Native Image deployer CLI (`imp`)
│   ├── ags/                   # AGS / Astal VHS top bar (TypeScript)
│   └── quickshell/            # Quickshell HUD overlays & 3D background room (QML)
├── plugins/                   # Native compiled extensions
│   └── retroroom/             # Qt 6 Quick 3D background room plugin (C++)
├── assets/                    # Dedicated media assets (sounds, textures, sprites)
├── config/                    # Pure runtime dotfiles (symlinked to ~/.config/)
│   ├── hypr/                  # Hyprland window manager & bindings (Lua)
│   └── tofi/, fish/, gtk-*/   # Application launchers and themes
├── nixos/                     # NixOS system configuration (configuration.nix, modules/)
├── nix/                       # Nix packaging derivations (retroroom.nix, dotfiles-imp.nix)
├── bin/                       # Output native binaries (`bin/imp`)
├── build/                     # Unified gitignored build directory
├── settings.gradle            # Multi-project setup: include 'cli'
├── build.gradle               # Polyglot monorepo task coordinator
└── docs/                      # Architecture, keybindings, and integration guides
```

## Documentation

- [docs/project_architecture.md](docs/project_architecture.md): Polyglot monorepo architecture, toolchains, and workflows.
- [docs/retro_room_architecture.md](docs/retro_room_architecture.md): Interactive 3D retro room architecture & IPC protocol.
- [docs/retrovhssucks_port_handoff.md](docs/retrovhssucks_port_handoff.md): Guide to Quickshell overlays, lockscreen, screensaver, and AGS bar.
- [docs/keybindings.md](docs/keybindings.md): Complete list of Hyprland keybinds and shortcuts.

## License

GPL-3.0-or-later
