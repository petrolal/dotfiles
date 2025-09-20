# Dotfiles

Configurações pessoais para Openbox + CachyOS (Arch-based) usando **GNU Stow**.

Inclui:

- Openbox, Tint2, Picom, Rofi, Nitrogen, Obmenu-generator
- Kitty, Alacritty
- Bash, Zsh, Fish, Starship
- GTK2, GTK3, Kvantum, Icons, Themes, Xresources
- Dunst (notificações)
- MPD, Ncmpcpp, MPV
- Lazygit, Btop
- Wallpapers + Feh

---

## Instalação rápida

Requer Arch Linux ou derivado (ex.: CachyOS, EndeavourOS, Manjaro).

Clone e rode o script de instalação:

```bash
bash -c "$(curl -fsSL https://raw.githubusercontent.com/SEU-USUARIO/SEU-REPO/main/install.sh)"
```

Reinicie a sessão ou rode:

```bash
openbox --restart
```

## O que o script faz

- Instala pacotes via pacman (openbox, kitty, etc).
- Instala pacotes via AUR usando paru ou yay (se não houver, compila paru automaticamente).
- Clona ou atualiza este repositório em ~/dotfiles.
- Executa bootstrap.sh que:
  - Faz backup dos arquivos antigos em ~/dotfiles*backup*<data>.
  - Aplica symlinks via stow.

## Variáveis opcionais

> - REPO_URL=https://gitlab.com/usuario/dotfiles.git
>   Usar outro repositório (default é o GitHub acima).

> - DOTDIR=$HOME/.meusdotfiles
>   Alterar diretório de instalação.

> - NO_AUR=1
>   Pular instalação de pacotes AUR.

> - CHSH_SKIP=1
>   Não trocar o shell para Zsh.

exemplo:

```bash
REPO_URL=https://gitlab.com/usuario/dotfiles.git NO_AUR=1 bash -c "$(curl -fsSL https://raw.githubusercontent.com/SEU-USUARIO/SEU-REPO/main/install.sh)"
```

## Estrutura

> dotfiles/
> ├── openbox/.config/openbox/
> ├── tint2/.config/tint2/
> ├── picom/.config/picom/
> ├── kitty/.config/kitty/
> ├── alacritty/.config/alacritty/
> ├── dunst/.config/dunst/
> ├── rofi/.config/rofi/
> ├── gtk/.config/gtk-3.0/
> ├── gtk2/.gtkrc-2.0
> ├── kvantum/.config/Kvantum/
> ├── fish/.config/fish/
> ├── scripts/.local/bin/
> ├── wallpapers/.wallpapers/
> ├── fehbg/.fehbg
> └── bootstrap.sh

## Observação

- Se já existir config local, ela será movida para ~/dotfiles*backup*<timestamp>.
- Pacotes gráficos (temas, ícones, fontes) dependem dos diretórios .themes, .icons, .fonts.

# Créditos

Setup baseado em GNU Stow para manter dotfiles limpos e portáveis.

- petrolal - [https://github.com/petrolal](petrolal)
