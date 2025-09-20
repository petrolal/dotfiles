#!/usr/bin/env bash
set -euo pipefail

# ===== Config =====
REPO_URL="${REPO_URL:-https://SEU-REPO.git}" # export REPO_URL=... para sobrescrever
DOTDIR="${DOTDIR:-$HOME/dotfiles}"

PAC_PKGS=(
	git stow
	openbox tint2 picom rofi
	kitty alacritty
	dunst
	mpd ncmpcpp mpv
	lazygit btop
	feh nitrogen
	starship
	kvantum-qt5 kvantum-qt6
)
AUR_PKGS=(
	obmenu-generator
	nerd-fonts-jetbrains-mono
)

# ===== Funções =====
need() { command -v "$1" >/dev/null 2>&1; }
have_sudo() { sudo -n true >/dev/null 2>&1 || {
	echo "Solicitando sudo..."
	sudo true
}; }
install_pacman() { sudo pacman -Syu --noconfirm --needed "${PAC_PKGS[@]}"; }
ensure_base_devel() { sudo pacman -S --noconfirm --needed base-devel; }

ensure_aur_helper() {
	if need yay; then
		echo yay
		return
	fi
	if need paru; then
		echo paru
		return
	fi
	ensure_base_devel
	tmpdir="$(mktemp -d)"
	trap 'rm -rf "$tmpdir"' EXIT
	git clone https://aur.archlinux.org/paru.git "$tmpdir/paru"
	(cd "$tmpdir/paru" && makepkg -si --noconfirm)
	echo paru
}

clone_or_update_repo() {
	if [ -d "$DOTDIR/.git" ]; then
		git -C "$DOTDIR" pull --rebase
	else
		git clone "$REPO_URL" "$DOTDIR"
	fi
}

run_bootstrap() {
	[ -x "$DOTDIR/bootstrap.sh" ] || {
		echo "bootstrap.sh não encontrado/executável em $DOTDIR"
		exit 1
	}
	"$DOTDIR/bootstrap.sh"
}

# ===== Execução =====
# 0) Pré-checagens
command -v pacman >/dev/null 2>&1 || {
	echo "Requer Arch/CachyOS (pacman)."
	exit 1
}
have_sudo

# 1) Pacotes oficiais
install_pacman

# 2) AUR (opcional; pule exportando NO_AUR=1)
if [ "${NO_AUR:-0}" != "1" ] && [ "${#AUR_PKGS[@]}" -gt 0 ]; then
	HELPER="$(ensure_aur_helper)"
	$HELPER -S --noconfirm --needed "${AUR_PKGS[@]}"
fi

# 3) Repo de dotfiles
clone_or_update_repo

# 4) Aplicar dotfiles
run_bootstrap

# 5) Pós (opcionais e seguros)
# setar zsh se existir e zsh instalado
if [ -f "$HOME/dotfiles/shell/.zshrc" ] && need zsh; then
	if [ "${CHSH_SKIP:-0}" != "1" ] && [ "$SHELL" != "$(command -v zsh)" ]; then
		chsh -s "$(command -v zsh)" "$USER" || true
	fi
fi

echo "[ok] Setup concluído."
echo "Repo: $REPO_URL"
echo "Dir:  $DOTDIR"
