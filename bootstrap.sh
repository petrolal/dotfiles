#!/usr/bin/env bash
# polyomino.dotfiles Bootstrap Installer
# Minimal installer: Installs Java & Coursier only
# Full setup is handled by: polyomino install
set -euo pipefail



echo -e "\033[1;36m[polyomino bootstrap]\033[0m Starting polyomino.dotfiles installer..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$HOME/.local/bin"

NON_INTERACTIVE=false
ENABLE_ALL=false
ENABLE_MINIMAL=false

# Optional component flags (unset by default to allow prompting)
ENABLE_CHROME=""
ENABLE_FIREFOX=""
ENABLE_BROWSER=""
BROWSER_MODE=""    # chrome | firefox | both | none
ENABLE_TETRAVIM=""
ENABLE_TUI_TOOLS=""
ENABLE_DEVOPS=""
ENABLE_DEV_RUNTIMES=""
ENABLE_DESKTOP_APPS=""
ENABLE_GAMING=""

for arg in "$@"; do
  case "$arg" in
    --all|-y|--yes)
      ENABLE_ALL=true
      NON_INTERACTIVE=true
      ;;
    --minimal|--no-optional)
      ENABLE_MINIMAL=true
      NON_INTERACTIVE=true
      ;;
    --non-interactive|-n)
      NON_INTERACTIVE=true
      ;;
    --chrome|--with-chrome)
      ENABLE_CHROME=true
      ;;
    --without-chrome|--no-chrome)
      ENABLE_CHROME=false
      ;;
    --firefox|--with-firefox)
      ENABLE_FIREFOX=true
      ;;
    --without-firefox|--no-firefox)
      ENABLE_FIREFOX=false
      ;;
    --gaming|--with-gaming|-g)
      ENABLE_GAMING=true
      ;;
    --without-gaming|--no-gaming)
      ENABLE_GAMING=false
      ;;
    --tetravim|--with-tetravim|--neovim)
      ENABLE_TETRAVIM=true
      ;;
    --without-tetravim|--no-tetravim|--no-neovim)
      ENABLE_TETRAVIM=false
      ;;
    --browser|--with-browser)
      ENABLE_BROWSER=true
      ENABLE_CHROME=true
      ENABLE_FIREFOX=true
      ;;
    --without-browser|--no-browser)
      ENABLE_BROWSER=false
      ENABLE_CHROME=false
      ENABLE_FIREFOX=false
      ;;
    --tui|--with-tui|--tools|--with-tools)
      ENABLE_TUI_TOOLS=true
      ;;
    --without-tui|--no-tui|--no-tools)
      ENABLE_TUI_TOOLS=false
      ;;
    --devops|--with-devops|--docker)
      ENABLE_DEVOPS=true
      ;;
    --without-devops|--no-devops|--no-docker)
      ENABLE_DEVOPS=false
      ;;
    --dev-runtimes|--with-dev-runtimes|--node|--with-node)
      ENABLE_DEV_RUNTIMES=true
      ;;
    --without-dev-runtimes|--no-dev-runtimes|--no-node)
      ENABLE_DEV_RUNTIMES=false
      ;;
    --desktop-apps|--with-desktop-apps|--telegram|--with-telegram)
      ENABLE_DESKTOP_APPS=true
      ;;
    --without-desktop-apps|--no-desktop-apps|--no-telegram)
      ENABLE_DESKTOP_APPS=false
      ;;
  esac
done

prompt_read() {
  local prompt_text="$1"
  local choice=""
  if [ -e /dev/tty ] && [ -r /dev/tty ]; then
    read -r -p "$prompt_text" choice < /dev/tty || choice=""
  else
    read -r -p "$prompt_text" choice || choice=""
  fi
  echo "$choice"
}

# ── Tetris-themed prompt palette ────────────────────────────────────────────
# Matches the sharp box-border / colour-accent aesthetic used across the rest
# of polyomino's TUIs (see PowerMenu): purple frames, cyan/green for active
# selections, yellow for warnings, muted gray for the secondary option.
T_PURPLE=$'\033[38;2;139;92;246m'
T_CYAN=$'\033[1;36m'
T_GREEN=$'\033[1;32m'
T_YELLOW=$'\033[1;33m'
T_RED=$'\033[1;31m'
T_BLUE=$'\033[1;34m'
T_ORANGE=$'\033[38;2;245;158;11m'
T_GRAY=$'\033[2;37m'
T_BOLD=$'\033[1m'
T_RESET=$'\033[0m'

# Sets globals GLYPH_TOP / GLYPH_BOTTOM to the two rows of a colored
# tetromino block glyph. $1 = piece letter (I O T S Z J L), $2 = color code.
_tetromino_glyph() {
  local piece="$1" color="$2"
  case "$piece" in
    I) GLYPH_TOP="${color}■■■■${T_RESET}"; GLYPH_BOTTOM="    " ;;
    O) GLYPH_TOP="${color}■■${T_RESET}"; GLYPH_BOTTOM="${color}■■${T_RESET}" ;;
    T) GLYPH_TOP="${color} ■ ${T_RESET}"; GLYPH_BOTTOM="${color}■■■${T_RESET}" ;;
    S) GLYPH_TOP="${color} ■■${T_RESET}"; GLYPH_BOTTOM="${color}■■ ${T_RESET}" ;;
    Z) GLYPH_TOP="${color}■■ ${T_RESET}"; GLYPH_BOTTOM="${color} ■■${T_RESET}" ;;
    J) GLYPH_TOP="${color}■  ${T_RESET}"; GLYPH_BOTTOM="${color}■■■${T_RESET}" ;;
    L) GLYPH_TOP="${color}  ■${T_RESET}"; GLYPH_BOTTOM="${color}■■■${T_RESET}" ;;
    *) GLYPH_TOP="${color}■■■■${T_RESET}"; GLYPH_BOTTOM="    " ;;
  esac
}

# Ensure cursor is restored on exit/interrupt
trap 'echo -en "\033[?25h"' EXIT INT TERM

# Non-blocking check: true if we should skip the interactive prompt entirely
# (CI, forced non-interactive, or no controlling terminal at all).
_tetris_noninteractive() {
  [ "${CI:-}" = "1" ] || [ "${NON_INTERACTIVE:-false}" = true ] || { [ ! -t 0 ] && [ ! -e /dev/tty ]; }
}

