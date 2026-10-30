// Battery.tsx --- reuses the same battery_{full,mid,low}.svg icons and
// /sys/class/power_supply polling approach as
// config/quickshell/overlay/InfoOverlayComponent.qml, so the two overlays
// stay visually consistent. No AstalBattery typelib is packaged (see
// nixos/modules/hyprland.nix), hence the raw sysfs read instead of a
// gi://Astal* import.
import { Variable } from "astal"
import GLib from "gi://GLib?version=2.0"

const ASSET_DIR = `${GLib.getenv("HOME")}/dotfiles/RetroVHSSucks/assets`

function iconFor(percent: number) {
    if (percent < 40) return `${ASSET_DIR}/battery_low.svg`
    if (percent < 80) return `${ASSET_DIR}/battery_mid.svg`
    return `${ASSET_DIR}/battery_full.svg`
}

export default function Battery() {
    const percent = Variable(100).poll(
        15000,
        "sh -c 'cat /sys/class/power_supply/BAT*/capacity 2>/dev/null | head -n1'",
        (out, prev) => {
            const n = parseInt(out, 10)
            return Number.isNaN(n) ? prev : n
        },
    )

    return <box className="Battery" visible={percent().as(p => p >= 0)}>
        <icon icon={percent().as(iconFor)} />
        <label label={percent().as(p => `PWR ${p}%`)} />
    </box>
}
