#!/usr/bin/env bash
set -euo pipefail

# 1) Dependências
if ! command -v stow >/dev/null 2>&1; then
	echo "[info] Instalando GNU Stow..."
	if command -v pacman >/dev/null 2>&1; then
		sudo pacman -S --noconfirm stow
	else
		echo "[erro] Pacman não encontrado. Instale o stow manualmente." >&2
		exit 1
	fi
fi

# 2) Backup de conflitos
TS="$(date +%Y%m%d-%H%M%S)"
BK="$HOME/dotfiles_backup_$TS"
mkdir -p "$BK"

backup() {
	local target="$1"
	if [ -e "$target" ] && [ ! -L "$target" ]; then
		mkdir -p "$BK$(dirname "$target")"
		mv -v "$target" "$BK$target"
	fi
}

# 3) Alvos críticos a checar
while read -r p; do backup "$p"; done <<P
$HOME/.config/openbox
$HOME/.config/rofi
$HOME/.config/gtk-3.0
$HOME/.config/Kvantum
$HOME/.config/tint2
$HOME/.config/alacritty
$HOME/.config/git
$HOME/.config/kitty
$HOME/.config/picom/picom.conf
$HOME/.local/bin
$HOME/.config/dunst
$HOME/.config/nitrogen
$HOME/.Xresources
$HOME/.config/fish
$HOME/.config/starship.toml
$HOME/.gtkrc-2.0
$HOME/.config/mpv
$HOME/.config/mpd
$HOME/.config/ncmpcpp
$HOME/.config/lazygit
$HOME/.config/btop
$HOME/.bashrc
$HOME/.zshrc
$HOME/.config/starship.toml
$HOME/.fonts
$HOME/.icons
$HOME/.themes
$HOME/.wallpapers
$HOME/.fehbg
$HOME/.config/obmenu-generator
$HOME/.config/autostart
P

# 4) Stow de todos os pacotes presentes
cd "$(dirname "$0")"
for pkg in */; do
	pkg="${pkg%/}"
	echo "[stow] $pkg"
	stow -v -R -t "$HOME" "$pkg"
done

echo "[ok] Dotfiles aplicados."
echo "Backups (se houver) em: $BK"
