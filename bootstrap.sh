#!/usr/bin/env bash
set -euo pipefail
command -v stow >/dev/null || {
	echo "Instale stow"
	exit 1
}

TS="$(date +%Y%m%d-%H%M%S)"
BK="$HOME/dotfiles_backup_$TS"
mkdir -p "$BK"

backup() { [ -e "$1" ] && [ ! -L "$1" ] && {
	mkdir -p "$BK$(dirname "$1")"
	mv -v "$1" "$BK$1"
}; }

# alvos comuns; adicione mais se precisar
while read -r p; do backup "$p"; done <<P
$HOME/.config/openbox/rc.xml
$HOME/.config/openbox/menu.xml
$HOME/.config/openbox/autostart
$HOME/.config/openbox/environment
$HOME/.config/tint2
$HOME/.config/picom/picom.conf
$HOME/.config/alacritty
$HOME/.config/nvim
$HOME/.local/bin
$HOME/.config/git/config
$HOME/.bashrc
$HOME/.zshrc
P

for pkg in */; do
	pkg="${pkg%/}"
	echo "[stow] $pkg"
	stow -v -R -t "$HOME" "$pkg"
done
echo "[ok] Backups em: $BK"