# Ensure sudo credentials are cached prior to running background steps
ensure_sudo() {
  if [ "$EUID" -ne 0 ] && command -v sudo &>/dev/null; then
    if ! sudo -n true 2>/dev/null; then
      echo -e "  ${T_YELLOW}[INFO]${T_RESET} Sudo privileges required. Please authenticate:"
      sudo -v || true
    fi
  fi
}

# Tetris-themed animated spinner & background task runner
# Usage: run_tetris_step "Step Description" <command or function> [args...]
run_tetris_step() {
  local title="$1"
  shift

  local log_slug
  log_slug="$(echo "$title" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g' | sed -E 's/^-|-$//g')"
  local log_file="/tmp/polyomino-bootstrap-${log_slug}.log"
  rm -f "$log_file"

  # Non-interactive fallback
  if _tetris_noninteractive; then
    echo -e "  ${T_CYAN}[ ▶ HARD DROP ]${T_RESET} ${T_BOLD}${title}${T_RESET}..."
    local start_time
    start_time=$(date +%s)
    if "$@" > "$log_file" 2>&1; then
      local elapsed=$(( $(date +%s) - start_time ))
      echo -e "  ${T_GREEN}[ ✔ LINE CLEAR ]${T_RESET} ${title} ${T_GRAY}(${elapsed}s)${T_RESET}"
      return 0
    else
      local exit_code=$?
      local elapsed=$(( $(date +%s) - start_time ))
      echo -e "  ${T_RED}[ ✘ TOP OUT ]${T_RESET} ${title} ${T_RED}FAILED${T_RESET} ${T_GRAY}(${elapsed}s)${T_RESET}" >&2
      echo -e "  ${T_YELLOW}── Last lines of log (${log_file}) ──${T_RESET}" >&2
      tail -n 15 "$log_file" | sed 's/^/    /' >&2 || true
      echo -e "  ${T_YELLOW}──────────────────────────────────────────${T_RESET}" >&2
      return $exit_code
    fi
  fi

  # Interactive animated runner
  local -a p_names=("I-Piece" "O-Piece" "T-Piece" "S-Piece" "Z-Piece" "J-Piece" "L-Piece")
  local -a p_colors=("$T_CYAN" "$T_YELLOW" "$T_PURPLE" "$T_GREEN" "$T_RED" "$T_BLUE" "$T_ORANGE")
  local -a p_glyphs=("■■■■   " "■■/■■  " " ■ /■■■" " ■■/■■ " "■■ / ■■" "■  /■■■" "  ■/■■■")

  local start_time
  start_time=$(date +%s)

  # Start step in background and capture all stdout/stderr
  "$@" > "$log_file" 2>&1 &
  local pid=$!

  # Hide cursor
  echo -en "\033[?25l"

  local frame=0
  local num_pieces=${#p_names[@]}

  while kill -0 "$pid" 2>/dev/null; do
    local idx=$(( frame % num_pieces ))
    local cur_color="${p_colors[$idx]}"
    local cur_glyph="${p_glyphs[$idx]}"
    local cur_name="${p_names[$idx]}"

    local cur_time
    cur_time=$(date +%s)
    local elapsed=$(( cur_time - start_time ))
    local mins=$(( elapsed / 60 ))
    local secs=$(( elapsed % 60 ))
    local timer
    printf -v timer "%02d:%02d" "$mins" "$secs"

    # Single-line retro HUD update
    printf "\r  ${T_PURPLE}│${T_RESET} ${T_GRAY}[%s]${T_RESET} %b[ %-7s %-7s ]%b %-45s" \
      "$timer" "$cur_color" "$cur_glyph" "$cur_name" "$T_RESET" "${title}..."

    frame=$(( frame + 1 ))
    sleep 0.12
  done

  wait "$pid"
  local exit_code=$?
  local total_elapsed=$(( $(date +%s) - start_time ))

  # Show cursor
  echo -en "\033[?25h"

  # Clear line
  printf "\r\033[2K"

  if [ $exit_code -eq 0 ]; then
    echo -e "  ${T_GREEN}[ ✔ LINE CLEAR ]${T_RESET} ${T_BOLD}${title}${T_RESET} ${T_GRAY}(${total_elapsed}s)${T_RESET}"
    return 0
  else
    echo -e "  ${T_RED}[ ✘ TOP OUT ]${T_RESET} ${T_BOLD}${title}${T_RESET} ${T_RED}FAILED${T_RESET} ${T_GRAY}(${total_elapsed}s)${T_RESET}" >&2
    echo -e "  ${T_YELLOW}── Last 20 lines of log (${log_file}) ──${T_RESET}" >&2
    tail -n 20 "$log_file" | sed 's/^/    /' >&2 || true
    echo -e "  ${T_YELLOW}──────────────────────────────────────────${T_RESET}" >&2
    return $exit_code
  fi
}

# prompt_tetris_yn <piece> <color> <label> <default: true|false>
# Draws a "NEXT PIECE" card and asks a Hard Drop (yes) / Hold (skip)
# question. Prints "true" or "false" on stdout (only) so it can be
# captured with `choice="$(prompt_tetris_yn ...)"`; all UI chrome goes
# to stderr.
prompt_tetris_yn() {
  local piece="$1" color="$2" label="$3" default_yes="$4"
  _tetromino_glyph "$piece" "$color"

  {
    echo -e "  ${T_PURPLE}┌─ NEXT PIECE ────────────────────────────────────────────┐${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${GLYPH_TOP}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${GLYPH_BOTTOM}"
    echo -e "  ${T_PURPLE}│${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${T_BOLD}${label}${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${T_GREEN}[H] Hard Drop${T_RESET} (yes)      ${T_GRAY}[h] Hold${T_RESET} (skip)"
    echo -e "  ${T_PURPLE}└─────────────────────────────────────────────────────────┘${T_RESET}"
  } >&2

  if _tetris_noninteractive; then
    if [ "$default_yes" = true ]; then
      echo -e "  ${T_CYAN}[INFO]${T_RESET} Non-interactive: auto Hard Drop." >&2
      echo "true"
    else
      echo -e "  ${T_GRAY}[INFO]${T_RESET} Non-interactive: auto Hold." >&2
      echo "false"
    fi
    return
  fi

  local hint="[H/h, Enter=Hold]"
  [ "$default_yes" = true ] && hint="[H/h, Enter=Hard Drop]"

  local key=""
  if [ -e /dev/tty ] && [ -r /dev/tty ]; then
    read -r -n1 -p "  Press key ${hint} > " key < /dev/tty || key=""
  else
    read -r -n1 -p "  Press key ${hint} > " key || key=""
  fi
  echo "" >&2

  case "$key" in
    H|Y|y)
      echo -e "  ${T_GREEN}>> Hard drop confirmed: installing.${T_RESET}" >&2
      echo "true"
      ;;
    h|N|n)
      echo -e "  ${T_GRAY}>> Piece held: skipping.${T_RESET}" >&2
      echo "false"
      ;;
    "")
      if [ "$default_yes" = true ]; then
        echo -e "  ${T_GREEN}>> Hard drop confirmed: installing.${T_RESET}" >&2
        echo "true"
      else
        echo -e "  ${T_GRAY}>> Piece held: skipping.${T_RESET}" >&2
        echo "false"
      fi
      ;;
    *)
      if [ "$default_yes" = true ]; then
        echo -e "  ${T_GREEN}>> Hard drop confirmed: installing.${T_RESET}" >&2
        echo "true"
      else
        echo -e "  ${T_GRAY}>> Piece held: skipping.${T_RESET}" >&2
        echo "false"
      fi
      ;;
  esac
}

