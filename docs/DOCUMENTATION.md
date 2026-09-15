# polyomino.dotfiles — Architecture, Design & Operations Guide

Complete technical reference, architecture guide, design specification, and maintainer workflow for **polyomino.dotfiles**.

---

## 1. System Architecture & Multi-Call Binary

`polyomino.dotfiles` is a desktop environment tooling suite designed for Sway/Wayland Linux desktop environments. It manages dynamic window autotiling, desktop theme switching across application surfaces, system health validation, config snapshot maintenance, and automated machine provisioning.

The suite follows a **Multi-Call Binary Architecture**: all subcommands compile into a single native binary (`polyomino`) via GraalVM Native Image. Subcommand invocations (e.g. `polyomino-theme`, `polyomino-autotiling`, `polyomino-whichkey`) are symlinks in `~/.local/bin` pointing to `polyomino`. The main binary inspects `argv[0]` or `argv[1]` to route execution to the target submodule.

### Technical Stack & Dependencies

- **Language & Runtime**: Scala 3.5.2 compiled Ahead-of-Time via GraalVM Community Edition 21.0.2 (`sbt-native-image` 0.5.0).
- **System I/O & Subprocesses**: `os-lib` (`com.lihaoyi %% os-lib % "0.11.9-M8"`) — direct POSIX syscalls with streamed process execution and zero intermediate buffering.
- **JSON Serialization**: `uPickle` (`com.lihaoyi %% upickle % "4.4.3"`) — compile-time static derive macros, 100% GraalVM reflection-free.
- **CLI Argument Parsing**: `mainargs` (`com.lihaoyi %% mainargs % "0.7.0"`).
- **Testing Framework**: `munit` (`org.scalameta %% munit % "1.0.0"`).
- **Target Environment**: Arch Linux / Fedora Linux on Wayland with Sway / SwayFX window manager.

### Architectural Invariants

1. **Single Multi-Call Executable**: One compiled ELF binary (~40–60 MB) with 15–50 ms startup latency and <60 MB RSS peak memory.
2. **Zero-Reflection Design**: All I/O and JSON parsing use compile-time macro derivation to avoid GraalVM `reflect-config.json` requirements.
3. **Functional Error Handling**: Errors propagate via `Either[PolyominoError, T]` across module boundaries, formatted with ANSI color output at top-level entrypoints.
4. **Context Discovery**: Environment, XDG paths (`~/.config`), and Sway IPC sockets (`SWAYSOCK`) are discovered at startup in `Context` and threaded through command dispatchers.

---

## 2. Desktop Surface Integrations

`polyomino.dotfiles` coordinates state and theme configuration across all desktop components:

| Component | Integration Method | Purpose |
|-----------|-------------------|---------|
| **Sway Window Manager** | IPC socket (`SWAYSOCK`) via `swaymsg` | Dynamic Fibonacci spiral window autotiling (`split v/h`), window focus, reload |
| **Waybar** | Signal trigger (`pkill -SIGUSR1 waybar`) | Status bar live stylesheet reload (`theme.css`) |
| **Kitty Terminal** | Signal (`kill -USR1`) | Live palette updates without restart |
| **Wofi Launcher** | GUI popup menus | Theme picker (`Mod+Shift+T`), wallpaper picker (`Mod+Shift+P`), cheatsheet (`Mod+Shift+?`) |
| **Neovim** | `$NVIM_LISTEN_ADDRESS` IPC | Live colorscheme synchronization |
| **GTK / GNOME** | `gsettings` CLI | Dark/light color-scheme preference syncing |
| **Swaylock** | Config template substitution | Lock screen styling per active theme |
| **Swayidle** | Daemon subprocess | Auto-lock, DPMS monitor power-off, suspend on inactivity |

---

## 3. Theme Engine Specification

### Neutral Base Tokens (Constant Across Themes)
- **Canvas / Monolith Base**: `#0F1117`
- **Surface / Container**: `#191C24`
- **Border / Divider**: `#2B303C`
- **Foreground Text**: `#F8FAFC`
- **Subdued / Muted Text**: `#64748B`

### Theme Variant Token Matrix
| Variant | Primary Accent | Secondary Accent | Wallpaper Motif |
|---------|----------------|------------------|-----------------|
| **Matriz** (Default) | `#EBB434` (Gold) | `#00D2D3` (Teal) | Golden sunrise geometric landscape |
| **Encruza** | `#EE5253` (Carmine Red) | `#3A3F4D` (Slate Graphite) | Deep red obsidian cliffs at dusk |
| **Caravela** | `#0984E3` (Deep Ocean) | `#00CEC9` (Maré Teal) | Turquoise Atlantic horizon |
| **Aruanda** | `#10AC84` (Mata Green) | `#F5CD79` (Warm Amber) | Lush forest canopy with sunbeams |

Dynamic token updates write to `~/.config/polyomino/theme.css` and notify running Waybar, Kitty, and Sway instances without altering layout metrics or border radii.

---

## 4. Maintenance & Tooling Operations

### SDKMAN! & JVM Toolchain Management

The repository includes `scripts/maintain-sdkman.sh` for interactive and non-interactive maintenance of Java and build tools:

```bash
# Interactive setup menu
./scripts/maintain-sdkman.sh install

# Routine checks & upgrades
./scripts/maintain-sdkman.sh check
./scripts/maintain-sdkman.sh list
./scripts/maintain-sdkman.sh update-all
./scripts/maintain-sdkman.sh upgrade
```

### Snapshot & Recovery

```bash
# Create timestamped tarball backup of dotfiles
polyomino backup

# Restore a previous snapshot
polyomino restore <archive-path>

# Run comprehensive diagnostic health check
polyomino healthcheck
```

---

## 5. Building, Publishing & Releases

### Local Compilation & Testing

```bash
# Run unit tests
sbt test

# Compile standalone GraalVM native binary
sbt nativeImage

# Install compiled binary locally
cp target/native-image/polyomino ~/.local/bin/polyomino
polyomino deploy
```

### Automated Release Pipeline (GitHub Actions)

When a git tag is pushed (e.g. `v0.1.0`), `.github/workflows/deploy.yml` automatically:
1. Runs the test suite (`sbt test`).
2. Compiles standalone native ELF binary `polyomino-x86_64-linux` via GraalVM Native Image.
3. Generates release archives and `SHA256SUMS.txt`.
4. Creates a GitHub Release with attached binaries.

### Arch Linux AUR Package Publishing

```bash
# 1. Update AUR metadata
makepkg --printsrcinfo > .SRCINFO

# 2. Push to AUR repository
cp PKGBUILD .SRCINFO ~/polyomino-dotfiles-aur/
cd ~/polyomino-dotfiles-aur
git add PKGBUILD .SRCINFO
git commit -m "chore: bump version to $NEW_VERSION"
git push
```

---

## 6. License

This project is distributed solely under the **BSD 3-Clause License**. Mandatory attribution to **Lucas Petrola** is required for any redistributions. See [LICENSE](../LICENSE) for details.
