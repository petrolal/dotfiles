# Dotfiles

NixOS system configuration and Wayland desktop powered by [Hyprland](https://hyprland.org/),
with a retro VHS/camcorder visual identity (Quickshell overlays + an AGS/Astal top bar), a unified
Kotlin CLI/deployer, and an integrated screen locker.

## Features

- **Hyprland Desktop**: Minimal, modern Wayland environment managed via Lua
  (`config/hypr/hyprland.lua`), with [greetd](https://man.sr.ht/~kennylevinsen/greetd/)/
  [tuigreet](https://github.com/apognu/tuigreet) as the login manager.
- **RetroVHSSucks retro-VHS overlays** (`RetroVHSSucks/assets/` + `config/quickshell/`, run via Quickshell (`qs`)):
  - `overlay/` — battery %/clock HUD, autostarted.
  - `notif/` — a `org.freedesktop.Notifications` D-Bus daemon + popup UI, autostarted.
  - `screensaver/` — idle screensaver (bouncing DVD logo + hold music), triggered by `hypridle`.
  - `lock/` — the retro lockscreen (`imp lock` → `qs ipc call lockscreen lock`), PAM auth via
    `config/quickshell/pam/password.conf`.
  - `config/ags/` — a separate AGS/Astal top bar in the same visual style (workspaces, clock,
    tray, battery). See `config/ags/README.md`.
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
and multimedia keys.

### Shell Integration

Default login shell is [fish](https://fishshell.com/) (`nixos/modules/users.nix`), configured in `config/fish/`.

## Project Structure

```
dotfiles/
├── bootstrap.sh                 # First-time setup script
├── build.gradle / settings.gradle / gradlew
│
├── RetroVHSSucks/                   # Retro-VHS assets (sprites, audio, GIFs) shared by the
│   └── assets/                    Quickshell overlays and the AGS bar
│
├── nixos/                       # NixOS system configuration (flake.nix, modules/)
│
├── src/main/kotlin/dev/petrolal/dotfiles/  # Deployer CLI (GraalVM native binary)
│   ├── cli/                       CLI entrypoint & Spring Shell commands
│   ├── core/                      Orchestration & path resolution (Orchestrator, DotfilePaths)
│   ├── domain/                    Functional domain types (Result, DotfileError)
│   ├── linker/                    Deterministic symlink engine (Linker)
│   └── system/                    Detached subprocess execution (ProcessRunner)
│
├── src/test/kotlin/...           # Unit tests
│
├── config/                       # Dotfiles (symlinked to ~/.config/)
│   ├── ags/                        AGS/Astal top bar (VHS styling)
│   ├── hypr/                       hyprland.lua, hypridle.conf, vhs.frag
│   ├── quickshell/                 RetroVHSSucks Quickshell overlays (HUD, lockscreen, screensaver)
│   ├── fish/, fastfetch/, scripts/
│
└── docs/
    ├── retrovhssucks_port_handoff.md  # RetroVHSSucks visual-identity integration guide
    └── keybindings.md             # Keybindings & autostart reference
```

## Documentation

- [docs/retrovhssucks_port_handoff.md](docs/retrovhssucks_port_handoff.md): Guide to the Quickshell
  overlays, lockscreen, screensaver, and AGS bar.
- [docs/keybindings.md](docs/keybindings.md): Complete list of Hyprland keybinds and shortcuts.

## License

GPL-3.0-or-later
