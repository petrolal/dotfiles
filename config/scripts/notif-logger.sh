#!/usr/bin/env bash
# notif-logger.sh --- Background daemon that logs D-Bus notifications for the Tofi notification center.

# Prevent multiple instances
for pid in $(pgrep -f "notif-logger.sh"); do
    if [ "$pid" != "$$" ]; then
        kill "$pid" 2>/dev/null || true
    fi
done

CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/notifications"
HISTORY_FILE="$CACHE_DIR/history.tsv"
MAX_HISTORY=100

mkdir -p "$CACHE_DIR"
touch "$HISTORY_FILE"

read_dbus_string() {
    local _prefix _content
    read -r _prefix _content
    local val="${_content#\"}"
    while [[ "$val" != *\" ]] && read -r _content; do
        val="${val} ${_content}"
    done
    val="${val%\"}"
    # Replace tabs and newlines with spaces to preserve TSV formatting
    val="${val//$'\t'/ }"
    val="${val//$'\n'/ }"
    echo "$val"
}

# Monitor freedesktop Notifications D-Bus calls
dbus-monitor "interface=org.freedesktop.Notifications,member=Notify" 2>/dev/null | while read -r line; do
    if [[ "$line" == *"member=Notify"* ]]; then
        app=$(read_dbus_string)
        read -r _ _   # uint32 replaces_id
        icon=$(read_dbus_string)
        summary=$(read_dbus_string)
        body=$(read_dbus_string)

        [ -z "$app" ] && app="Sistema"
        [ -z "$summary" ] && summary="(sem título)"

        time_str=$(date +"%H:%M")
        date_str=$(date +"%d/%m")

        # Save to history file: DATE TIME APP SUMMARY BODY
        printf "%s %s\t%s\t%s\t%s\n" "$date_str" "$time_str" "$app" "$summary" "$body" >> "$HISTORY_FILE"

        # Keep only the last MAX_HISTORY lines
        if [ $(( $(wc -l < "$HISTORY_FILE") )) -gt "$MAX_HISTORY" ]; then
            tail -n "$MAX_HISTORY" "$HISTORY_FILE" > "$HISTORY_FILE.tmp" && mv "$HISTORY_FILE.tmp" "$HISTORY_FILE"
        fi
    fi
done
