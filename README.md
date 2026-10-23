
# Dotfiles

NixOS system configuration and XFCE desktop setup, using the stock XFCE dark
theme.

## From-Scratch Install

On a fresh NixOS installation:

```bash
git clone <repo-url> ~/dotfiles
cd ~/dotfiles
./bootstrap.sh
```

That's it. The bootstrap script only needs what base NixOS provides (`sh`,
`git`, `sudo`, `nixos-rebuild`). It will:

1. Link the flake into `/etc/nixos`
2. Rebuild NixOS (installs all packages, fonts, tools)
3. Build the native deployer (Gradle + GraalVM native-image)
4. Deploy dotfiles and apply the desktop theme

Log out and back in for all changes to take effect.

## Day-to-Day Usage

After the initial bootstrap, use `make`:

| Command | What it does |
|---|---|
| `make deploy` | Redeploy dotfiles + theme (no NixOS rebuild) |
| `make nix-switch` | Rebuild NixOS after editing `.nix` modules |
| `make system-install` | Rebuild NixOS + redeploy (full update) |
| `make dry-run` | Preview what deploy would change |
| `make uninstall` | Safely remove all deployed dotfiles symlinks |
| `make check` | Run test suite and preview dry-run deploy |

Default login shell is [fish](https://fishshell.com/) (`nixos/modules/users.nix`),
configured in `config/fish/` (prompt, theme, abbreviations). Each command
above also has a fish abbreviation that runs from anywhere, no `cd` needed:
`dfdeploy`, `dfdry`, `dfuninstall`, `dfswitch`, `dfupdate`,
`dfupgrade`, `dfreload`, `ff` (fastfetch), plus `dotfiles` to `cd ~/dotfiles`.

## Project Structure

```
dotfiles/
├── bootstrap.sh                    # First-time setup script
├── build.sbt                       # Scala 3 + sbt build definition
├── Makefile                        # Desktop & NixOS orchestration tasks
│
├── nixos/                          # NixOS system configuration
│   ├── flake.nix                   # Flake entry point
│   ├── configuration.nix           # Module imports
│   └── modules/
│       ├── hardware.nix            # Boot loader, EFI
│       ├── networking.nix          # Hostname, NetworkManager, SSH
│       ├── desktop.nix             # X11, XFCE, sound, locale, GTK env
│       ├── nvidia.nix              # GPU (PRIME offload)
│       ├── fonts.nix               # Nerd Fonts (JetBrainsMono, FiraCode, Hack, Meslo)
│       ├── users.nix               # User accounts, default shell (fish)
│       ├── packages.nix            # System packages
│       └── development.nix         # direnv, dev-init, devshell
│
├── src/main/java/dev/petrolal/dotfiles/  # Java deployer (compiles to native binary via GraalVM)
│   ├── cli/                        # CLI argument parsing (Cli.java)
│   ├── core/                       # Orchestration, paths & theme constants (Orchestrator, DotfilePaths, Theme)
│   ├── desktop/                    # XFCE & GNOME settings (Xfconf)
│   ├── domain/                     # Functional domain types (Result, DotfileError)
│   ├── linker/                     # Symlink engine (Linker)
│   ├── wallpaper/                  # 3D OpenGL wallpaper engine (Wallpaper, X11Canvas, ShaderPipeline)
│   └── system/                     # Subprocess execution (ProcessRunner)
│
├── src/test/java/dev/petrolal/dotfiles/  # JUnit 5 tests (mirrored package structure)
│
├── config/                         # Dotfiles (symlinked to ~/.config/)
│   ├── fish/                       # Default shell: prompt, abbreviations, theme
│   ├── fastfetch/                  # Fastfetch system info configuration
│   ├── gtk-2.0/gtkrc
│   ├── gtk-3.0/settings.ini
│   ├── gtk-4.0/settings.ini
│   ├── xfce4/                     # Terminal, panel launchers, volume script
│   └── icons/                     # Chicago95 icon overrides
│
└── docs/
    └── dev-environments.md         # Per-project dev environment guide
```

## Dev Environments

Per-project toolchains via Nix + direnv. See
[docs/dev-environments.md](docs/dev-environments.md) for full documentation.

Quick start:

```bash
cd ~/projects/my-app
jvm-init           # Java (JDK 21 + Gradle + Maven)
node-init          # Node.js 22
go-init            # Go 1.26
python-init        # Python 3.13 + uv
dev-init python go # Multi-language
devshell           # Everything, from anywhere
```

## License

GPL-3.0-or-later