# prompt_tetromino_select <title> <"label|piece|color"> ...
# Draws a multi-piece selection card and returns the 1-based index of the
# chosen option on stdout. Falls back to option 1 when non-interactive or
# on invalid input.
prompt_tetromino_select() {
  local title="$1"; shift
  local -a labels=() pieces=() colors=()
  local opt lbl pc col
  for opt in "$@"; do
    IFS='|' read -r lbl pc col <<< "$opt"
    labels+=("$lbl"); pieces+=("$pc"); colors+=("$col")
  done

  {
    echo -e "  ${T_PURPLE}┌─ ${title} ─────────────────────────────────────────┐${T_RESET}"
    local idx
    for idx in "${!labels[@]}"; do
      _tetromino_glyph "${pieces[$idx]}" "${colors[$idx]}"
      echo -e "  ${T_PURPLE}│${T_RESET}   ${T_BOLD}[$((idx + 1))]${T_RESET} ${GLYPH_TOP}  ${labels[$idx]}"
      echo -e "  ${T_PURPLE}│${T_RESET}       ${GLYPH_BOTTOM}"
    done
    echo -e "  ${T_PURPLE}└────────────────────────────────────────────────────────┘${T_RESET}"
  } >&2

  if _tetris_noninteractive; then
    echo -e "  ${T_CYAN}[INFO]${T_RESET} Non-interactive: defaulting to [1] ${labels[0]}." >&2
    echo "1"
    return
  fi

  local key=""
  if [ -e /dev/tty ] && [ -r /dev/tty ]; then
    read -r -n1 -p "  Rotate & drop [1-${#labels[@]}] > " key < /dev/tty || key=""
  else
    read -r -n1 -p "  Rotate & drop [1-${#labels[@]}] > " key || key=""
  fi
  echo "" >&2

  if [[ "$key" =~ ^[0-9]$ ]] && [ "$key" -ge 1 ] && [ "$key" -le "${#labels[@]}" ]; then
    echo -e "  ${T_GREEN}>> Line clear! Selected: ${labels[$((key - 1))]}${T_RESET}" >&2
    echo "$key"
  else
    echo -e "  ${T_YELLOW}[WARN]${T_RESET} Invalid input, defaulting to [1] ${labels[0]}." >&2
    echo "1"
  fi
}

