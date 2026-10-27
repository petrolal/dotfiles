# Dotfiles

NixOS system configuration and Wayland desktop powered by [Hyprland](https://hyprland.org/), featuring custom 3D OpenGL desktop tools written in Kotlin: an Astroland wireframe-orrery overlay launcher, an OpenGL wallpaper engine, and an integrated screen locker with PAM authentication.

## Features

- **Hyprland Desktop**: Minimal, modern Wayland environment managed via Lua (`config/hypr/hyprland.lua`), with [greetd](https://man.sr.ht/~kennylevinsen/greetd/) and [tuigreet](https://github.com/apognu/tuigreet) as the login manager and [AGS](https://github.com/Aylur/ags) as the status bar/shell.
- **Astroland 3D Overlay Launcher** (`imp overlay`): Real-time interactive celestial orrery launcher rendered with raw LWJGL 3, GLFW, and JOML. Apps are represented as Platonic solid polyhedra (tetrahedron, octahedron, icosahedron) with translucent facet bodies and glowing wireframe edges orbiting a central CPU-reactive star, complete with ray-sphere mouse targeting, tracking HUD labels, and an FBO post-processing pipeline (chromatic aberration, vignette, scanlines).
- **3D Wallpaper Engine** (`imp wallpaper`): Multi-monitor OpenGL wallpaper daemon with offscreen rendering, shader pipelines, and optional MangoHud telemetry overlay toggled via `imp toggle-hud`.
- **Screen Locker** (`imp lock`): OpenGL-rendered screen locker using Java 25 Foreign Function & Memory API (Project Panama) for asynchronous PAM authentication against `imp-lock`.
- **Unified Kotlin CLI & Deployer** (`imp`): Built with Kotlin, Spring Shell, and GraalVM Native Image for instant startup. Includes an interactive shell and deterministic dotfiles symlink management.

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
4. Deploy dotfiles and start the wallpaper daemon

Log in to Hyprland for all changes to take effect.

## Day-to-Day Usage

### Gradle Tasks

Use Gradle (`./gradlew`) for building and maintenance:

| Command | What it does |
|---|---|
| `./gradlew deploy` | Re-deploy dotfiles symlinks and (re)start the 3D wallpaper daemon |
| `./gradlew quick-deploy` | Fast JVM execution of deploy without rebuilding the native image |
| `./gradlew quick-overlay` | Run the Astroland 3D overlay launcher directly on the JVM |
| `./gradlew test` | Run all Kotlin unit tests |
| `./gradlew nativeCompile` | Compile `imp` to a standalone GraalVM native executable (`bin/imp`) |
| `./gradlew nix-switch` | Rebuild NixOS flake configuration (`sudo nixos-rebuild switch --flake ./nixos`) |
| `./gradlew system-install` | Rebuild NixOS + redeploy dotfiles |
| `./gradlew dry-run` | Preview what deploy would change without touching the filesystem |
| `./gradlew uninstall` | Safely remove all deployed dotfiles symlinks |
| `./gradlew tasks` | Show all available Gradle tasks |

### Imp CLI (`bin/imp`)

`imp` can be run directly from the command line or as an interactive Spring Shell when invoked without subcommands:

```bash
imp deploy [-n/--dry-run] [-q/--quiet] [--links-only] [--no-wallpaper]
imp overlay [-n/--dry-run]
imp wallpaper [-n/--dry-run] [-q/--quiet] [--fps <maxFps>]
imp lock [-n/--dry-run] [-t/--timeout <seconds>]
imp toggle-hud [-q/--quiet]
imp uninstall [-n/--dry-run] [-q/--quiet]
imp version
```

### Keybindings (Hyprland)

Configured in [config/hypr/hyprland.lua](file:///home/petrolal/dotfiles/config/hypr/hyprland.lua):

- `SUPER + CTRL + O`: Open Astroland 3D overlay launcher (`imp overlay`)
- `SUPER + L`: Lock screen (`imp lock`)
- `SHIFT + SUPER + P`: Toggle MangoHud on wallpaper daemon (`imp toggle-hud`)
- `SUPER + RETURN`: Terminal (`kitty`)
- `SUPER + E`: File manager (`thunar`)
- `SUPER + B`: Web browser (`google-chrome-stable`)
- `SUPER + PRINT` / `SUPER + ALT + S`: Screenshot, region select (`grim`+`slurp`)

### Shell Integration

Default login shell is [fish](https://fishshell.com/) (`nixos/modules/users.nix`), configured in `config/fish/`. Handy abbreviations include:
- `dfdeploy`: `./gradlew deploy`
- `dfdry`: `./gradlew dry-run`
- `dfuninstall`: `./gradlew uninstall`
- `dfswitch`: `sudo nixos-rebuild switch --flake ./nixos`
- `dfupdate`: update flake inputs and rebuild
- `ff`: fastfetch
- `dotfiles`: `cd ~/dotfiles`

## Project Structure

```
dotfiles/
├── bootstrap.sh                        # First-time setup script
├── build.gradle                        # Gradle build & orchestration tasks
├── settings.gradle                     # Gradle settings
├── gradlew                             # Gradle wrapper script
│
├── nixos/                              # NixOS system configuration
│   ├── flake.nix                       # Flake entry point
│   ├── configuration.nix               # Module imports
│   └── modules/
│       ├── hardware.nix                # Boot loader, EFI
│       ├── networking.nix              # Hostname, NetworkManager, SSH
│       ├── desktop.nix                 # Audio (PipeWire), locale, console, blueman
│       ├── hyprland.nix                # Hyprland Wayland session, greetd, portals, polkit, imp-lock PAM
│       ├── nvidia.nix                  # GPU (PRIME offload)
│       ├── fonts.nix                   # Nerd Fonts (JetBrainsMono, FiraCode, Hack, Meslo)
│       ├── users.nix                   # User accounts, default shell (fish)
│       ├── packages.nix                # System packages
│       └── development.nix             # direnv, dev-init, devshell
│
├── src/main/kotlin/dev/petrolal/dotfiles/ # Kotlin deployer & GL tools (GraalVM native binary)
│   ├── cli/                            # CLI entrypoint & Spring Shell commands (Cli, DotfilesCommands)
│   ├── core/                           # Orchestration & path resolution (Orchestrator, DotfilePaths)
│   ├── domain/                         # Functional domain types (Result, DotfileError)
│   ├── linker/                         # Deterministic symlink engine (Linker)
│   ├── locker/                         # Screen locker & PAM authentication (ScreenLocker, PamAuthService)
│   ├── overlay/                        # Astroland 3D orrery launcher (CelestialSystem, PlanetRenderer, etc.)
│   ├── system/                         # Detached subprocess execution (ProcessRunner)
│   ├── telemetry/                      # Real-time /proc metrics polling (SystemVitals)
│   └── wallpaper/                      # 3D OpenGL wallpaper engine (Wallpaper, X11Canvas, ShaderPipeline)
│
├── src/test/kotlin/dev/petrolal/dotfiles/ # Unit tests in Kotlin
│
├── config/                             # Dotfiles (symlinked to ~/.config/)
│   ├── hypr/                           # Hyprland Lua configuration (hyprland.lua)
│   ├── fish/                           # Shell prompt, abbreviations, theme
│   ├── fastfetch/                      # System information display
│   ├── gtk-3.0/                        # GTK 3 settings
│   └── gtk-4.0/                        # GTK 4 settings
│
└── docs/
    ├── migration_plan.md               # Hyprland session migration & overlay architecture
    ├── checklist.md                    # Migration progress & verification checklist
    ├── biopunk_plan.md                 # Design notes & roadmap history
    └── dev-environments.md             # Per-project dev environment guide
```

## Documentation

- [docs/migration_plan.md](docs/migration_plan.md): Architectural decisions, native-image considerations, GLFW Wayland caveats, and session migration details.
- [docs/checklist.md](docs/checklist.md): Step-by-step verification checklist and component status.
- [docs/biopunk_plan.md](docs/biopunk_plan.md): Design exploration, audit, and rationale behind the overlay's evolutionary pivots.
- [docs/dev-environments.md](docs/dev-environments.md): Per-project development environments via Nix and direnv.

## License

GPL-3.0-or-later
