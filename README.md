
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

After the initial bootstrap, use Gradle (`./gradlew`):

| Command | What it does |
|---|---|
| `./gradlew deploy` | Redeploy dotfiles + theme (no NixOS rebuild) |
| `./gradlew nix-switch` | Rebuild NixOS after editing `.nix` modules |
| `./gradlew system-install` | Rebuild NixOS + redeploy (full update) |
| `./gradlew dry-run` | Preview what deploy would change |
| `./gradlew uninstall` | Safely remove all deployed dotfiles symlinks |
| `./gradlew scale` | Reset WM margins for current display |
| `./gradlew tasks` | Show all available Gradle tasks |

## Project Structure

```
dotfiles/
├── bootstrap.sh                    # First-time setup script
├── build.gradle                    # Gradle build & orchestration tasks
├── settings.gradle                 # Gradle settings
├── gradlew                         # Gradle wrapper script
├── shell.nix                       # Nix dev shell (Gradle, GraalVM, git)
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
│       ├── users.nix               # User accounts
│       ├── packages.nix            # System packages
│       └── development.nix         # direnv, dev-init, devshell
│
├── src/main/java/dev/petrolal/dotfiles/  # Java deployer (compiles to native binary via GraalVM)
│   ├── Main.java                   # Entry point, delegates to Cli
│   ├── cli/                        # CLI argument parsing (Cli.java)
│   ├── core/                       # Orchestration & path resolution (Orchestrator, DotfilePaths)
│   ├── desktop/                    # Display, XFCE settings & XML replay (Display, Xfconf, XfconfXml)
│   ├── domain/                     # Functional domain types (Result, DotfileError)
│   ├── linker/                     # Symlink engine & config generation (Linker, Generators)
│   └── system/                     # Subprocess execution (ProcessRunner)
│
├── src/test/java/dev/petrolal/dotfiles/  # JUnit 5 tests (mirrored package structure)
│
├── config/                         # Dotfiles (symlinked to ~/.config/)
│   ├── gtk-2.0/gtkrc
│   ├── gtk-3.0/settings.ini
│   ├── gtk-4.0/settings.ini
│   └── picom/picom.conf            # Compositor
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