prompt_checkbox_menu() {
  local -a labels=(
    "Google Chrome Stable (Official Web Browser)"
    "Mozilla Firefox (Secondary Web Browser)"
    "Neovim & Tetravim (Polyomino IDE Distribution)"
    "TUI Productivity Suite (spotify_player, bluetui, impala, aerc, yazi, zoxide, fastfetch)"
    "DevOps & Cloud Tooling (Docker, Terraform, Ansible, kubectl, Helm, cloud CLIs)"
    "Developer Runtimes (Node.js/npm via NVM, SDKMAN! & Kotlin)"
    "Desktop Apps (Telegram Desktop, Discord, TETR.IO)"
    "Gaming Stack & Emulators (GameMode, Gamescope, MangoHud, Steam, Emulators)"
  )
  local -a states=(1 1 1 1 0 0 0 0)

  # If explicit CLI flags were provided, respect them
  [ "${ENABLE_CHROME:-}" = true ] && states[0]=1
  [ "${ENABLE_CHROME:-}" = false ] && states[0]=0
  [ "${ENABLE_FIREFOX:-}" = true ] && states[1]=1
  [ "${ENABLE_FIREFOX:-}" = false ] && states[1]=0
  [ "${ENABLE_TETRAVIM:-}" = true ] && states[2]=1
  [ "${ENABLE_TETRAVIM:-}" = false ] && states[2]=0
  [ "${ENABLE_TUI_TOOLS:-}" = true ] && states[3]=1
  [ "${ENABLE_TUI_TOOLS:-}" = false ] && states[3]=0
  [ "${ENABLE_DEVOPS:-}" = true ] && states[4]=1
  [ "${ENABLE_DEVOPS:-}" = false ] && states[4]=0
  [ "${ENABLE_DEV_RUNTIMES:-}" = true ] && states[5]=1
  [ "${ENABLE_DEV_RUNTIMES:-}" = false ] && states[5]=0
  [ "${ENABLE_DESKTOP_APPS:-}" = true ] && states[6]=1
  [ "${ENABLE_DESKTOP_APPS:-}" = false ] && states[6]=0
  [ "${ENABLE_GAMING:-}" = true ] && states[7]=1
  [ "${ENABLE_GAMING:-}" = false ] && states[7]=0

  if _tetris_noninteractive; then
    echo -e "  ${T_CYAN}[INFO]${T_RESET} Non-interactive mode: using configured component selection." >&2
  else
    local cursor=0
    local num_items=${#labels[@]}
    local tty_in="/dev/tty"
    [ -r /dev/tty ] || tty_in="-"

    echo -en "\033[?25l" >&2

    draw_checkbox_menu() {
      echo -e "  ${T_PURPLE}┌── POLYOMINO // OPTIONAL COMPONENT CHECKBOXES ─────────────────────────────────┐${T_RESET}" >&2
      echo -e "  ${T_PURPLE}│${T_RESET}  ${T_GRAY}Navigate: ↑/↓ or j/k • Toggle: Space • Direct: [1-8] • Confirm: Enter      ${T_RESET}${T_PURPLE}│${T_RESET}" >&2
      echo -e "  ${T_PURPLE}├───────────────────────────────────────────────────────────────────────────────┤${T_RESET}" >&2
      local i
      for i in "${!labels[@]}"; do
        local mark=" "
        local mark_color="$T_GRAY"
        if [ "${states[$i]}" -eq 1 ]; then
          mark="✔"
          mark_color="$T_GREEN"
        fi
        local pointer="  "
        local item_color="$T_RESET"
        if [ "$i" -eq "$cursor" ]; then
          pointer="${T_CYAN}❯${T_RESET} "
          item_color="${T_BOLD}${T_CYAN}"
        fi
        local num_tag="${T_YELLOW}[$((i+1))]${T_RESET}"
        local box="${mark_color}[${mark}]${T_RESET}"
        printf "  ${T_PURPLE}│${T_RESET} %b%b %b %b%-60s${T_PURPLE}│${T_RESET}\n" "$pointer" "$box" "$num_tag" "$item_color" "${labels[$i]}" >&2
      done
      echo -e "  ${T_PURPLE}├───────────────────────────────────────────────────────────────────────────────┤${T_RESET}" >&2
      echo -e "  ${T_PURPLE}│${T_RESET}  ${T_CYAN}[A]${T_RESET} Select All     ${T_YELLOW}[N]${T_RESET} Deselect All     ${T_GREEN}[Enter]${T_RESET} Confirm & Proceed          ${T_PURPLE}│${T_RESET}" >&2
      echo -e "  ${T_PURPLE}└───────────────────────────────────────────────────────────────────────────────┘${T_RESET}" >&2
    }

    local total_lines=$((num_items + 6))
    draw_checkbox_menu

    while true; do
      local key=""
      if [ "$tty_in" = "/dev/tty" ]; then
        IFS= read -rsn1 key < /dev/tty || key=""
      else
        IFS= read -rsn1 key || key=""
      fi

      if [ "$key" = $'\x1b' ]; then
        local rest=""
        if [ "$tty_in" = "/dev/tty" ]; then
          IFS= read -rsn2 -t 0.1 rest < /dev/tty || rest=""
        else
          IFS= read -rsn2 -t 0.1 rest || rest=""
        fi
        if [ "$rest" = "[A" ]; then key="UP"; fi
        if [ "$rest" = "[B" ]; then key="DOWN"; fi
      fi

      case "$key" in
        UP|k|K)
          cursor=$(( (cursor - 1 + num_items) % num_items ))
          ;;
        DOWN|j|J)
          cursor=$(( (cursor + 1) % num_items ))
          ;;
        " ")
          states[$cursor]=$(( 1 - states[$cursor] ))
          ;;
        [1-8])
          local idx=$((key - 1))
          states[$idx]=$(( 1 - states[$idx] ))
          ;;
        a|A)
          for i in "${!states[@]}"; do states[$i]=1; done
          ;;
        n|N)
          for i in "${!states[@]}"; do states[$i]=0; done
          ;;
        "")
          break
          ;;
      esac

      echo -en "\033[${total_lines}A" >&2
      draw_checkbox_menu
    done

    echo -en "\033[?25h" >&2
    echo "" >&2
  fi

  [ "${states[0]}" -eq 1 ] && ENABLE_CHROME=true || ENABLE_CHROME=false
  [ "${states[1]}" -eq 1 ] && ENABLE_FIREFOX=true || ENABLE_FIREFOX=false
  [ "${states[2]}" -eq 1 ] && ENABLE_TETRAVIM=true || ENABLE_TETRAVIM=false
  [ "${states[3]}" -eq 1 ] && ENABLE_TUI_TOOLS=true || ENABLE_TUI_TOOLS=false
  [ "${states[4]}" -eq 1 ] && ENABLE_DEVOPS=true || ENABLE_DEVOPS=false
  [ "${states[5]}" -eq 1 ] && ENABLE_DEV_RUNTIMES=true || ENABLE_DEV_RUNTIMES=false
  [ "${states[6]}" -eq 1 ] && ENABLE_DESKTOP_APPS=true || ENABLE_DESKTOP_APPS=false
  [ "${states[7]}" -eq 1 ] && ENABLE_GAMING=true || ENABLE_GAMING=false

  if [ "$ENABLE_CHROME" = true ] && [ "$ENABLE_FIREFOX" = true ]; then
    BROWSER_MODE="both"
    ENABLE_BROWSER=true
  elif [ "$ENABLE_CHROME" = true ]; then
    BROWSER_MODE="chrome"
    ENABLE_BROWSER=true
  elif [ "$ENABLE_FIREFOX" = true ]; then
    BROWSER_MODE="firefox"
    ENABLE_BROWSER=true
  else
    BROWSER_MODE="none"
    ENABLE_BROWSER=false
  fi
}

prompt_optional_dependencies() {
  if [ "$ENABLE_ALL" = true ]; then
    ENABLE_CHROME=true
    ENABLE_FIREFOX=true
    ENABLE_BROWSER=true
    BROWSER_MODE="both"
    ENABLE_TETRAVIM=true
    ENABLE_TUI_TOOLS=true
    ENABLE_DEVOPS=true
    ENABLE_DEV_RUNTIMES=true
    ENABLE_DESKTOP_APPS=true
    ENABLE_GAMING=true
    return
  fi

  if [ "$ENABLE_MINIMAL" = true ]; then
    ENABLE_CHROME=false
    ENABLE_FIREFOX=false
    ENABLE_BROWSER=false
    BROWSER_MODE="none"
    ENABLE_TETRAVIM=false
    ENABLE_TUI_TOOLS=false
    ENABLE_DEVOPS=false
    ENABLE_DEV_RUNTIMES=false
    ENABLE_DESKTOP_APPS=false
    ENABLE_GAMING=false
    return
  fi

  prompt_checkbox_menu
}

# Step 1: Detect package manager & install system dependencies
detect_pkg_mgr() {
  if command -v pacman &> /dev/null; then
    echo "pacman"
  elif command -v apt-get &> /dev/null; then
    echo "apt-get"
  elif command -v dnf &> /dev/null; then
    echo "dnf"
  else
    echo "unknown"
  fi
}

