#!/usr/bin/env bash
# toggle-vhs.sh --- Toggle VHS / CRT screen shader on and off in Hyprland

SHADER="$HOME/.config/hypr/vhs.frag"
CURRENT=$(hyprctl getoption decoration:screen_shader -j 2>/dev/null)

if echo "$CURRENT" | grep -q "vhs.frag"; then
    hyprctl eval 'hl.config({ decoration = { screen_shader = "" } })'
    notify-send "Modo VHS" "Desativado" -i video-display
else
    hyprctl eval 'hl.config({ decoration = { screen_shader = "'"$SHADER"'" } })'
    notify-send "Modo VHS" "Ativado" -i video-display
fi
