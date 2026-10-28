#!/usr/bin/env bash
# notif-center.sh --- Interactive Tofi-based Notification Center for Hyprland/Astroland.

CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/notifications"
HISTORY_FILE="$CACHE_DIR/history.tsv"
DND_FILE="$CACHE_DIR/dnd"

mkdir -p "$CACHE_DIR"
touch "$HISTORY_FILE"

get_dnd_status() {
    local status
    status=$(qs ipc call notifs getDnd 2>/dev/null)
    if [ "$status" = "ATIVADO" ] || [ "$status" = "DESATIVADO" ]; then
        echo "$status"
    elif [ -f "$DND_FILE" ] && [ "$(cat "$DND_FILE" 2>/dev/null)" = "true" ]; then
        echo "ATIVADO"
    else
        echo "DESATIVADO"
    fi
}

toggle_dnd() {
    local new_status
    new_status=$(qs ipc call notifs toggleDnd 2>/dev/null)
    if [ "$new_status" = "ATIVADO" ]; then
        echo "true" > "$DND_FILE"
    elif [ "$new_status" = "DESATIVADO" ]; then
        echo "false" > "$DND_FILE"
    else
        if [ "$(get_dnd_status)" = "ATIVADO" ]; then
            echo "false" > "$DND_FILE"
        else
            echo "true" > "$DND_FILE"
        fi
    fi
}

show_menu() {
    local dnd_status
    dnd_status=$(get_dnd_status)

    local menu_items=""
    menu_items+="1. [ Limpar todas as notificações ]"$'\n'
    menu_items+="2. [ Alternar Não Perturbe (DND: $dnd_status) ]"$'\n'
    menu_items+="──────────────────────────────────────────────────"$'\n'

    local notif_count=0
    local raw_history=()

    # Read history in reverse (most recent first)
    if [ -s "$HISTORY_FILE" ]; then
        while IFS=$'\t' read -r datetime app summary body; do
            [ -z "$summary" ] && continue
            raw_history+=("$datetime"$'\t'"$app"$'\t'"$summary"$'\t'"$body")
            ((notif_count++))
        done < <(tac "$HISTORY_FILE")
    fi

    if [ "$notif_count" -eq 0 ]; then
        menu_items+="(Nenhuma notificação recente no histórico)"$'\n'
    else
        for entry in "${raw_history[@]}"; do
            IFS=$'\t' read -r dt app sum bdy <<< "$entry"
            if [ -n "$bdy" ]; then
                menu_items+="[$dt] $app: $sum — $bdy"$'\n'
            else
                menu_items+="[$dt] $app: $sum"$'\n'
            fi
        done
    fi

    # Display in Tofi (interactive selection via keyboard arrows / numbers + Enter)
    local selected
    selected=$(echo -n "$menu_items" | tofi --prompt-text="notificações [↑/↓ e Enter]: " --placeholder-text="digite 1, 2 ou busque...")

    # If user pressed Escape or closed
    [ -z "$selected" ] && exit 0

    case "$selected" in
        *[Ll]impar*|*[Cc]lear*|*\[X\]*|*\[\ X\ \]*|"1"|"1."*)
            > "$HISTORY_FILE"
            qs ipc call notifs clear 2>/dev/null || true
            sleep 0.15
            exec "$0"
            ;;
        *[Pp]erturbe*|*[Dd][Nn][Dd]*|*\[\!\]*|*\[\ !\ \]*|"2"|"2."*)
            toggle_dnd
            sleep 0.15
            exec "$0"
            ;;
        "───"*|"(Nenhuma notificação"*)
            exit 0
            ;;
        *)
            # The user selected a notification. Find it in history and show details.
            show_details "$selected"
            ;;
    esac
}

show_details() {
    local selected_label="$1"
    local found_entry=""

    # Match selected label back to history
    while IFS=$'\t' read -r datetime app summary body; do
        local label="[$datetime] $app: $summary"
        [ -n "$body" ] && label+=" — $body"
        if [ "$label" = "$selected_label" ]; then
            found_entry="$datetime"$'\t'"$app"$'\t'"$summary"$'\t'"$body"
            break
        fi
    done < "$HISTORY_FILE"

    if [ -z "$found_entry" ]; then
        sleep 0.15
        exec "$0"
    fi

    IFS=$'\t' read -r dt app sum bdy <<< "$found_entry"

    local detail_view=""
    detail_view+="1. [ < Voltar ]"$'\n'
    detail_view+="2. [ Excluir esta notificação ]"$'\n'
    detail_view+="──────────────────────────────────────────────────"$'\n'
    detail_view+="Data/Hora : $dt"$'\n'
    detail_view+="Aplicação : $app"$'\n'
    detail_view+="Título    : $sum"$'\n'
    if [ -n "$bdy" ]; then
        detail_view+="Mensagem  : $bdy"$'\n'
    fi

    local action
    action=$(echo -n "$detail_view" | tofi --prompt-text="detalhes [↑/↓ e Enter]: " --placeholder-text="digite 1 para voltar, 2 para excluir...")

    case "$action" in
        *[Ee]xcluir*|*[Dd]elete*|*2*|"2."*)
            # Remove this specific line from history
            local tmp_file
            tmp_file=$(mktemp)
            while IFS=$'\t' read -r d a s b; do
                if [ "$d" = "$dt" ] && [ "$a" = "$app" ] && [ "$s" = "$sum" ] && [ "$b" = "$bdy" ]; then
                    continue
                fi
                printf "%s\t%s\t%s\t%s\n" "$d" "$a" "$s" "$b" >> "$tmp_file"
            done < "$HISTORY_FILE"
            mv "$tmp_file" "$HISTORY_FILE"
            sleep 0.15
            exec "$0"
            ;;
        *)
            sleep 0.15
            exec "$0"
            ;;
    esac
}

show_menu