install_system_deps() {
  local pkg_mgr="$1"

  if [ "$pkg_mgr" = "unknown" ]; then
    echo -e "  ${T_YELLOW}[NOTE]${T_RESET} Package manager not detected. Skipping system package installation."
    return
  fi

  ensure_sudo

  # Optional packages to include
  local opt_pkgs=""
  local browser_pkgs_pacman="" browser_pkgs_apt="" browser_pkgs_dnf=""
  case "$BROWSER_MODE" in
    chrome)
      browser_pkgs_pacman=""
      browser_pkgs_apt=""
      browser_pkgs_dnf=""
      ;;
    firefox)
      browser_pkgs_pacman="firefox"
      browser_pkgs_apt="firefox"
      browser_pkgs_dnf="firefox"
      ;;
    both)
      browser_pkgs_pacman="firefox"
      browser_pkgs_apt="firefox"
      browser_pkgs_dnf="firefox"
      ;;
    *)
      browser_pkgs_pacman=""
      browser_pkgs_apt=""
      browser_pkgs_dnf=""
      ;;
  esac

  case "$pkg_mgr" in
    pacman)
      SWAY_PKG=""
      if ! pacman -Qq swayfx &>/dev/null && ! pacman -Qq sway &>/dev/null; then
        if ! command -v yay &>/dev/null; then
          SWAY_PKG="sway"
        fi
      fi

      [ "$ENABLE_TETRAVIM" = true ] && opt_pkgs="$opt_pkgs neovim"
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs $browser_pkgs_pacman"
      [ "$ENABLE_TUI_TOOLS" = true ] && opt_pkgs="$opt_pkgs fastfetch zoxide"
      [ "$ENABLE_DEVOPS" = true ] && opt_pkgs="$opt_pkgs docker"
      [ "$ENABLE_DESKTOP_APPS" = true ] && opt_pkgs="$opt_pkgs telegram-desktop"

      _pacman_install() {
        sudo pacman -S --needed --noconfirm \
          base-devel git curl wget \
          zsh fontconfig \
          $SWAY_PKG waybar kitty wofi swaylock gtklock swayidle grim slurp \
          brightnessctl libpulse playerctl wireplumber swaync mako mpv \
          python-gobject python-cairo gtk3 gtk-layer-shell gtk-session-lock pam \
          ttf-jetbrains-mono-nerd \
          $opt_pkgs
      }
      run_tetris_step "Installing system packages & Wayland stack (pacman)" _pacman_install
      ;;
    apt-get)
      _apt_update() {
        sudo DEBIAN_FRONTEND=noninteractive apt-get update -y
      }
      run_tetris_step "Updating Apt package repositories" _apt_update

      DOCKER_PKG=""
      if [ "$ENABLE_DEVOPS" = true ] && ! command -v docker &> /dev/null; then
        DOCKER_PKG="docker.io"
      fi

      [ "$ENABLE_TETRAVIM" = true ] && opt_pkgs="$opt_pkgs neovim"
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs $browser_pkgs_apt"
      [ "$ENABLE_TUI_TOOLS" = true ] && opt_pkgs="$opt_pkgs fastfetch zoxide"

      _apt_install() {
        sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
          build-essential git curl wget \
          zsh fontconfig \
          sway waybar kitty wofi swaylock swayidle grim slurp \
          brightnessctl playerctl wireplumber pulseaudio-utils sway-notification-center mako-notifier mpv \
          python3-gi python3-cairo gir1.2-gtk-3.0 gir1.2-gtklayershell-0.1 libpam0g-dev \
          fonts-jetbrains-mono \
          $DOCKER_PKG \
          $opt_pkgs
      }
      run_tetris_step "Installing system packages & Wayland stack (apt-get)" _apt_install
      ;;
    dnf)
      [ "$ENABLE_TETRAVIM" = true ] && opt_pkgs="$opt_pkgs neovim"
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs $browser_pkgs_dnf"
      [ "$ENABLE_TUI_TOOLS" = true ] && opt_pkgs="$opt_pkgs fastfetch zoxide"
      [ "$ENABLE_DEVOPS" = true ] && opt_pkgs="$opt_pkgs docker"
      [ "$ENABLE_DESKTOP_APPS" = true ] && opt_pkgs="$opt_pkgs telegram-desktop"

      _dnf_install() {
        sudo dnf install -y \
          gcc gcc-c++ git curl wget \
          zsh fontconfig \
          sway waybar kitty wofi swaylock swayidle grim slurp \
          brightnessctl playerctl wireplumber pulseaudio-libs sway-notification-center mako mpv \
          python3-gobject python3-cairo gtk3 gtk-layer-shell pam-devel \
          $opt_pkgs
      }
      run_tetris_step "Installing system packages & Wayland stack (dnf)" _dnf_install
      ;;
    *)
      echo -e "  ${T_YELLOW}[NOTE]${T_RESET} Package manager '$pkg_mgr' not automatically managed. Skipping installation."
      ;;
  esac

  # Google Chrome Stable installation
  if [ "${ENABLE_CHROME:-false}" = true ]; then
    if ! command -v google-chrome &>/dev/null && ! command -v google-chrome-stable &>/dev/null; then
      _install_chrome() {
        case "$pkg_mgr" in
          apt-get)
            local chrome_deb="/tmp/google-chrome-stable_current_amd64.deb"
            curl -fsSL "https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb" -o "$chrome_deb"
            if [ -f "$chrome_deb" ]; then
              sudo DEBIAN_FRONTEND=noninteractive apt-get install -y "$chrome_deb" || sudo DEBIAN_FRONTEND=noninteractive apt-get install -f -y
              rm -f "$chrome_deb"
            fi
            ;;
          pacman)
            if command -v yay &>/dev/null; then
              yay -S --needed --noconfirm --answerclean None --answerdiff None google-chrome
            fi
            ;;
          dnf)
            sudo dnf install -y fedora-workstation-repositories || true
            sudo dnf config-manager --set-enabled google-chrome || true
            sudo dnf install -y google-chrome-stable
            ;;
        esac
      }
      run_tetris_step "Installing Google Chrome Stable" _install_chrome
    fi
  fi
}

