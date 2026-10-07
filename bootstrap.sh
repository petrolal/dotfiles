#!/bin/sh
# bootstrap.sh --- First-time setup for a fresh NixOS installation
# License: GPL-3.0-or-later
#
# This script requires only what a base NixOS provides: sh, git, sudo,
# ln, and nixos-rebuild.  It links the flake into /etc/nixos, rebuilds
# the system (which installs gnumake, GraalVM, and every other package),
# then compiles and deploys the dotfiles.
#
# Usage:
#   git clone <repo> ~/dotfiles
#   cd ~/dotfiles
#   ./bootstrap.sh
#
# After the first run, use `make system-install` or `make deploy` for
# subsequent updates.

set -eu

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
DOTFILES_DIR="$(cd "$(dirname "$0")" && pwd)"
NIXOS_DIR="${DOTFILES_DIR}/nixos"
SYSCONFDIR="/etc/nixos"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m==> WARNING:\033[0m %s\n' "$*"; }
error() { printf '\033[1;31m==> ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || error "'$1' not found. Is this a NixOS system?"
}

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
info "Running pre-flight checks..."

require_cmd git
require_cmd sudo
require_cmd nixos-rebuild
require_cmd ln

[ -f "${NIXOS_DIR}/flake.nix" ] || error "flake.nix not found in ${NIXOS_DIR}. Are you in the dotfiles root?"
[ -f "${NIXOS_DIR}/configuration.nix" ] || error "configuration.nix not found in ${NIXOS_DIR}."

# ---------------------------------------------------------------------------
# Step 1: Register all files with git so the flake can see them
# ---------------------------------------------------------------------------
info "Registering files with git (intent-to-add)..."
cd "${DOTFILES_DIR}"
git add -N . 2>/dev/null || true

# ---------------------------------------------------------------------------
# Step 2: Symlink flake and configuration into /etc/nixos
# ---------------------------------------------------------------------------
info "Linking NixOS configuration into ${SYSCONFDIR}..."
sudo ln -sfn "${NIXOS_DIR}/configuration.nix" "${SYSCONFDIR}/configuration.nix"
sudo ln -sfn "${NIXOS_DIR}/flake.nix"         "${SYSCONFDIR}/flake.nix"

# ---------------------------------------------------------------------------
# Step 3: Rebuild NixOS (installs gnumake, GraalVM, fonts, everything)
# ---------------------------------------------------------------------------
info "Rebuilding NixOS (this may take a while on first run)..."
# nixos-rebuild resolves a bare --flake path by matching this machine's
# current hostname against flake.nix's nixosConfigurations; on a fresh
# install that hostname is whatever the installer set, almost never
# "abatedouro-de-anoes-PC". #default is the portable fallback config
# flake.nix defines for exactly this case.
sudo nixos-rebuild switch --flake "${NIXOS_DIR}#default"

# ---------------------------------------------------------------------------
# Step 4: Build the native invoker and deploy dotfiles
# ---------------------------------------------------------------------------
info "Compiling the invoker..."
make -C "${DOTFILES_DIR}" all

info "Deploying dotfiles and desktop theme..."
make -C "${DOTFILES_DIR}" deploy

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
echo ""
info "Bootstrap complete!"
echo ""
echo "  Your system is fully configured. For future changes:"
echo ""
echo "    make deploy          Redeploy dotfiles and theme"
echo "    make nix-switch      Rebuild NixOS after editing nix modules"
echo "    make system-install  Rebuild NixOS + redeploy (= full update)"
echo ""
echo "  Log out and back in for all desktop changes to take effect."
