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

backup_if_conflict() {
	local target="$1"
	if [ -e "$target" ] && [ ! -L "$target" ]; then
		mkdir -p "$BK$(dirname "$target")"
		mv -v "$target" "$BK$target"
	fi
}

# Liste aqui os destinos críticos que podem existir
mapfile -t paths < <(
	cat <<P
$HOME/.config/openbox/rc.xml
$HOME/.config/openbox/menu.xml
$HOME/.config/openbox/autostart
$HOME/.config/openbox/environment
$HOME/.config/tint2
$HOME/.config/picom/picom.conf
$HOME/.config/alacritty
$HOME/.local/bin
$HOME/.config/git/config
$HOME/.bashrc
$HOME/.zshrc
P
)

for p in "${paths[@]}"; do
	backup_if_conflict "$p"
done

# 3) Stow dos pacotes
cd "$(dirname "$0")"

# Cada diretório de primeiro nível é um pacote
for pkg in */; do
	pkg="${pkg%/}"
	echo "[info] stow $pkg"
	stow -v -R -t "$HOME" "$pkg"
done

echo "[ok] Dotfiles aplicados."
echo "Backups (se houver) em: $BK"