install_java() {
  if [ -d "$HOME/.sdkman/candidates/java/current/bin" ]; then
    export PATH="$HOME/.sdkman/candidates/java/current/bin:$PATH"
  fi

  if command -v java &> /dev/null; then
    echo -e "  ${T_GREEN}[OK]${T_RESET} Java already installed: $(java -version 2>&1 | head -1)"
    return
  fi

  _java_step() {
    # Try to install via SDKMan
    if [ ! -d "$HOME/.sdkman" ]; then
      curl -s "https://get.sdkman.io" | bash
    fi

    if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
      set +u
      source "$HOME/.sdkman/bin/sdkman-init.sh"
      sdk install java 21.0.1-graal --default || true
      if ! command -v sbt &> /dev/null; then
        sdk install sbt --default || true
      fi
      set -u
    fi
  }

  run_tetris_step "Installing Java (GraalVM 21) & SDKMan" _java_step

  if [ -d "$HOME/.sdkman/candidates/java/current/bin" ]; then
    export PATH="$HOME/.sdkman/candidates/java/current/bin:$PATH"
  fi
  if [ -d "$HOME/.sdkman/candidates/sbt/current/bin" ]; then
    export PATH="$HOME/.sdkman/candidates/sbt/current/bin:$PATH"
  fi

  if command -v java &> /dev/null; then
    echo -e "  ${T_GREEN}[OK]${T_RESET} Java installed via SDKMan"
    java -version 2>&1 | head -1
  else
    echo -e "  ${T_RED}[ERROR]${T_RESET} Java installation failed"
    exit 1
  fi
}

install_polyomino_binary() {
  mkdir -p "$BIN_DIR"

  # 1. Pull latest dotfiles changes if this is a git repo
  if [ -d "$SCRIPT_DIR/.git" ]; then
    _git_pull() {
      git -C "$SCRIPT_DIR" pull --ff-only || true
    }
    run_tetris_step "Updating local dotfiles repository" _git_pull
  fi

  # 2. Compile standalone GraalVM native binary if sbt is available
  if command -v sbt &>/dev/null && [ -f "$SCRIPT_DIR/build.sbt" ]; then
    _sbt_compile() {
      (cd "$SCRIPT_DIR" && sbt nativeImage)
    }
    run_tetris_step "Compiling Polyomino native binary (sbt nativeImage)" _sbt_compile
  fi

  # 3. Copy compiled binary to $BIN_DIR/polyomino or download release fallback
  if [ -f "$SCRIPT_DIR/target/native-image/polyomino" ]; then
    cp --remove-destination "$SCRIPT_DIR/target/native-image/polyomino" "$BIN_DIR/polyomino"
    chmod +x "$BIN_DIR/polyomino"
    echo -e "  ${T_GREEN}[OK]${T_RESET} Installed native binary to $BIN_DIR/polyomino"
  elif [ ! -f "$BIN_DIR/polyomino" ]; then
    _download_bin() {
      curl -fL "https://github.com/petrolal/polyomino.dotfiles/releases/latest/download/polyomino-x86_64-linux" -o "$BIN_DIR/polyomino"
      chmod +x "$BIN_DIR/polyomino"
    }
    run_tetris_step "Fetching Polyomino native binary release" _download_bin
  fi

  # 4. Deploy dotfiles, symlinks, themes, and apply configurations to system
  if [ -x "$BIN_DIR/polyomino" ]; then
    _deploy_step() {
      "$BIN_DIR/polyomino" deploy || true
      "$BIN_DIR/polyomino" fastfetch-logo || true
    }
    run_tetris_step "Deploying dotfiles configurations and symlinks" _deploy_step
  fi
}

install_tools() {
  local pkg_mgr="$1"
  ensure_sudo

  # Ensure cargo/rust and required build dependencies are available
  if ! command -v cargo &> /dev/null; then
    _rust_step() {
      case "$pkg_mgr" in
        pacman)
          sudo pacman -S --needed --noconfirm rust cargo alsa-lib libpulse dbus openssl pkgconf fastfetch
          ;;
        apt-get)
          sudo DEBIAN_FRONTEND=noninteractive apt-get install -y cargo rustc pkg-config libasound2-dev libpulse-dev libdbus-1-dev libssl-dev fastfetch
          ;;
        dnf)
          sudo dnf install -y cargo rust alsa-lib-devel pulseaudio-libs-devel dbus-devel openssl-devel pkgconf-pkg-config fastfetch
          ;;
        *)
          if command -v curl &> /dev/null; then
            curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
            export PATH="$HOME/.cargo/bin:$PATH"
          fi
          ;;
      esac
    }
    run_tetris_step "Installing Rust & Cargo build toolchain" _rust_step
  else
    # Install build headers if cargo is already installed
    _headers_step() {
      case "$pkg_mgr" in
        pacman)
          sudo pacman -S --needed --noconfirm alsa-lib libpulse dbus openssl pkgconf || true
          ;;
        apt-get)
          sudo DEBIAN_FRONTEND=noninteractive apt-get install -y pkg-config libasound2-dev libpulse-dev libdbus-1-dev libssl-dev || true
          ;;
        dnf)
          sudo dnf install -y alsa-lib-devel pulseaudio-libs-devel dbus-devel openssl-devel pkgconf-pkg-config || true
          ;;
        *)
          ;;
      esac
    }
    run_tetris_step "Verifying C/Audio build headers" _headers_step
  fi

  if [ -d "$HOME/.cargo/bin" ]; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi

  # 1. spotify_player TUI (cargo)
  if command -v spotify_player &> /dev/null; then
    echo -e "  ${T_GREEN}[OK]${T_RESET} spotify_player already installed"
  elif command -v cargo &> /dev/null; then
    _spotify_step() {
      cargo install spotify_player --locked --features daemon,pulseaudio-backend,rodio-backend
    }
    run_tetris_step "Compiling spotify_player TUI (cargo)" _spotify_step
  fi

  # 2. bluetui Bluetooth TUI (cargo)
  if command -v bluetui &> /dev/null; then
    echo -e "  ${T_GREEN}[OK]${T_RESET} bluetui already installed"
  elif command -v cargo &> /dev/null; then
    _bluetui_step() {
      cargo install bluetui --locked
    }
    run_tetris_step "Compiling bluetui Bluetooth TUI (cargo)" _bluetui_step
  fi

  # 3. aerc Email Client TUI (package manager)
  if command -v aerc &> /dev/null; then
    echo -e "  ${T_GREEN}[OK]${T_RESET} aerc email client already installed"
  else
    _aerc_step() {
      case "$pkg_mgr" in
        pacman)
          sudo pacman -S --needed --noconfirm aerc || true
          ;;
        apt-get)
          sudo DEBIAN_FRONTEND=noninteractive apt-get install -y aerc || true
          ;;
        dnf)
          sudo dnf install -y aerc || true
          ;;
        *)
          ;;
      esac
    }
    run_tetris_step "Installing aerc email client" _aerc_step
  fi

  # 4. zoxide directory jumper (system / cargo / standalone fallback)
  if command -v zoxide &> /dev/null; then
    echo -e "  ${T_GREEN}[OK]${T_RESET} zoxide already installed"
  else
    _zoxide_step() {
      if command -v cargo &> /dev/null; then
        cargo install zoxide --locked || true
      elif command -v curl &> /dev/null; then
        curl -sSfL https://raw.githubusercontent.com/ajeetdsouza/zoxide/main/install.sh | sh || true
      fi
    }
    run_tetris_step "Installing zoxide smart directory jumper" _zoxide_step
  fi
}

