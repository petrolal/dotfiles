
# Abyssal Biopunk / Infernal Retro Dotfiles

NixOS system configuration and XFCE desktop theme inspired by Windows 98 and
Mac OS 9 — dark palette, pixel-perfect bevels, zero animations.

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
3. Compile the native deployer (GraalVM native-image)
4. Deploy dotfiles and apply the desktop theme

Log out and back in for all changes to take effect.

## Day-to-Day Usage

After the initial bootstrap, use Make:

| Command | What it does |
|---|---|
| `make deploy` | Redeploy dotfiles + theme (no NixOS rebuild) |
| `make nix-switch` | Rebuild NixOS after editing `.nix` modules |
| `make system-install` | Rebuild NixOS + redeploy (full update) |
| `make dry-run` | Preview what deploy would change |
| `make uninstall` | Safely remove all deployed dotfiles symlinks |
| `make scale` | Reset panel height and WM margins for current display |
| `make help` | Show all available targets |

## Project Structure

```
dotfiles/
├── bootstrap.sh                    # First-time setup script
├── Makefile                        # Build and deploy orchestration
├── shell.nix                       # Nix dev shell (gnumake, GraalVM, git)
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
├── java/                           # Java deployer (compiles to native binary via GraalVM)
│   ├── Main.java                   # Entry point, delegates to Cli
│   ├── Cli.java                    # CLI parsing, --help, --version
│   ├── Orchestrator.java           # Deploy + reload orchestration
│   ├── Linker.java                 # Symlink mappings and link-file
│   ├── DotfilePaths.java           # Dotfiles root and home resolution
│   ├── Display.java                # Display detection (xrandr)
│   ├── Xfconf.java                 # XFCE/GNOME settings via xfconf-query
│   ├── XfconfXml.java              # Parses exported xfce-perchannel-xml, replays via xfconf-query
│   ├── Generators.java             # Hook for templated config generation (currently unused)
│   ├── Shell.java                  # ProcessBuilder wrapper
│   ├── Result.java                 # Functional error handling
│   └── DotfileError.java           # Typed domain errors
│
├── config/                         # Dotfiles (symlinked to ~/.config/)
│   ├── gtk-2.0/gtkrc
│   ├── gtk-3.0/settings.ini
│   ├── gtk-3.0/gtk.css             # IMP95 dark palette overrides
│   ├── gtk-4.0/settings.ini
│   ├── gtk-4.0/gtk.css             # Libadwaita accent overrides
│   ├── picom/picom.conf            # Compositor (hard shadows, no blur)
│   └── quickshell/                 # Quickshell panel config
│
├── themes/
│   ├── imp95-palette.css           # Canonical color palette
│   ├── icons/                      # imp98 icon theme
│   └── imp98/xfwm4/               # Window manager border theme
│
├── fonts/
│   └── w95fa.otf                   # Windows 95 font (W95FA)
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
