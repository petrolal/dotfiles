#!/bin/sh
# volume.sh --- Volume control with in-place updating OSD for XFCE & PipeWire
set -e

action="$1"
case "$action" in
    up)
        wpctl set-volume -l 1.0 @DEFAULT_AUDIO_SINK@ 5%+
        ;;
    down)
        wpctl set-volume -l 1.0 @DEFAULT_AUDIO_SINK@ 5%-
        ;;
    mute)
        wpctl set-mute @DEFAULT_AUDIO_SINK@ toggle
        ;;
    mic-mute)
        wpctl set-mute @DEFAULT_AUDIO_SOURCE@ toggle
        ;;
    *)
        exit 1
        ;;
esac

vol_info=$(wpctl get-volume @DEFAULT_AUDIO_SINK@ 2>/dev/null || true)
vol_raw=$(echo "$vol_info" | awk '{print $2}')
vol_pct=$(awk "BEGIN {printf \"%d\", ($vol_raw * 100) + 0.5}")
[ "$vol_pct" -gt 100 ] && vol_pct=100

if echo "$vol_info" | grep -q "MUTED"; then
    icon="audio-volume-muted"
    summary="Audio Muted"
    body="Muted"
    vol_pct=0
else
    if [ "$vol_pct" -ge 70 ]; then
        icon="audio-volume-high"
    elif [ "$vol_pct" -ge 30 ]; then
        icon="audio-volume-medium"
    elif [ "$vol_pct" -gt 0 ]; then
        icon="audio-volume-low"
    else
        icon="audio-volume-muted"
    fi
    summary="Volume"
    body="${vol_pct}%"
fi

# Track notification ID to replace in-place rather than stacking new popups
id_file="${XDG_RUNTIME_DIR:-/tmp}/volume_notification_id"
replaces_id=0
if [ -s "$id_file" ]; then
    replaces_id=$(cat "$id_file" 2>/dev/null || echo 0)
fi

res=$(gdbus call --session \
    --dest org.freedesktop.Notifications \
    --object-path /org/freedesktop/Notifications \
    --method org.freedesktop.Notifications.Notify \
    "Volume" "$replaces_id" "$icon" "$summary" "$body" '[]' \
    "{\"value\": <int32 $vol_pct>, \"synchronous\": <\"volume\">, \"x-canonical-private-synchronous\": <\"volume\">}" \
    1000 2>/dev/null || true)

new_id=$(echo "$res" | awk '{print $2}' | tr -dc '0-9')
if [ -n "$new_id" ] && [ "$new_id" -gt 0 ] 2>/dev/null; then
    echo "$new_id" > "$id_file.tmp" && mv -f "$id_file.tmp" "$id_file"
fi