setup_zsh_and_ohmyzsh() {
  _zsh_step() {
    # 1. Install or update Oh-My-Zsh
    if [ ! -d "$HOME/.oh-my-zsh" ]; then
      git clone --depth=1 https://github.com/ohmyzsh/ohmyzsh.git "$HOME/.oh-my-zsh"
    elif [ -d "$HOME/.oh-my-zsh/.git" ]; then
      git -C "$HOME/.oh-my-zsh" pull --ff-only || true
    fi

    # 2. Custom Plugins (clone or update)
    local custom_plugins="$HOME/.oh-my-zsh/custom/plugins"
    mkdir -p "$custom_plugins"
    if [ ! -d "$custom_plugins/zsh-autosuggestions" ]; then
      git clone --depth=1 https://github.com/zsh-users/zsh-autosuggestions "$custom_plugins/zsh-autosuggestions"
    elif [ -d "$custom_plugins/zsh-autosuggestions/.git" ]; then
      git -C "$custom_plugins/zsh-autosuggestions" pull --ff-only || true
    fi

    if [ ! -d "$custom_plugins/zsh-syntax-highlighting" ]; then
      git clone --depth=1 https://github.com/zsh-users/zsh-syntax-highlighting.git "$custom_plugins/zsh-syntax-highlighting"
    elif [ -d "$custom_plugins/zsh-syntax-highlighting/.git" ]; then
      git -C "$custom_plugins/zsh-syntax-highlighting" pull --ff-only || true
    fi

    # 3. Ensure ~/.bashrc seamless interactive auto-switch to Zsh
    if [ -f "$HOME/.bashrc" ] && ! grep -q "Polyomino Zsh auto-switch" "$HOME/.bashrc"; then
      cat << 'EOF' >> "$HOME/.bashrc"

# Polyomino Zsh auto-switch
if [ -t 1 ] && [ -n "$PS1" ] && [ -z "$POLYOMINO_SHELL_SWITCHED" ] && command -v zsh >/dev/null 2>&1; then
  export POLYOMINO_SHELL_SWITCHED=1
  export SHELL="$(command -v zsh)"
  exec zsh
fi
EOF
    fi
  }

  run_tetris_step "Configuring Zsh shell, Oh-My-Zsh & plugins" _zsh_step
}

setup_workspace_and_tetravim() {
  _tetravim_step() {
    # 1. Ensure ~/Projects workspace exists
    mkdir -p "$HOME/Projects"

    # 2. Clone or update Tetravim (git@github.com:petrolal/tetravim.nvim.git) with HTTPS fallback
    local tetravim_dir="$HOME/tetravim.nvim"
    local nvim_config_dir="$HOME/.config/nvim"

    if [ ! -d "$tetravim_dir/.git" ]; then
      if ! git clone git@github.com:petrolal/tetravim.nvim.git "$tetravim_dir" 2>/dev/null; then
        git clone https://github.com/petrolal/tetravim.nvim.git "$tetravim_dir"
      fi
    else
      git -C "$tetravim_dir" pull --ff-only || true
    fi

    # 3. Symlink ~/.config/nvim -> ~/tetravim.nvim
    if [ -d "$tetravim_dir" ]; then
      mkdir -p "$HOME/.config"
      if [ -e "$nvim_config_dir" ] && [ ! -L "$nvim_config_dir" ]; then
        local backup_dir="$HOME/.polyomino_backup/nvim_$(date +%s)"
        mkdir -p "$backup_dir"
        mv "$nvim_config_dir" "$backup_dir/"
      fi
      ln -sfn "$tetravim_dir" "$nvim_config_dir"
    fi
  }

  run_tetris_step "Setting up ~/Projects & Tetravim Neovim distribution" _tetravim_step
}

install_swayfx() {
  local pkg_mgr="$1"
  if command -v sway &> /dev/null && sway --version 2>&1 | grep -iq "swayfx"; then
    echo -e "  ${T_GREEN}[OK]${T_RESET} SwayFX compositor is already installed"
    return
  fi

  ensure_sudo

  _swayfx_step() {
    case "$pkg_mgr" in
      pacman)
        if command -v yay &> /dev/null; then
          if pacman -Qq sway &>/dev/null; then
            sudo pacman -Rdd --noconfirm sway || true
          fi
          yay -S --needed --noconfirm --answerclean None --answerdiff None swayfx
        elif ! command -v sway &> /dev/null; then
          sudo pacman -S --needed --noconfirm sway
        fi
        ;;
      dnf)
        sudo dnf copr enable -y swayfx/swayfx || true
        sudo dnf install -y swayfx
        ;;
      apt-get)
        sudo DEBIAN_FRONTEND=noninteractive apt-get install -y meson ninja-build libwlroots-dev wayland-protocols libwayland-dev \
          libpango1.0-dev libcairo2-dev libgdk-pixbuf-2.0-dev libjson-c-dev libpcre2-dev libevdev-dev \
          libinput-dev libxkbcommon-dev scdoc cmake git sway || true

        local build_dir="$HOME/.cache/polyomino/swayfx"
        mkdir -p "$HOME/.cache/polyomino"
        if [ ! -d "$build_dir/.git" ]; then
          rm -rf "$build_dir"
          git clone --depth 1 --branch 0.4 https://github.com/WillPower3309/swayfx.git "$build_dir"
        fi
        mkdir -p "$build_dir/subprojects"
        if [ ! -d "$build_dir/subprojects/scenefx/.git" ]; then
          rm -rf "$build_dir/subprojects/scenefx"
          git clone --depth 1 --branch 0.1 https://github.com/wlrfx/scenefx.git "$build_dir/subprojects/scenefx"
        fi
        if [ -f "$build_dir/meson.build" ]; then
          sed -i "s/subproject(\t'wlroots'/# subproject('wlroots'/g" "$build_dir/meson.build" || true
          sed -i "s/subproject(  'wlroots'/# subproject('wlroots'/g" "$build_dir/meson.build" || true
          mkdir -p "$HOME/.local/bin"
          if [ ! -f "$build_dir/build/build.ninja" ]; then
            meson setup "$build_dir/build" "$build_dir" --prefix="$HOME/.local" -Dman-pages=disabled -Dtray=disabled "-Dc_link_args=-Wl,-rpath,\$ORIGIN/../lib/x86_64-linux-gnu:\$ORIGIN/../lib"
          fi
          ninja -C "$build_dir/build"
          ninja -C "$build_dir/build" install
          if [ -f "$HOME/.local/bin/sway" ]; then
            mkdir -p "$HOME/.local/share/wayland-sessions"
            cat << EOF > "$HOME/.local/share/wayland-sessions/swayfx.desktop"
[Desktop Entry]
Name=SwayFX
Comment=An i3-compatible Wayland compositor with FX
Exec=$HOME/.local/bin/sway
Type=Application
DesktopNames=sway
EOF
            cp -f "$HOME/.local/share/wayland-sessions/swayfx.desktop" "$HOME/.local/share/wayland-sessions/sway.desktop"
            sudo cp -f "$HOME/.local/share/wayland-sessions/swayfx.desktop" /usr/share/wayland-sessions/ 2>/dev/null || true
            sudo cp -f "$HOME/.local/share/wayland-sessions/sway.desktop" /usr/share/wayland-sessions/ 2>/dev/null || true
            sudo ln -sf "$HOME/.local/bin/sway" /usr/local/bin/sway 2>/dev/null || true
            [ -f "$HOME/.local/bin/swaymsg" ] && sudo ln -sf "$HOME/.local/bin/swaymsg" /usr/local/bin/swaymsg 2>/dev/null || true
            [ -f "$HOME/.local/bin/swaybar" ] && sudo ln -sf "$HOME/.local/bin/swaybar" /usr/local/bin/swaybar 2>/dev/null || true
            [ -f "$HOME/.local/bin/swaynag" ] && sudo ln -sf "$HOME/.local/bin/swaynag" /usr/local/bin/swaynag 2>/dev/null || true
          fi
        fi
        ;;
    esac
  }

  run_tetris_step "Building & installing SwayFX compositor" _swayfx_step
}

install_gaming() {
  local pkg_mgr="$1"
  ensure_sudo

  _gaming_step() {
    case "$pkg_mgr" in
      pacman)
        sudo pacman -S --needed --noconfirm \
          gamemode gamescope mangohud vulkan-icd-loader vulkan-tools nvidia-prime
        if pacman -Si lib32-gamemode &>/dev/null; then
          sudo pacman -S --needed --noconfirm lib32-gamemode lib32-mangohud lib32-vulkan-icd-loader || true
        fi
        if command -v yay &>/dev/null; then
          yay -S --needed --noconfirm --answerclean None --answerdiff None steam || true
        fi
        ;;
      dnf)
        sudo dnf install -y gamemode gamescope mangohud vulkan-tools steam
        ;;
      apt-get)
        sudo DEBIAN_FRONTEND=noninteractive apt-get install -y gamemode gamescope mangohud vulkan-tools
        ;;
      *)
        ;;
    esac
  }

  run_tetris_step "Installing Gaming Stack & Emulators (GameMode, Gamescope, MangoHud)" _gaming_step
}

enable_path() {
  if ! echo "$PATH" | grep -q "$BIN_DIR"; then
    echo -e "  \033[36m[INFO]\033[0m Adding $BIN_DIR to PATH..."
    export PATH="$BIN_DIR:$PATH"

    # Also add SDKMan to PATH
    if [ -d "$HOME/.sdkman/candidates/java/current/bin" ]; then
      export PATH="$HOME/.sdkman/candidates/java/current/bin:$PATH"
    elif [ -d "$HOME/.sdkman/bin" ]; then
      export PATH="$HOME/.sdkman/bin:$PATH"
    fi
  fi

  # Cargo binaries in PATH
  if [ -d "$HOME/.cargo/bin" ] && ! echo "$PATH" | grep -q "$HOME/.cargo/bin"; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi
}

# Main installation flow
PKG_MGR="$(detect_pkg_mgr)"

echo -e "  \033[36m[INFO]\033[0m Package manager: $PKG_MGR"
echo ""

# Prompt or configure optional dependencies
prompt_optional_dependencies

# Install system dependencies (mandatory base + selected optional)
install_system_deps "$PKG_MGR"
echo ""

# Setup Zsh, Oh-My-Zsh and plugins
setup_zsh_and_ohmyzsh
echo ""

# Install TUI tools (spotify_player, bluetui, aerc, zoxide) if enabled
if [ "$ENABLE_TUI_TOOLS" = true ]; then
  install_tools "$PKG_MGR"
  echo ""
fi

# Setup ~/Projects and Tetravim Neovim distribution if enabled
if [ "$ENABLE_TETRAVIM" = true ]; then
  setup_workspace_and_tetravim
  echo ""
fi

# Install / Build SwayFX
install_swayfx "$PKG_MGR"
echo ""

# Gaming Stack if enabled
if [ "$ENABLE_GAMING" = true ]; then
  install_gaming "$PKG_MGR"
  echo ""
fi

# Install Java
install_java
echo ""

# Install polyomino native binary & create helper symlinks
install_polyomino_binary
echo ""

# Ensure PATH is set
enable_path
echo ""

# Installation complete
echo -e "\033[1;32m[SUCCESS]\033[0m Bootstrap complete!"
echo ""

# Only show Next Steps when bootstrap.sh is run standalone (not from install.sh)
if [ -z "${POLYOMINO_BOOTSTRAP_CALLED_FROM_INSTALLER:-}" ]; then
  echo -e "\033[1;36m[Next Steps]\033[0m"
  echo -e "  1. Run the interactive installer:"
  echo -e "     \033[33mpolyomino install\033[0m"
  echo ""
  echo -e "  2. Optional: Install or toggle gaming optimization:"
  echo -e "     \033[33mpolyomino install-gaming\033[0m    (install GameMode, Gamescope, MangoHud)"
  echo -e "     \033[33mpolyomino gamemode toggle\033[0m   (toggle Game Mode ON/OFF live)"
  echo ""
  echo -e "  3. Follow the interactive prompts to:"
  echo -e "     - Choose your preferred tools and versions"
  echo -e "     - Deploy dotfiles and symlinks"
  echo -e "     - Run system health check"
  echo ""
  echo -e "\033[1;36m[INFO]\033[0m Ensure \$HOME/.local/bin is in your PATH:"
  echo -e "  \033[33mexport PATH=\\\"$HOME/.local/bin:\$PATH\\\"\033[0m"
  echo ""
fi
